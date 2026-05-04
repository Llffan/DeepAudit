package com.deepaudit.service;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Result of running {@link MedicalRecordExtractor} on a病案首页 PDF (T3.2).
 *
 * <p>{@code main} is a JSON object with the 30 curated fields keyed to
 * {@link com.deepaudit.persistence.entity.MedicalRecordMain} columns
 * (camelCase). Missing-from-LLM fields are present as {@code null}.
 *
 * <p>{@code extra} is whatever else the model considered worth keeping,
 * stored verbatim in {@code medical_record_extra.extra_fields} for forensic
 * value (plan §6.4 "其他抽取字段（只读）"). May be an empty object.
 *
 * <p>{@code confidence} is the model's self-reported overall confidence in
 * the extraction (0.0–1.0). Frontend uses this to traffic-light the field
 * form (plan §6.5).
 *
 * <p>{@code degraded} signals that extraction did NOT really run -- LLM
 * unavailable, JSON parse failed, or schema rejected. The caller still
 * persists the record (status=draft) but with the empty schema and
 * confidence=0 so the operator drops into the manual-fill flow (plan §8.7).
 */
public record ExtractionResult(
    JsonNode main,
    JsonNode extra,
    double confidence,
    boolean degraded,
    String degradedReason
) {
}
