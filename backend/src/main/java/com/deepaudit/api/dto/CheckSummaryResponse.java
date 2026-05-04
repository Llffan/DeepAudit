package com.deepaudit.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Response shape for both T4 endpoints (plan §7.6):
 *
 * <ul>
 *   <li>{@code POST /api/medical-records/{id}/check} — runs the engine, persists hits.
 *   <li>{@code GET  /api/check-results/{recordId}} — replays the last persisted state.
 * </ul>
 *
 * <p>Two read carriers worth highlighting:
 * <ul>
 *   <li>{@code recordStatus} lets the frontend tell apart
 *       <em>"draft, never checked"</em> (results=[]) from
 *       <em>"checked, all passed"</em> (results=[]) — they look identical
 *       otherwise.
 *   <li>{@code skippedRules} is always empty for the GET path (the GET
 *       does not re-evaluate); the POST path fills it when DSL throws.
 * </ul>
 */
public record CheckSummaryResponse(
    Long recordId,
    String recordStatus,
    OffsetDateTime checkedAt,
    SummaryStats summary,
    List<CheckResultItemDto> results,
    List<SkippedRuleDto> skippedRules
) {
    public record SummaryStats(
        int totalRulesEvaluated,
        int totalHits,
        Map<String, Integer> byDimension,
        Map<String, Integer> bySeverity
    ) {
    }
}
