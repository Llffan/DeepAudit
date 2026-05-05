package com.deepaudit.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Response body for {@code POST /api/rules/from-natural-language} (T2.5).
 *
 * <p>The LLM is asked to return a structured rule wrapper -- expression
 * plus metadata (name / description / dimension / severity /
 * errorMessageTemplate) -- so the UI can pre-fill the entire form, not
 * just the DSL editor. Any subset of metadata fields may be {@code null}
 * if the model omitted them or they failed enum validation; the UI keeps
 * whatever the user has already typed when a field comes back null.
 *
 * <p>Three possible outcomes mapped onto these fields:
 * <ul>
 *   <li><b>Clean success</b> -- {@code dsl} non-null,
 *       {@code validationErrors} empty, {@code rawOutput} null. Metadata
 *       fields populated when the model emitted them.
 *   <li><b>Generated DSL fails T2.2 validation</b> -- {@code dsl} non-null
 *       with the parsed JSON, {@code validationErrors} populated. UI
 *       should still pre-fill the editor so the user can edit.
 *   <li><b>LLM returned non-JSON</b> -- {@code dsl} null,
 *       {@code rawOutput} carries the raw response so the user can see
 *       what came back, {@code validationErrors} explains the parse
 *       failure.
 * </ul>
 *
 * <p>HTTP status is 200 for all three. The 503 path (LLM not configured)
 * is handled by {@code ServiceUnavailableException} -> the global
 * exception handler, not by this DTO.
 */
public record NlRuleResponse(
    JsonNode dsl,
    String name,
    String description,
    String dimension,
    String severity,
    String errorMessageTemplate,
    List<String> validationErrors,
    String rawOutput,
    /** Codes of operator templates auto-created as a side-effect of this generation. */
    List<String> createdOperatorCodes
) {
    public NlRuleResponse {
        validationErrors     = validationErrors     == null ? List.of() : List.copyOf(validationErrors);
        createdOperatorCodes = createdOperatorCodes == null ? List.of() : List.copyOf(createdOperatorCodes);
    }
}
