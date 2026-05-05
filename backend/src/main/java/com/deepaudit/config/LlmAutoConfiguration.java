package com.deepaudit.config;

import com.deepaudit.service.RuleDslGenerator;
import com.deepaudit.service.SamplePdfTool;
import com.deepaudit.service.TestingAssistantService;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
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
 * Wires langchain4j model beans against DeepSeek V3 via OpenAI-compatible API.
 *
 * <ul>
 *   <li>{@link ChatLanguageModel}          -- NL->DSL (T2.5), violation explanation (T4.3)
 *   <li>{@link StreamingChatLanguageModel} -- SSE token stream (T4.3)
 * </ul>
 *
 * <p>Beans are conditional on {@code deepaudit.llm.api-key} being non-empty.
 * When absent the rule engine still runs; only NL rule generation, error
 * explanation, and testing assistant gracefully degrade.
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
        return OpenAiChatModel.builder()
            .baseUrl(props.getBaseUrl())
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
        return OpenAiStreamingChatModel.builder()
            .baseUrl(props.getBaseUrl())
            .apiKey(props.getApiKey())
            .modelName(props.getChatModel())
            .temperature(0.0)
            .timeout(props.timeout())
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

        return (naturalLanguage, customOperatorsCtx) -> {
            String prompt = systemPrompt.replace(
                "{{CUSTOM_OPERATORS_PLACEHOLDER}}", customOperatorsCtx);
            return chatModel.generate(List.of(
                SystemMessage.from(prompt),
                UserMessage.from(naturalLanguage)
            )).content().text();
        };
    }

    private static String loadResourceText(String path) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        }
    }

    private static final String TESTING_ASSISTANT_PROMPT_RESOURCE =
        "prompts/testing_assistant.system.txt";

    /**
     * 测试助手 AiService（T2.5 衍生）：把 SamplePdfTool 的三个 @Tool 方法
     * 注入 Gemini，让 LLM 按需调用 Python 生成器生成测试 PDF。
     *
     * <p>系统提示从 {@code prompts/testing_assistant.system.txt} 加载；
     * 通过 {@code systemMessageProvider} 注入，绕过 PromptTemplate 引擎，
     * 避免 prompt 里的双大括号被误判为变量（同 ruleDslGenerator 方案）。
     */
    @Bean
    @ConditionalOnBean(ChatLanguageModel.class)
    public TestingAssistantService testingAssistantService(
        ChatLanguageModel chatModel,
        SamplePdfTool samplePdfTool
    ) throws IOException {
        String systemPrompt = loadResourceText(TESTING_ASSISTANT_PROMPT_RESOURCE);
        log.info("Loaded testing-assistant system prompt ({} chars)", systemPrompt.length());
        return AiServices.builder(TestingAssistantService.class)
            .chatLanguageModel(chatModel)
            .tools(samplePdfTool)
            .systemMessageProvider(ignored -> systemPrompt)
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
                log.info("LLM provider={} baseUrl={} chatModel={} timeout={}s",
                    props.getProvider(), props.getBaseUrl(),
                    props.getChatModel(), props.getTimeoutSeconds());
            } else {
                log.warn("LLM api-key not configured -- chat beans will be absent. " +
                         "Set DEEPSEEK_API_KEY env var to enable NL->DSL and " +
                         "violation explanation. The rule engine itself does NOT depend on this.");
            }
        };
    }
}
