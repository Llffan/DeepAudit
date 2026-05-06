package com.deepaudit.service;

import com.deepaudit.api.dto.ReembedResult;
import com.deepaudit.engine.IcdDictCache;
import com.deepaudit.persistence.entity.IcdDict;
import com.deepaudit.persistence.repository.IcdDictRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Backfills {@code icd_dict.name_embedding} by feeding each row's
 * {@code embedding_text} through {@link EmbeddingService} (DashScope
 * text-embedding-v3 by default — see {@link com.deepaudit.config.LlmAutoConfiguration}).
 *
 * <p>Two operating modes:
 * <ul>
 *   <li>{@link #reembedMissing()} — only rows where {@code name_embedding}
 *       is NULL. Idempotent and cheap; what the {@code POST /reload} flow
 *       and admin button normally call.
 *   <li>{@link #reembedAll()} — clears every existing vector first, then
 *       runs missing. Use after switching embedding model or column dimension;
 *       burns full DashScope quota.
 * </ul>
 *
 * <p>Why this is a separate service from {@link IcdDictLoader}:
 * <ul>
 *   <li><b>Cost</b> — embedding API costs money per call; loader is free.
 *   <li><b>Latency</b> — loader is sub-second; this is seconds-to-minutes.
 *   <li><b>Failure semantics</b> — loader fail-fast at startup is fine;
 *       embedding partial failure must be tolerated and resumable.
 *   <li><b>Triggers</b> — loader runs every boot (idempotent), this only
 *       runs on demand or after JSONL changes.
 * </ul>
 *
 * <p>Synchronous on purpose: caller (admin endpoint) blocks until the
 * batch finishes. For the current ~227-row dictionary that's a few seconds.
 * Once a full ICD-9-CM-3 (8000+) / ICD-10 (30000+) load lands, wrap the
 * public methods in {@code @Async} or hand off to a {@link java.util.concurrent.ExecutorService}.
 */
@Service
public class IcdDictEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(IcdDictEmbeddingService.class);

    /**
     * DashScope text-embedding-v3 accepts up to 25 inputs per call. Stay one
     * under the limit so we don't have to special-case the last batch.
     */
    private static final int BATCH_SIZE = 25;

    private final IcdDictRepository repository;
    private final EmbeddingService embeddingService;
    private final IcdDictCache cache;

    public IcdDictEmbeddingService(IcdDictRepository repository,
                                   EmbeddingService embeddingService,
                                   IcdDictCache cache) {
        this.repository = repository;
        this.embeddingService = embeddingService;
        this.cache = cache;
    }

    /**
     * Embed every row whose {@code name_embedding} is currently NULL.
     * Idempotent. Safe to re-run after partial failures — only the still-NULL
     * rows are retried.
     */
    public ReembedResult reembedMissing() {
        return runEmbedding("missing");
    }

    /**
     * Wipe all existing vectors, then re-run {@link #reembedMissing()}.
     * Use when the embedding model or output dimension changed and old
     * vectors are no longer valid.
     *
     * <p>Deliberately NOT {@code @Transactional} on the whole method —
     * the clear runs in its own implicit tx (Spring Data @Modifying), and
     * the embedding loop relies on each {@code saveAndFlush} committing
     * independently so a partial failure leaves earlier rows persisted.
     */
    public ReembedResult reembedAll() {
        int cleared = repository.clearAllEmbeddings();
        log.info("Cleared {} existing embeddings before full re-embed", cleared);
        return runEmbedding("all");
    }

    private ReembedResult runEmbedding(String mode) {
        long startNanos = System.nanoTime();

        if (!embeddingService.isAvailable()) {
            long pending = repository.countByNameEmbeddingIsNull();
            log.warn("EmbeddingModel bean absent -- skipping {} pending row(s). " +
                     "Set DASHSCOPE_API_KEY and restart, then call /reembed again.", pending);
            return new ReembedResult(mode, (int) pending, 0, 0, (int) pending,
                durationMs(startNanos));
        }

        List<IcdDict> pending = repository.findByNameEmbeddingIsNullOrderByIdAsc();
        if (pending.isEmpty()) {
            log.info("Re-embed mode={} -- no rows pending, nothing to do", mode);
            return new ReembedResult(mode, 0, 0, 0, 0, durationMs(startNanos));
        }
        log.info("Re-embed mode={} starting on {} row(s) in batches of {}",
            mode, pending.size(), BATCH_SIZE);

        int succeeded = 0;
        int failed = 0;

        for (int i = 0; i < pending.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, pending.size());
            List<IcdDict> batch = pending.subList(i, end);

            List<String> texts = new ArrayList<>(batch.size());
            for (IcdDict d : batch) texts.add(d.getEmbeddingText());

            List<float[]> vectors = embeddingService.embedAll(texts);
            // embedAll returns size == input size with nulls for failures.
            int batchOk = 0;
            int batchFail = 0;
            for (int k = 0; k < batch.size(); k++) {
                float[] v = k < vectors.size() ? vectors.get(k) : null;
                if (v == null) {
                    batchFail++;
                    continue;
                }
                IcdDict row = batch.get(k);
                row.setNameEmbedding(v);
                // saveAndFlush per row keeps each successful write
                // independently committed under the JPA tx default —
                // a later batch failing won't roll back earlier ones.
                repository.saveAndFlush(row);
                batchOk++;
            }
            succeeded += batchOk;
            failed += batchFail;
            log.info("Re-embed batch {}/{}: {} ok, {} failed",
                (i / BATCH_SIZE) + 1, ((pending.size() - 1) / BATCH_SIZE) + 1,
                batchOk, batchFail);
        }

        long durationMs = durationMs(startNanos);
        log.info("Re-embed mode={} complete: total={} succeeded={} failed={} duration={}ms",
            mode, pending.size(), succeeded, failed, durationMs);
        // Refresh the in-memory vector index so RuleEvaluator's
        // icdNameSimilar op sees freshly-written embeddings without a
        // service restart. Cheap (~ms) compared to the embed run that
        // just finished.
        if (succeeded > 0) {
            cache.reload();
        }
        return new ReembedResult(mode, pending.size(), succeeded, failed, 0, durationMs);
    }

    private static long durationMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
