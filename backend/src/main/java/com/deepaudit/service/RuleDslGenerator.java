package com.deepaudit.service;

/**
 * Translates a natural-language rule description into a JSON DSL wrapper
 * (T2.5 / plan §8.3).
 *
 * <p>Implementation lives in {@link com.deepaudit.config.LlmAutoConfiguration}.
 * Note: this is intentionally <b>not</b> a langchain4j {@code @AiService}
 * with {@code @SystemMessage(fromResource=...)} -- that path runs the
 * prompt through {@code PromptTemplate}, which interprets the
 * {@code {{fieldName}}} mustache placeholders inside our example
 * {@code errorMessageTemplate} strings as required variables and throws
 * "Value for the variable 'X' is missing". We instead build a plain
 * {@code SystemMessage} from the raw resource text so the template
 * engine never sees it.
 *
 * <p>{@link RuleGeneratorService} is responsible for stripping accidental
 * markdown code fences, parsing JSON, and re-validating with T2.2
 * RuleDslValidator (R10 defense per plan §9).
 */
public interface RuleDslGenerator {

    String generate(String naturalLanguage);
}
