package com.deepaudit.api.dto;

import java.time.OffsetDateTime;

/**
 * One row of {@code check_result} as exposed to the frontend (plan §7.6).
 *
 * <p>Carries the write-time snapshots — never the live {@code qc_rule}
 * fields — so historical reports stay frozen even after rule edits.
 *
 * <p>{@code hasExplanation} flips true once T4.3's streaming explainer
 * fills the {@code llm_explanation} cache; the frontend uses it to decide
 * whether to open the SSE stream or replay the cached explanation.
 */
public record CheckResultItemDto(
    Long id,
    String ruleCode,
    String ruleName,
    String dimension,
    String severity,
    String fieldPath,
    String fieldValue,
    String hitMessage,
    String status,
    boolean hasExplanation,
    OffsetDateTime createdAt
) {
}
