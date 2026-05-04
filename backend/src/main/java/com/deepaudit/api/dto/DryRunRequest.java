package com.deepaudit.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Request body for {@code POST /api/rules/dry-run}.
 *
 * <p>Both fields use {@link JsonNode} so the sandbox can submit
 * arbitrary partial records and arbitrary DSL trees without binding
 * to entity-shaped DTOs. The controller converts {@code record} to
 * {@code MedicalRecordMain} via Jackson before calling the evaluator.
 */
public record DryRunRequest(
    JsonNode expression,
    JsonNode record
) {
}
