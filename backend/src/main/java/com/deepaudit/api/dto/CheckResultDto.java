package com.deepaudit.api.dto;

import java.time.OffsetDateTime;

public record CheckResultDto(
    Long id,
    Long recordId,
    Long ruleId,
    String ruleCodeSnapshot,
    String ruleNameSnapshot,
    String ruleSeveritySnapshot,
    String ruleDimensionSnapshot,
    String fieldPath,
    String fieldValueSnapshot,
    String hitMessage,
    String llmExplanation,
    OffsetDateTime llmExplainedAt,
    String status,
    OffsetDateTime createdAt
) {
}
