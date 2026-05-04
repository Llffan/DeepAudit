package com.deepaudit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * LLM provider configuration bound from {@code deepaudit.llm.*} in
 * application.yml. Default values target Google AI Gemini 2.5 Flash
 * + text-embedding-004 (T2.4).
 */
@ConfigurationProperties(prefix = "deepaudit.llm")
public class LlmProperties {

    private String provider = "gemini";
    private String apiKey = "";
    private String chatModel = "gemini-2.5-flash";
    private String visionModel = "gemini-2.5-flash";
    private String embeddingModel = "text-embedding-004";
    private int timeoutSeconds = 60;
    private int maxRetries = 2;

    public Duration timeout() {
        return Duration.ofSeconds(timeoutSeconds);
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getChatModel() { return chatModel; }
    public void setChatModel(String chatModel) { this.chatModel = chatModel; }

    public String getVisionModel() { return visionModel; }
    public void setVisionModel(String visionModel) { this.visionModel = visionModel; }

    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
}
