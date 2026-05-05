package com.deepaudit.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Read-shape DTO for {@code medical_record_main}. 57 curated fields
 * (V1 §4.4 + V6 HQMS expansion) plus extraction metadata and workflow status.
 */
public record MedicalRecordMainDto(
    Long id,
    String recordNo,

    // Identity (6)
    String name,
    String gender,
    LocalDate birthDate,
    Integer age,
    String idCardMasked,

    // V6 — Demographics expansion (10)
    String nationality,
    String ethnicity,
    String maritalStatus,
    String occupation,
    Integer ageDays,
    Integer newbornBirthWeight,
    Integer newbornAdmissionWeight,
    String idCardType,
    String birthPlace,
    String nativePlace,

    // V6 — Address & contacts (12)
    String currentAddress,
    String currentPhone,
    String currentZip,
    String registeredAddress,
    String registeredZip,
    String workplace,
    String workPhone,
    String workZip,
    String contactName,
    String contactRelation,
    String contactAddress,
    String contactPhone,

    // Admission / discharge (7)
    LocalDate admissionDate,
    LocalDate dischargeDate,
    Integer lengthOfStay,
    String admissionDept,
    String dischargeDept,
    String admissionRoute,
    String dischargeStatus,

    // V6 — Ward / specialty (3)
    String admissionWard,
    String dischargeWard,
    String specialtyDept,

    // V6 — Outpatient diagnosis (2)
    String outpatientDiagnosis,
    String outpatientDiagnosisCode,

    // Diagnoses — main flattened (8: 5 V1 + 3 V7)
    String mainDiagnosisCode,
    String mainDiagnosisName,
    String mainDiagnosisIcdVer,
    String mainAdmissionCondition,
    String mainDischargeCondition,
    String mainNote,
    Integer otherDiagnosisCount,
    String pathologicalDiagnosis,

    // Operations (5)
    String mainOperationCode,
    String mainOperationName,
    LocalDate operationDate,
    String operator,
    String anesthesiaMethod,

    // Cost (4)
    BigDecimal totalCost,
    BigDecimal drugCost,
    BigDecimal operationCost,
    BigDecimal medicalServiceCost,

    // Source / extraction metadata (3)
    String sourceHospital,
    String sourcePdfPath,
    BigDecimal extractionConfidence,

    // V7 — Full diagnosis list (含主诊在内的全部子表行；按 diagType,seqNo 排序)
    List<DiagnosisDto> diagnoses,

    // Workflow
    String status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
