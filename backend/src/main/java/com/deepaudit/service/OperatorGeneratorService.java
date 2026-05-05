package com.deepaudit.service;

import com.deepaudit.api.exception.ServiceUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the parameterNames + description -> bodyDsl pipeline for
 * custom operators. Parallel to {@link RuleGeneratorService} but simpler
 * because the output has no metadata wrapper — just a bodyDsl JSON tree.
 *
 * <p>No structural validation here on purpose: the bodyDsl uses
 * {@code {"$ref": "<paramName>"}} placeholders which are not real field
 * names, so {@link com.deepaudit.engine.RuleDslValidator} would
 * (correctly) reject them. Validation happens at the rule-evaluation site
 * after refs are substituted with actual field names.
 */
@Service
public class OperatorGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(OperatorGeneratorService.class);

    private final ObjectProvider<OperatorBodyDslGenerator> generatorProvider;
    private final ObjectMapper mapper;

    public OperatorGeneratorService(ObjectProvider<OperatorBodyDslGenerator> generatorProvider,
                                    ObjectMapper mapper) {
        this.generatorProvider = generatorProvider;
        this.mapper = mapper;
    }

    public Response generate(List<String> parameterNames, String description) {
        if (parameterNames == null) parameterNames = List.of();
        if (parameterNames.isEmpty()) {
            return error("参数列表为空，请先在抽屉里选好字段再让 AI 推荐", null);
        }
        if (description == null || description.isBlank()) {
            return error("说明为空，AI 无法判断算子要校验什么；请补一句中文说明", null);
        }

        OperatorBodyDslGenerator gen = generatorProvider.getIfAvailable();
        if (gen == null) {
            throw new ServiceUnavailableException(
                "LLM 未配置：请在 .env 设置 DEEPSEEK_API_KEY 后重启后端");
        }

        String raw;
        try {
            raw = gen.generate(parameterNames, description);
        } catch (RuntimeException e) {
            log.warn("Operator bodyDsl LLM call failed: {}", e.getMessage());
            return error("LLM 调用失败：" + e.getMessage(), null);
        }

        // 复用 RuleGeneratorService 同款的去围栏逻辑
        String clean = RuleGeneratorService.stripCodeFences(raw);
        JsonNode root;
        try {
            root = mapper.readTree(clean);
        } catch (Exception e) {
            return error("LLM 输出不是合法 JSON：" + e.getMessage(), clean);
        }

        // Sentinel: model says it cannot process the input
        if (root.isObject() && root.has("_error")) {
            String code = root.path("_error").asText();
            String hint = root.path("_hint").asText("");
            String msg = switch (code) {
                case "description_unclear" ->
                    "AI 觉得说明不够清晰：" + (hint.isBlank() ? "请补充更多细节" : hint);
                case "empty_parameters" -> "AI 拒绝生成：参数列表为空";
                default -> "AI 返回错误：" + code + (hint.isBlank() ? "" : "（" + hint + "）");
            };
            return error(msg, clean);
        }

        // 校验 $ref 用的参数名是否都在传入列表里 —— 防止 LLM 引入"幽灵"参数
        List<String> errors = new ArrayList<>();
        validateRefsAgainstParams(root, parameterNames, errors);

        return new Response(root, errors, raw, true);
    }

    /** 递归走 DSL 树，凡是 {"$ref": "X"} 就检查 X 是否在 paramNames 里 */
    private static void validateRefsAgainstParams(JsonNode node, List<String> paramNames, List<String> errors) {
        if (node == null || node.isNull()) return;
        if (node.isObject()) {
            if (node.size() == 1 && node.has("$ref")) {
                String ref = node.get("$ref").asText();
                if (!paramNames.contains(ref)) {
                    errors.add("AI 引用了不在参数列表里的参数 '" + ref + "'，请人工修正");
                }
                return;
            }
            node.fieldNames().forEachRemaining(k -> validateRefsAgainstParams(node.get(k), paramNames, errors));
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> validateRefsAgainstParams(child, paramNames, errors));
        }
    }

    private static Response error(String message, String rawOutput) {
        return new Response(null, List.of(message), rawOutput, false);
    }

    /**
     * Service-layer response. {@code bodyDsl} 在错误情况下为 null。
     * {@code errors} 即使在 ok=true 时也可能有内容（比如非致命的 $ref 警告），
     * 前端可以选择是否显示提示再让用户落库。
     */
    public record Response(
        JsonNode bodyDsl,
        List<String> errors,
        String rawOutput,
        boolean ok
    ) {}
}
