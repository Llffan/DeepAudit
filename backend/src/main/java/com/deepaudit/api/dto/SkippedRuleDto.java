package com.deepaudit.api.dto;

/**
 * A rule whose DSL threw at evaluation time and was skipped (plan §7.2
 * fault-tolerance). Surfaced in the response so the frontend can show
 * an alert banner ("3 rules skipped") and ops can fix the broken rule
 * without reading server logs.
 */
public record SkippedRuleDto(
    String ruleCode,
    String reason
) {
}
