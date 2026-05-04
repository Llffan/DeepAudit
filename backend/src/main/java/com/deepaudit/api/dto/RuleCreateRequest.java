package com.deepaudit.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;

/**
 * Request body for {@code POST /api/rules}. {@code enabled} is
 * boxed so callers can omit it (entity default is true). The
 * {@code description_embedding} column is intentionally not part of
 * this DTO -- it is computed from {@code name} + {@code description}
 * by the embedding service (T2.4) on first save.
 */
public record RuleCreateRequest(
    String code,
    String name,
    String description,
    String dimension,
    String severity,
    JsonNode expression,
    String errorMessageTemplate,
    Boolean enabled,
    LocalDate effectiveFrom,
    LocalDate effectiveTo
) {
}
