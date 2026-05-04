package com.deepaudit.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Read-shape DTO for {@code medical_record_main}. All 30 curated fields
 * plus extraction metadata and workflow status.
 */
public record MedicalRecordMainDto(
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
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
