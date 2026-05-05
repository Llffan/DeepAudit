package com.deepaudit.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Body of {@code POST /api/medical-records} (T3.3 / plan §6.6).
 *
 * <p>If {@code id} is present, the endpoint updates that row; otherwise
 * a new row is inserted with the supplied {@code status} (typically
 * {@code "draft"} or {@code "confirmed"}).
 *
 * <p>V6 HQMS expansion + V9 supplementary fields（损伤/病理/过敏/血型/医生/
 * 质控）；V9 同时移除了原"主要手术 + 费用"两块共 9 个字段。{@code extra}
 * 可选：null 时不动现有 extra 行（新行写空 {@code {}}）。
 */
public record MedicalRecordSaveRequest(
    Long id,
    String recordNo,

    // Identity (6)
    String name,
    String gender,
    LocalDate birthDate,
    Integer age,
    String idCardMasked,

    // V6 — Demographics (10)
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

    // V6 — Outpatient diagnosis (2) + V8 extras (2)
    String outpatientDiagnosis,
    String outpatientDiagnosisCode,
    String outpatientAdmissionCondition,
    LocalDate confirmedAfterAdmissionDate,

    // Diagnoses — main flattened (8: 5 V1 + 3 V7)
    String mainDiagnosisCode,
    String mainDiagnosisName,
    String mainDiagnosisIcdVer,
    String mainAdmissionCondition,
    String mainDischargeCondition,
    String mainNote,
    Integer otherDiagnosisCount,
    String pathologicalDiagnosis,

    // V9 supplementary — 损伤、中毒（2）
    String injuryPoisoningCause,
    String injuryPoisoningCode,

    // V9 supplementary — 病理扩展（2）
    String pathologicalDiagnosisCode,
    String pathologyNumber,

    // V9 supplementary — 过敏 / 尸检 / 血型（5）
    String drugAllergy,
    String allergyDrugs,
    String autopsy,
    String bloodType,
    String rhBloodType,

    // V9 supplementary — 医生（8）
    String departmentDirector,
    String chiefPhysician,
    String attendingPhysician,
    String residentPhysician,
    String responsibleNurse,
    String traineePhysician,
    String internPhysician,
    String coder,

    // V9 supplementary — 质控（4）
    String recordQuality,
    String qcPhysician,
    String qcNurse,
    LocalDate qcDate,

    // Source / extraction
    String sourceHospital,
    String sourcePdfPath,
    BigDecimal extractionConfidence,

    String status,

    JsonNode extra,

    /**
     * V7: 其他诊断列表（type='other'）。可空：null 时保留现有子表行不动；
     * 传空列表 [] 时清空子表所有 'other' 行。主诊不放这里 —— 主诊由 main 表
     * 平铺字段权威，service 层自动同步到子表 type='main'。
     */
    List<DiagnosisDto> diagnoses
) {
}
