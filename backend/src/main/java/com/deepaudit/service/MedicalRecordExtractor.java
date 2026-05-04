package com.deepaudit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;

/**
 * PDF -> structured fields multimodal extraction (T3.2 / plan §8.2).
 *
 * <p>Bypasses {@code AiServices} for the same reason as {@link RuleDslGenerator}:
 * the system prompt contains JSON examples with literal braces; running them
 * through langchain4j's {@code PromptTemplate} would treat every {@code
 * {{name}}} as a required variable and crash. We hand-build the message
 * list ({@link SystemMessage} + multipart {@link UserMessage}) and call
 * {@link ChatLanguageModel#generate(List)} directly, which leaves the prompt
 * untouched.
 *
 * <p>Failure semantics: any LLM error, malformed JSON, or schema violation
 * yields a degraded {@link ExtractionResult} (empty main, empty extra,
 * confidence 0.0, {@code degraded=true}). Callers do NOT throw — the import
 * pipeline still persists the record so the operator can hand-fill it
 * (plan §8.7 降级原则).
 *
 * <p>The bean is conditional on a {@link ChatLanguageModel} being present.
 * No API key -> the extractor bean is absent and the controller surfaces
 * a "未配置 LLM" path that returns 503.
 */
@Service
public class MedicalRecordExtractor {

    private static final Logger log = LoggerFactory.getLogger(MedicalRecordExtractor.class);

    private static final String SYSTEM_PROMPT_RESOURCE = "prompts/extractor.system.txt";
    private static final String USER_INSTRUCTION = "请抽取以下病案首页图片中的字段，按系统消息约定的 JSON 结构输出。";

    /** White-listed main keys; anything else returned by the model is moved to extra. */
    private static final Set<String> MAIN_KEYS = Set.of(
        "recordNo", "name", "gender", "birthDate", "age", "idCardMasked",
        "admissionDate", "dischargeDate", "lengthOfStay",
        "admissionDept", "dischargeDept", "admissionRoute", "dischargeStatus",
        "mainDiagnosisCode", "mainDiagnosisName", "mainDiagnosisIcdVer",
        "otherDiagnosisCount", "pathologicalDiagnosis",
        "mainOperationCode", "mainOperationName", "operationDate", "operator",
        "anesthesiaMethod",
        "totalCost", "drugCost", "operationCost", "medicalServiceCost"
    );

    private final ObjectProvider<ChatLanguageModel> chatModelProvider;
    private final ObjectMapper mapper;
    private final String systemPrompt;

    public MedicalRecordExtractor(ObjectProvider<ChatLanguageModel> chatModelProvider,
                                  ObjectMapper mapper) throws IOException {
        this.chatModelProvider = chatModelProvider;
        this.mapper = mapper;
        this.systemPrompt = loadPrompt(SYSTEM_PROMPT_RESOURCE);
    }

    public boolean isAvailable() {
        return chatModelProvider.getIfAvailable() != null;
    }

    public ExtractionResult extract(List<byte[]> pagePngs) {
        ChatLanguageModel chat = chatModelProvider.getIfAvailable();
        if (chat == null) {
            return degraded("LLM 未配置 (GEMINI_API_KEY 缺失)");
        }
        if (pagePngs == null || pagePngs.isEmpty()) {
            return degraded("PDF 未渲染出任何页面");
        }

        List<dev.langchain4j.data.message.Content> userParts = new ArrayList<>(pagePngs.size() + 1);
        userParts.add(TextContent.from(USER_INSTRUCTION));
        for (byte[] png : pagePngs) {
            String base64 = Base64.getEncoder().encodeToString(png);
            Image img = Image.builder()
                .base64Data(base64)
                .mimeType("image/png")
                .build();
            userParts.add(ImageContent.from(img));
        }

        String raw;
        try {
            raw = chat.generate(List.of(
                SystemMessage.from(systemPrompt),
                UserMessage.from(userParts)
            )).content().text();
        } catch (RuntimeException e) {
            log.warn("Gemini multimodal extract call failed: {}", e.toString());
            return degraded("LLM 调用失败：" + e.getMessage());
        }

        String clean = stripCodeFences(raw);
        JsonNode root;
        try {
            root = mapper.readTree(clean);
        } catch (Exception e) {
            log.warn("Extractor output not valid JSON. raw[0..200]={}",
                clean == null ? "null" : clean.substring(0, Math.min(clean.length(), 200)));
            return degraded("LLM 输出不是合法 JSON：" + e.getMessage());
        }

        // Tolerate two layouts: {main, extra, confidence} (preferred) OR a flat
        // object that's just the main fields. The flat fallback is a small
        // robustness gain — Gemini occasionally drops the wrapper despite the
        // schema in the system prompt.
        JsonNode main;
        JsonNode extra;
        double confidence;

        if (root.isObject() && root.has("main")) {
            main = root.get("main");
            extra = root.has("extra") && root.get("extra").isObject()
                ? root.get("extra")
                : JsonNodeFactory.instance.objectNode();
            confidence = root.path("confidence").isNumber()
                ? root.get("confidence").asDouble()
                : 0.0;
        } else if (root.isObject()) {
            // Flat fallback. Split keys into main vs extra by whitelist.
            var mainObj = JsonNodeFactory.instance.objectNode();
            var extraObj = JsonNodeFactory.instance.objectNode();
            root.fieldNames().forEachRemaining(k -> {
                if (MAIN_KEYS.contains(k)) {
                    mainObj.set(k, root.get(k));
                } else if (!"confidence".equals(k)) {
                    extraObj.set(k, root.get(k));
                }
            });
            main = mainObj;
            extra = extraObj;
            confidence = root.path("confidence").isNumber()
                ? root.get("confidence").asDouble()
                : 0.0;
        } else {
            return degraded("LLM 输出根节点不是 JSON 对象");
        }

        if (!main.isObject()) {
            return degraded("LLM 输出 main 不是对象");
        }

        // Clamp confidence to [0,1]; Gemini sometimes returns 1.0+ or stray strings.
        if (confidence < 0.0) confidence = 0.0;
        if (confidence > 1.0) confidence = 1.0;

        return new ExtractionResult(main, extra, confidence, false, null);
    }

    private static ExtractionResult degraded(String reason) {
        return new ExtractionResult(
            JsonNodeFactory.instance.objectNode(),
            JsonNodeFactory.instance.objectNode(),
            0.0,
            true,
            reason
        );
    }

    /** Same fence-stripping logic used by the NL->DSL pipeline. */
    static String stripCodeFences(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (!s.startsWith("```")) return s;
        int firstNewline = s.indexOf('\n');
        if (firstNewline < 0) return s;
        s = s.substring(firstNewline + 1).trim();
        if (s.endsWith("```")) {
            s = s.substring(0, s.length() - 3).trim();
        }
        return s;
    }

    private static String loadPrompt(String path) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        }
    }
}
