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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Orchestrates the natural-language -> rule wrapper pipeline (T2.5 /
 * plan §8.3):
 *
 * <pre>
 *   user input -> RuleDslGenerator (langchain4j AiService over Gemini)
 *              -> strip accidental ```...``` fences
 *              -> Jackson parse to JsonNode
 *              -> extract wrapper (expression + metadata) OR fall back
 *                 to legacy bare-DSL output for backward compatibility
 *              -> T2.2 RuleDslValidator on the expression
 *                 (mandatory R10 second-pass defense)
 *              -> enum-validate dimension / severity (drop if invalid)
 *              -> NlRuleResponse with whatever metadata survived
 * </pre>
 *
 * <p>Validation failures do NOT throw -- the parsed DSL plus the
 * validation error list are returned together so the UI can pre-fill
 * the editor and let the user fix the LLM's output rather than starting
 * from scratch. Metadata fields that fail enum checks are nulled out so
 * the UI doesn't try to write garbage into the form.
 */
@Service
public class RuleGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(RuleGeneratorService.class);

    private static final Set<String> ALLOWED_DIMENSIONS =
        Set.of("completeness", "logic", "standardization", "consistency");
    private static final Set<String> ALLOWED_SEVERITIES =
        Set.of("mandatory", "deduction", "hint");

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
            return error("naturalLanguage 不能为空", null);
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
            return error("LLM 调用失败：" + e.getMessage(), null);
        }

        String clean = stripCodeFences(raw);
        JsonNode root;
        try {
            root = mapper.readTree(clean);
        } catch (Exception e) {
            return error("LLM 输出不是合法 JSON：" + e.getMessage(), clean);
        }

        // Sentinel: model says it cannot map a field name to the whitelist.
        if (root.isObject() && root.has("_error")
            && "field_not_found".equals(root.path("_error").asText())) {
            return error("AI 无法识别字段 '"
                + root.path("_requested").asText("?") + "'，请改用 30 个白名单字段中的一个",
                clean);
        }

        // Wrapper format: {expression, name, description, dimension, severity, errorMessageTemplate}
        // Legacy fallback: a bare DSL object (no "expression" key) -- treat the whole node as expression.
        JsonNode expression;
        String name = null;
        String description = null;
        String dimension = null;
        String severity = null;
        String errorMessageTemplate = null;
        List<String> errors = new ArrayList<>();

        if (root.isObject() && root.has("expression")) {
            expression = root.get("expression");
            name = textOrNull(root, "name");
            description = textOrNull(root, "description");
            errorMessageTemplate = textOrNull(root, "errorMessageTemplate");

            String rawDimension = textOrNull(root, "dimension");
            if (rawDimension != null) {
                if (ALLOWED_DIMENSIONS.contains(rawDimension)) {
                    dimension = rawDimension;
                } else {
                    errors.add("AI 给出的维度 '" + rawDimension
                        + "' 不在白名单内，已忽略，请人工选择");
                }
            }

            String rawSeverity = textOrNull(root, "severity");
            if (rawSeverity != null) {
                if (ALLOWED_SEVERITIES.contains(rawSeverity)) {
                    severity = rawSeverity;
                } else {
                    errors.add("AI 给出的严重度 '" + rawSeverity
                        + "' 不在白名单内，已忽略，请人工选择");
                }
            }
        } else {
            // Legacy bare-DSL output -- still callable, just no metadata to fill.
            expression = root;
        }

        ValidationResult vr = dslValidator.validate(expression);
        errors.addAll(vr.errors());

        return new NlRuleResponse(
            expression,
            name,
            description,
            dimension,
            severity,
            errorMessageTemplate,
            errors,
            null
        );
    }

    private static NlRuleResponse error(String message, String rawOutput) {
        return new NlRuleResponse(
            null, null, null, null, null, null,
            List.of(message),
            rawOutput
        );
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        if (!v.isTextual()) return null;
        String s = v.asText().trim();
        return s.isEmpty() ? null : s;
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
