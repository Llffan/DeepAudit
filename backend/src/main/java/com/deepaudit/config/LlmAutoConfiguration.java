package com.deepaudit.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
