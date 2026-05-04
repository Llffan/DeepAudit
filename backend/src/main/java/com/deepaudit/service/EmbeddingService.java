package com.deepaudit.service;

import dev.langchain4j.model.embedding.EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Embedding façade for the rule pipeline (T2.4 / plan §3.6 / §5.5).
 * Uses {@link ObjectProvider} so the bean stays optional -- when
 * {@code GEMINI_API_KEY} is absent the underlying {@link EmbeddingModel}
 * bean is not registered and {@link #embed(String)} returns {@code null}
 * silently. Callers (RuleService) must treat null as "no embedding"
 * and proceed; missing embeddings only disable similarity-based dedup.
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final ObjectProvider<EmbeddingModel> modelProvider;

    public EmbeddingService(ObjectProvider<EmbeddingModel> modelProvider) {
        this.modelProvider = modelProvider;
    }

    /**
     * @return embedding vector or {@code null} when the model is not
     *         configured / call failed / input is blank.
     */
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        EmbeddingModel model = modelProvider.getIfAvailable();
        if (model == null) {
            log.debug("EmbeddingModel bean absent -- embedding skipped for {} chars", text.length());
            return null;
        }
        try {
            return model.embed(text).content().vector();
        } catch (RuntimeException e) {
            log.warn("Embedding call failed ({} chars): {}", text.length(), e.getMessage());
            return null;
        }
    }

    public boolean isAvailable() {
        return modelProvider.getIfAvailable() != null;
    }
}
