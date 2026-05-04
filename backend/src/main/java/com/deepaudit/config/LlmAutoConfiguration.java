package com.deepaudit.config;

import com.deepaudit.service.RuleDslGenerator;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Wires the three langchain4j model beans against Google AI Gemini
 * (T2.4 / plan §3.6):
 *
 * <ul>
 *   <li>{@link ChatLanguageModel}          -- T2.5 NL->DSL, T4.3 violation explanation
 *   <li>{@link StreamingChatLanguageModel} -- T4.3 SSE token stream
 *   <li>{@link EmbeddingModel}             -- T2.4 rule + ICD dictionary embeddings
 * </ul>
 *
 * <p>Beans are conditional on {@code deepaudit.llm.api-key} being non-empty.
 * When the env-var-backed key is absent (typical in unit tests / CI without
 * secrets), the beans are simply not registered and consumers receive an
 * empty {@code ObjectProvider} -- the rule engine continues to function;
 * only the LLM-dependent paths (embedding fill, NL rule generation, error
 * explanation) gracefully degrade.
 */
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(LlmAutoConfiguration.class);

    private static final String API_KEY_PRESENT =
        "'${deepaudit.llm.api-key:}' != ''";

    private static final String RULE_DSL_PROMPT_RESOURCE =
        "prompts/rule_dsl_generator.system.txt";

    @Bean
    @ConditionalOnExpression(API_KEY_PRESENT)
    public ChatLanguageModel chatLanguageModel(LlmProperties props) {
        return GoogleAiGeminiChatModel.builder()
            .apiKey(props.getApiKey())
            .modelName(props.getChatModel())
            .temperature(0.0)
            .timeout(props.timeout())
            .maxRetries(props.getMaxRetries())
            .build();
    }

    @Bean
    @ConditionalOnExpression(API_KEY_PRESENT)
    public StreamingChatLanguageModel streamingChatLanguageModel(LlmProperties props) {
        return GoogleAiGeminiStreamingChatModel.builder()
            .apiKey(props.getApiKey())
            .modelName(props.getChatModel())
            .temperature(0.0)
            .timeout(props.timeout())
            .build();
    }

    @Bean
    @ConditionalOnExpression(API_KEY_PRESENT)
    public EmbeddingModel embeddingModel(LlmProperties props) {
        return GoogleAiEmbeddingModel.builder()
            .apiKey(props.getApiKey())
            .modelName(props.getEmbeddingModel())
            .build();
    }

    /**
     * Plain-text NL->DSL generator (T2.5).
     *
     * <p>We deliberately bypass {@code AiServices} + {@code @SystemMessage}
     * here. That path renders the prompt through langchain4j's
     * {@code PromptTemplate}, which treats every {@code {{name}}} in the
     * prompt body as a required variable -- our example
     * {@code errorMessageTemplate} strings legitimately contain
     * {@code {{age}}}, {@code {{operationDate}}} etc. (matching the
     * production rule-message format used by R001/R002 in V1__init_schema.sql),
     * and that triggered "Value for the variable 'X' is missing" at runtime.
     *
     * <p>Reading the system prompt as a flat string and constructing
     * {@link SystemMessage} directly skips the template engine entirely,
     * so the placeholder syntax in the prompt is preserved verbatim and
     * the model can use it in its own example output.
     */
    @Bean
    @ConditionalOnBean(ChatLanguageModel.class)
    public RuleDslGenerator ruleDslGenerator(ChatLanguageModel chatModel) throws IOException {
        String systemPrompt = loadResourceText(RULE_DSL_PROMPT_RESOURCE);
        log.info("Loaded NL->DSL system prompt ({} chars) from {}",
            systemPrompt.length(), RULE_DSL_PROMPT_RESOURCE);

        return naturalLanguage -> chatModel.generate(List.of(
            SystemMessage.from(systemPrompt),
            UserMessage.from(naturalLanguage)
        )).content().text();
    }

    private static String loadResourceText(String path) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        }
    }

    /**
     * Startup confirmation log. Always runs; the message tells the operator
     * whether LLM-backed features will be active or in degraded mode this boot.
     */
    @Bean
    public ApplicationRunner llmStartupReport(LlmProperties props) {
        return args -> {
            if (props.hasApiKey()) {
                log.info("LLM provider={} chatModel={} embeddingModel={} timeout={}s",
                    props.getProvider(), props.getChatModel(),
                    props.getEmbeddingModel(), props.getTimeoutSeconds());
            } else {
                log.warn("LLM api-key not configured -- chat / embedding beans will be absent. " +
                         "Set GEMINI_API_KEY env var to enable NL->DSL, embeddings, and " +
                         "violation explanation. The rule engine itself does NOT depend on this.");
            }
        };
    }
}
