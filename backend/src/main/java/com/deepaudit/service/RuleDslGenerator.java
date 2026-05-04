package com.deepaudit.service;

import dev.langchain4j.service.SystemMessage;

/**
 * langchain4j {@code AiService} interface for translating a natural-
 * language rule description into a DSL JSON tree (T2.5 / plan §8.3).
 *
 * <p>The system prompt lives in {@code resources/prompts/...} so prompt
 * iteration is a text edit + restart, not a Java recompile. The
 * implementation is generated at runtime by {@code AiServices.builder}
 * and wired in {@link com.deepaudit.config.LlmAutoConfiguration}.
 *
 * <p>Returns a String (raw model output) -- {@link RuleGeneratorService}
 * is responsible for stripping accidental markdown code fences,
 * parsing JSON, and re-validating with T2.2 RuleDslValidator (R10
 * defense per plan §9).
 */
public interface RuleDslGenerator {

    @SystemMessage(fromResource = "/prompts/rule_dsl_generator.system.txt")
    String generate(String naturalLanguage);
}
