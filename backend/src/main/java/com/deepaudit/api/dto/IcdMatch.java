package com.deepaudit.api.dto;

/**
 * One result row from {@code IcdDictCache.topKByCosine} — used by the
 * {@code searchByName} tool the LLM calls. Similarity is the cosine
 * value in {@code [-1, 1]} (1 = identical, 0 = unrelated, &lt; 0 =
 * inversely related — only theoretically achievable on text embeddings).
 *
 * <p>Kept as a record (immutable, no equals overrides needed) so the
 * langchain4j tool serializer renders it cleanly when the LLM inspects
 * tool output.
 */
public record IcdMatch(
    String code,
    String name,
    String category,
    double similarity
) {}
