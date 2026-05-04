package com.deepaudit.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;

/**
 * Request body for {@code PUT /api/rules/{id}}. Same shape as
 * {@link RuleCreateRequest} except {@code code} is omitted -- it is
 * the natural key for cross-references and stays immutable post-create.
 */
public record RuleUpdateRequest(
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
