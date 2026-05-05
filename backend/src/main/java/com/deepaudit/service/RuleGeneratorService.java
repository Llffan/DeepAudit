package com.deepaudit.service;

import com.deepaudit.api.dto.NlRuleResponse;
import com.deepaudit.api.exception.ServiceUnavailableException;
import com.deepaudit.engine.CustomOperatorRegistry;
import com.deepaudit.engine.RuleDslValidator;
import com.deepaudit.engine.RuleDslValidator.ValidationResult;
import com.deepaudit.persistence.entity.QcOperatorTemplate;
import com.deepaudit.persistence.repository.QcOperatorTemplateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
 *   user input -> RuleDslGenerator (langchain4j AiService over DeepSeek)
 *              -> strip accidental ```...``` fences
 *              -> Jackson parse to JsonNode
 *              -> optionally persist any new operator templates from "operators[]"
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
    private final CustomOperatorRegistry registry;
    private final QcOperatorTemplateRepository operatorRepo;
    private final ObjectMapper mapper;

    public RuleGeneratorService(ObjectProvider<RuleDslGenerator> generatorProvider,
                                RuleDslValidator dslValidator,
                                CustomOperatorRegistry registry,
                                QcOperatorTemplateRepository operatorRepo,
                                ObjectMapper mapper) {
        this.generatorProvider = generatorProvider;
        this.dslValidator = dslValidator;
        this.registry = registry;
        this.operatorRepo = operatorRepo;
        this.mapper = mapper;
    }

    public NlRuleResponse generate(String naturalLanguage) {
        if (naturalLanguage == null || naturalLanguage.isBlank()) {
            return error("naturalLanguage 不能为空", null);
        }

        RuleDslGenerator gen = generatorProvider.getIfAvailable();
        if (gen == null) {
            throw new ServiceUnavailableException(
                "LLM 未配置：请在 .env 设置 DEEPSEEK_API_KEY 后重启后端");
        }

        String raw;
        try {
            raw = gen.generate(naturalLanguage, buildOperatorsContext());
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

        // Persist any new operator templates the model generated.
        List<String> createdOperatorCodes = persistOperators(root);

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
            null,
            createdOperatorCodes
        );
    }

    /**
     * Builds the string that replaces {@code {{CUSTOM_OPERATORS_PLACEHOLDER}}} in the
     * system prompt. Returns a compact JSON array of operator summaries, or a
     * human-readable note when the library is empty.
     */
    private String buildOperatorsContext() {
        var ops = registry.all();
        if (ops.isEmpty()) {
            return "(none yet)";
        }
        try {
            ArrayNode arr = mapper.createArrayNode();
            for (QcOperatorTemplate t : ops) {
                var obj = mapper.createObjectNode();
                obj.put("code", t.getCode());
                obj.put("name", t.getName());
                if (t.getDescription() != null) obj.put("description", t.getDescription());
                var params = mapper.createArrayNode();
                t.getParameterNames().forEach(params::add);
                obj.set("parameterNames", params);
                obj.set("bodyDsl", t.getBodyDsl());
                arr.add(obj);
            }
            return mapper.writeValueAsString(arr);
        } catch (Exception e) {
            log.warn("Failed to serialize operators context: {}", e.getMessage());
            return "(serialization error)";
        }
    }

    /**
     * Reads the optional top-level {@code "operators"} array from the LLM response
     * and persists any entries whose {@code code} is not already in the DB.
     * Calls {@link CustomOperatorRegistry#reload()} if anything was actually created.
     *
     * @return codes of newly created operators (empty if none)
     */
    private List<String> persistOperators(JsonNode root) {
        if (!root.isObject() || !root.has("operators")) return List.of();
        JsonNode ops = root.get("operators");
        if (!ops.isArray() || ops.isEmpty()) return List.of();

        List<String> created = new ArrayList<>();
        for (JsonNode op : ops) {
            if (!op.isObject()) continue;
            String code = textOrNull(op, "code");
            if (code == null || code.isBlank()) {
                log.warn("LLM returned operator with missing code, skipping");
                continue;
            }
            if (operatorRepo.existsByCode(code)) {
                log.info("Custom operator '{}' already exists, skipping auto-create", code);
                continue;
            }

            JsonNode bodyDsl = op.get("bodyDsl");
            if (bodyDsl == null || bodyDsl.isNull()) {
                log.warn("LLM operator '{}' has no bodyDsl, skipping", code);
                continue;
            }

            QcOperatorTemplate t = new QcOperatorTemplate();
            t.setCode(code);
            t.setName(textOrNullFallback(op, "name", code));
            t.setDescription(textOrNull(op, "description"));
            t.setBodyDsl(bodyDsl);

            List<String> paramNames = new ArrayList<>();
            JsonNode params = op.get("parameterNames");
            if (params != null && params.isArray()) {
                for (JsonNode p : params) {
                    if (p.isTextual()) paramNames.add(p.asText());
                }
            }
            t.setParameterNames(paramNames);

            operatorRepo.save(t);
            created.add(code);
            log.info("Auto-created custom operator '{}' from NL rule generation", code);
        }

        if (!created.isEmpty()) {
            registry.reload();
        }
        return created;
    }

    private static NlRuleResponse error(String message, String rawOutput) {
        return new NlRuleResponse(
            null, null, null, null, null, null,
            List.of(message),
            rawOutput,
            List.of()
        );
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        if (!v.isTextual()) return null;
        String s = v.asText().trim();
        return s.isEmpty() ? null : s;
    }

    private static String textOrNullFallback(JsonNode node, String field, String fallback) {
        String v = textOrNull(node, field);
        return v != null ? v : fallback;
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
