package com.deepaudit.api.dto;

/**
 * Diagnosis row for {@code medical_record_diagnosis}. Used both in
 * {@code MedicalRecordSaveRequest.diagnoses} (input) and
 * {@code MedicalRecordMainDto.diagnoses} (output).
 *
 * <p>Field semantics:
 * <ul>
 *   <li>{@code diagType}: {@code "main"} or {@code "other"}.
 *   <li>{@code seqNo}: row order within the same {@code (recordId, diagType)};
 *       main diagnosis always seq=1, other diagnoses 1..N.
 *   <li>{@code admissionCondition}: HQMS RC014 — 有 / 临床未确定 / 情况不明 / 无.
 *   <li>{@code dischargeCondition}: HQMS RC015 — 治愈 / 好转 / 未愈 / 死亡 / 其他.
 * </ul>
 */
public record DiagnosisDto(
    String diagType,
    Integer seqNo,
    String diagnosisName,
    String diagnosisCode,
    String icdVersion,
    String admissionCondition,
    String dischargeCondition,
    String note
) {
}
