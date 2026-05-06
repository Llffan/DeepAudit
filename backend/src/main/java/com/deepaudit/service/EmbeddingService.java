package com.deepaudit.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    /**
     * Batch variant for offline indexing (IcdDictEmbeddingService) where
     * a single round-trip per row would dominate latency and burn rate
     * limits. Returns a list aligned 1:1 with the input — failed entries
     * are {@code null} so callers can skip-and-log without losing index
     * alignment with their source rows.
     *
     * <p>When the model bean is absent (no DASHSCOPE_API_KEY) or the
     * input is empty, returns a list of nulls of the same size — same
     * graceful-degrade contract as {@link #embed(String)}.
     *
     * <p>Why we don't fan out per-element on failure: DashScope rate-limits
     * by request count, not row count, so a 25-row batch failure is one
     * event the operator should see and react to — splitting silently
     * could mask a misconfigured key.
     */
    public List<float[]> embedAll(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }
        EmbeddingModel model = modelProvider.getIfAvailable();
        if (model == null) {
            log.debug("EmbeddingModel bean absent -- batch embedding skipped for {} texts", texts.size());
            List<float[]> empty = new ArrayList<>(texts.size());
            for (int i = 0; i < texts.size(); i++) empty.add(null);
            return empty;
        }
        List<TextSegment> segments = new ArrayList<>(texts.size());
        for (String t : texts) {
            // Empty/null inputs become empty TextSegments — the model would
            // reject them, so we mark them as "skip" by keeping a null in
            // the output list at that index. Pre-filter ahead of the call.
            segments.add(t == null || t.isBlank() ? null : TextSegment.from(t));
        }
        // Pack only non-null segments for the actual API call, remember
        // their original indices, and scatter the result back.
        List<TextSegment> packed = new ArrayList<>(segments.size());
        List<Integer> packedIndex = new ArrayList<>(segments.size());
        for (int i = 0; i < segments.size(); i++) {
            if (segments.get(i) != null) {
                packed.add(segments.get(i));
                packedIndex.add(i);
            }
        }
        List<float[]> out = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) out.add(null);
        if (packed.isEmpty()) return out;
        try {
            Response<List<Embedding>> resp = model.embedAll(packed);
            List<Embedding> embeddings = resp.content();
            for (int i = 0; i < embeddings.size(); i++) {
                int origIdx = packedIndex.get(i);
                out.set(origIdx, embeddings.get(i).vector());
            }
        } catch (RuntimeException e) {
            log.warn("Batch embedding call failed (size={}): {}", packed.size(), e.getMessage());
            // out is already all-nulls; return as-is so caller sees uniform failure
        }
        return out;
    }
}
