package com.deepaudit.service;

import java.util.List;

/**
 * Translates a custom-operator parameter list + Chinese description into a
 * bodyDsl JSON tree, output by the LLM. Parallel to {@link RuleDslGenerator}
 * but for operator templates rather than rules.
 *
 * <p>Implementation lives in {@link com.deepaudit.config.LlmAutoConfiguration}.
 * Same as RuleDslGenerator, this is a plain SystemMessage-based call (not
 * a langchain4j {@code @AiService}) so the prompt's literal {@code {{...}}}
 * substrings are not mistaken for template placeholders.
 *
 * <p>{@link OperatorGeneratorService} is responsible for stripping accidental
 * markdown fences and parsing the JSON.
 */
public interface OperatorBodyDslGenerator {

    /**
     * @param parameterNames operator's parameter names (typically real medical-record
     *                       camelCase field names since the UI is a multi-select dropdown)
     * @param description    Chinese natural-language description of what the
     *                       operator should check
     * @return raw LLM output (JSON or sentinel-error object), possibly with
     *         markdown fences that the caller needs to strip
     */
    String generate(List<String> parameterNames, String description);
}
