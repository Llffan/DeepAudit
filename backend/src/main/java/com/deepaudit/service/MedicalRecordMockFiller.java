package com.deepaudit.service;

import com.deepaudit.api.exception.ServiceUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.langchain4j.data.message.SystemMessage;
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
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates a plausible-but-fictional 病案首页 record via Gemini for the
 * frontend's "🎲 LLM 生成测试数据" dev-helper button (T3.x sidequest).
 *
 * <p>Implementation mirrors {@link MedicalRecordExtractor}: bypasses
 * {@code AiServices} so the JSON example block in the system prompt isn't
 * mistaken for prompt-template variables. The user message rotates a
 * scenario theme + a short nonce so consecutive clicks don't collide on
 * Gemini's response cache.
 */
@Service
public class MedicalRecordMockFiller {

    private static final Logger log = LoggerFactory.getLogger(MedicalRecordMockFiller.class);

    private static final String SYSTEM_PROMPT_RESOURCE = "prompts/mock_filler.system.txt";

    /** Rotated through to nudge Gemini toward different specialties / patient profiles. */
    private static final String[] SCENARIOS = {
        "中年男性内科入院（如冠心病、肺炎、急性胰腺炎、消化道出血）",
        "中年女性外科手术（如胆囊切除、阑尾炎、乳腺良性肿瘤）",
        "老年慢病（如糖尿病、慢阻肺、脑梗死、骨质疏松性骨折）",
        "妇产科（如剖宫产、子宫肌瘤、卵巢囊肿）",
        "儿科常见病（如急性支气管炎、肠炎、热性惊厥），≤10 岁",
        "骨科创伤（如踝关节骨折、腰椎间盘突出）",
        "肿瘤科（如肺癌、乳腺癌、结直肠癌；视情况包含病理）",
        "急诊抢救后转入（脑外伤、心肌梗死溶栓后）"
    };

    private final ObjectProvider<ChatLanguageModel> chatModelProvider;
    private final ObjectMapper mapper;
    private final String systemPrompt;

    public MedicalRecordMockFiller(ObjectProvider<ChatLanguageModel> chatModelProvider,
                                   ObjectMapper mapper) throws IOException {
        this.chatModelProvider = chatModelProvider;
        this.mapper = mapper;
        this.systemPrompt = loadPrompt(SYSTEM_PROMPT_RESOURCE);
    }

    public ExtractionResult generate() {
        ChatLanguageModel chat = chatModelProvider.getIfAvailable();
        if (chat == null) {
            throw new ServiceUnavailableException(
                "LLM 未配置：请在 .env 设置 GEMINI_API_KEY 后重启后端");
        }

        String scenario = SCENARIOS[ThreadLocalRandom.current().nextInt(SCENARIOS.length)];
        String nonce = Long.toHexString(ThreadLocalRandom.current().nextLong());
        String userMsg = "请按系统消息约定的 JSON 结构生成一份病案首页样例。"
            + "\n场景主题：" + scenario
            + "\n本次随机种子：" + nonce
            + "\n请确保各字段相互一致，且与上次生成结果不同。";

        String raw;
        try {
            raw = chat.generate(List.of(
                SystemMessage.from(systemPrompt),
                UserMessage.from(userMsg)
            )).content().text();
        } catch (RuntimeException e) {
            log.warn("Gemini mock-fill call failed: {}", e.toString());
            throw new ServiceUnavailableException("LLM 调用失败：" + e.getMessage());
        }

        String clean = MedicalRecordExtractor.stripCodeFences(raw);
        JsonNode root;
        try {
            root = mapper.readTree(clean);
        } catch (Exception e) {
            log.warn("Mock filler output not valid JSON. raw[0..200]={}",
                clean == null ? "null" : clean.substring(0, Math.min(clean.length(), 200)));
            throw new ServiceUnavailableException("LLM 输出不是合法 JSON：" + e.getMessage());
        }

        JsonNode main = root.has("main") && root.get("main").isObject()
            ? root.get("main")
            : root.isObject() ? root : null;
        if (main == null || !main.isObject()) {
            throw new ServiceUnavailableException("LLM 输出 main 不是对象");
        }
        JsonNode extra = root.has("extra") && root.get("extra").isObject()
            ? root.get("extra")
            : JsonNodeFactory.instance.objectNode();

        // Mock data is "fully generated"; tag confidence as 1.0 so the
        // frontend traffic-light bar shows green and signals "this is a
        // synthetic record, not an extraction".
        return new ExtractionResult(main, extra, 1.0, false, null);
    }

    private static String loadPrompt(String path) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        }
    }
}
