package com.deepaudit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * LLM provider configuration bound from {@code deepaudit.llm.*} in
 * application.yml. Default values target DeepSeek V3 via OpenAI-compatible API.
 */
@ConfigurationProperties(prefix = "deepaudit.llm")
public class LlmProperties {

    private String provider = "deepseek";
    private String baseUrl = "https://api.deepseek.com/v1";
    private String apiKey = "";
    private String chatModel = "deepseek-chat";
    private int timeoutSeconds = 60;
    private int maxRetries = 2;

    // Embedding sub-config: chat lives on DeepSeek (no embedding endpoint),
    // so embedding goes to a separate provider — Aliyun DashScope by default.
    private final Embedding embedding = new Embedding();

    public Duration timeout() {
        return Duration.ofSeconds(timeoutSeconds);
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public Embedding getEmbedding() { return embedding; }

    /**
     * Embedding provider config. DashScope exposes an OpenAI-compatible API
     * at {@code https://dashscope.aliyuncs.com/compatible-mode/v1}, so we
     * reuse {@code OpenAiEmbeddingModel} from langchain4j-open-ai instead of
     * pulling in a separate dashscope module. {@code text-embedding-v3} returns
     * 1024-dim vectors — must stay aligned with V10 migration column type.
     */
    public static class Embedding {
        private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        private String apiKey = "";
        private String model = "text-embedding-v3";
        private int dimensions = 1024;
        private int timeoutSeconds = 30;
        private int maxRetries = 2;

        public Duration timeout() {
            return Duration.ofSeconds(timeoutSeconds);
        }

        public boolean hasApiKey() {
            return apiKey != null && !apiKey.isBlank();
        }

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public int getDimensions() { return dimensions; }
        public void setDimensions(int dimensions) { this.dimensions = dimensions; }

        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getChatModel() { return chatModel; }
    public void setChatModel(String chatModel) { this.chatModel = chatModel; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
}
