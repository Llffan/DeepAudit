package com.deepaudit.service;

import com.deepaudit.api.dto.NlRuleResponse;
import com.deepaudit.api.exception.ServiceUnavailableException;
import com.deepaudit.engine.RuleDslValidator;
import com.deepaudit.engine.RuleDslValidator.ValidationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates the natural-language -> DSL pipeline (T2.5 / plan §8.3):
 *
 * <pre>
 *   user input -> RuleDslGenerator (langchain4j AiService over Gemini)
 *              -> strip accidental ```...``` fences
 *              -> Jackson parse to JsonNode
 *              -> T2.2 RuleDslValidator (mandatory R10 second-pass defense)
 *              -> NlRuleResponse
 * </pre>
 *
 * <p>Validation failures do NOT throw -- the parsed DSL plus the
 * validation error list are returned together so the UI can pre-fill
 * the editor and let the user fix the LLM's output rather than starting
 * from scratch.
 */
@Service
public class RuleGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(RuleGeneratorService.class);

    private final ObjectProvider<RuleDslGenerator> generatorProvider;
    private final RuleDslValidator dslValidator;
    private final ObjectMapper mapper;

    public RuleGeneratorService(ObjectProvider<RuleDslGenerator> generatorProvider,
                                RuleDslValidator dslValidator,
                                ObjectMapper mapper) {
        this.generatorProvider = generatorProvider;
        this.dslValidator = dslValidator;
        this.mapper = mapper;
    }

    public NlRuleResponse generate(String naturalLanguage) {
        if (naturalLanguage == null || naturalLanguage.isBlank()) {
            return new NlRuleResponse(null,
                List.of("naturalLanguage 不能为空"),
                null);
        }

        RuleDslGenerator gen = generatorProvider.getIfAvailable();
        if (gen == null) {
            throw new ServiceUnavailableException(
                "LLM 未配置：请在 .env 设置 GEMINI_API_KEY 后重启后端");
        }

        String raw;
        try {
            raw = gen.generate(naturalLanguage);
        } catch (RuntimeException e) {
            log.warn("LLM call failed for input '{}': {}",
                truncateForLog(naturalLanguage), e.getMessage());
            return new NlRuleResponse(null,
                List.of("LLM 调用失败：" + e.getMessage()),
                null);
        }

        String clean = stripCodeFences(raw);
        JsonNode dsl;
        try {
            dsl = mapper.readTree(clean);
        } catch (Exception e) {
            return new NlRuleResponse(null,
                List.of("LLM 输出不是合法 JSON：" + e.getMessage()),
                clean);
        }

        // Detect the explicit "field_not_found" sentinel that the prompt
        // instructs the model to emit when no field maps cleanly.
        if (dsl.isObject() && dsl.has("_error")
            && "field_not_found".equals(dsl.path("_error").asText())) {
            return new NlRuleResponse(null,
                List.of("AI 无法识别字段 '"
                    + dsl.path("_requested").asText("?") + "'，请改用 30 个白名单字段中的一个"),
                clean);
        }

        ValidationResult vr = dslValidator.validate(dsl);
        return new NlRuleResponse(dsl, vr.errors(), null);
    }

    /**
     * Strips ```json ... ``` and ``` ... ``` markdown fences that some
     * models still emit despite "no markdown" instructions. Idempotent
     * on already-clean output.
     */
    static String stripCodeFences(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (!s.startsWith("```")) return s;
        int firstNewline = s.indexOf('\n');
        if (firstNewline < 0) return s;        // single-line fence -> bail
        s = s.substring(firstNewline + 1).trim();
        if (s.endsWith("```")) {
            s = s.substring(0, s.length() - 3).trim();
        }
        return s;
    }

    private static String truncateForLog(String s) {
        if (s == null) return "null";
        return s.length() <= 80 ? s : s.substring(0, 77) + "...";
    }
}
