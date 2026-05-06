package com.deepaudit.api.dto;

/**
 * Outcome of an IcdDictEmbeddingService run.
 *
 * @param mode        "missing" | "all"
 * @param total       rows the run targeted (NULL-only for missing, every row for all)
 * @param succeeded   rows where a vector was written
 * @param failed      rows where the embedding API call failed and the row was left NULL
 * @param skipped     rows skipped because the embedding model bean is absent
 *                    (no DASHSCOPE_API_KEY) — distinct from "failed" so the operator
 *                    knows whether to fix config or chase a transient API error
 * @param durationMs  wall-clock duration of the whole run in ms
 */
public record ReembedResult(
    String mode,
    int total,
    int succeeded,
    int failed,
    int skipped,
    long durationMs
) {}
