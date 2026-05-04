package com.deepaudit.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;

/**
 * Response of {@code POST /api/medical-records/import} (T3.3 / plan §6.6).
 *
 * <p>{@code fields} is the camelCase JSON shape produced by
 * {@link com.deepaudit.service.MedicalRecordExtractor} — the frontend
 * directly merges it into its form state without per-field plumbing.
 *
 * <p>{@code degraded=true} signals the LLM call did not really run
 * (no API key / call failed / output unparseable). The endpoint still
 * returns 200 with empty {@code fields} and {@code extractionConfidence=0}
 * so the operator drops into manual-fill mode without an error toast
 * (plan §8.7 降级原则).
 */
public record MedicalRecordImportResponse(
    JsonNode fields,
    JsonNode extra,
    BigDecimal extractionConfidence,
    String sourcePdfPath,
    boolean degraded,
    String degradedReason
) {
}
