package com.deepaudit.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Body of {@code POST /api/medical-records} (T3.3 / plan §6.6).
 *
 * <p>If {@code id} is present, the endpoint updates that row; otherwise
 * a new row is inserted with the supplied {@code status} (typically
 * {@code "draft"} or {@code "confirmed"}).
 *
 * <p>{@code extra} is optional — the frontend currently doesn't round-trip
 * the LLM-returned extra block, so when null the upsert simply leaves the
 * existing extra row untouched (or writes an empty {@code {}} for new rows).
 */
public record MedicalRecordSaveRequest(
    Long id,
    String recordNo,
    String name,
    String gender,
    LocalDate birthDate,
    Integer age,
    String idCardMasked,

    LocalDate admissionDate,
    LocalDate dischargeDate,
    Integer lengthOfStay,
    String admissionDept,
    String dischargeDept,
    String admissionRoute,
    String dischargeStatus,

    String mainDiagnosisCode,
    String mainDiagnosisName,
    String mainDiagnosisIcdVer,
    Integer otherDiagnosisCount,
    String pathologicalDiagnosis,

    String mainOperationCode,
    String mainOperationName,
    LocalDate operationDate,
    String operator,
    String anesthesiaMethod,

    BigDecimal totalCost,
    BigDecimal drugCost,
    BigDecimal operationCost,
    BigDecimal medicalServiceCost,

    String sourceHospital,
    String sourcePdfPath,
    BigDecimal extractionConfidence,

    String status,

    JsonNode extra
) {
}
