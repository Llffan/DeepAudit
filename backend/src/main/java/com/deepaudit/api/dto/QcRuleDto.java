package com.deepaudit.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Read-shape DTO for {@code qc_rule}. Embedding intentionally NOT exposed
 * (1024 floats x N rules would explode response size; embeddings are an
 * internal detail of the dedup / similarity pipeline).
 */
public record QcRuleDto(
    Long id,
    String code,
    String name,
    String description,
    String dimension,
    String severity,
    JsonNode expression,
    String errorMessageTemplate,
    Boolean enabled,
    Integer version,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
