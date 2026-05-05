package com.deepaudit.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Diagnosis row. One {@code MedicalRecordMain} has 1 row of {@code diag_type='main'}
 * (mirrored from main_diagnosis_* columns) plus 0..N rows of {@code diag_type='other'}.
 */
@Entity
@Table(name = "medical_record_diagnosis")
public class MedicalRecordDiagnosis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "record_id", nullable = false)
    private Long recordId;

    /** main | other */
    @Column(name = "diag_type", nullable = false, length = 20)
    private String diagType;

    @Column(name = "seq_no", nullable = false)
    private Integer seqNo = 1;

    @Column(name = "diagnosis_name", nullable = false, length = 200)
    private String diagnosisName;

    @Column(name = "diagnosis_code", length = 32)
    private String diagnosisCode;

    @Column(name = "icd_version", length = 20)
    private String icdVersion;

    /** 有 / 临床未确定 / 情况不明 / 无 */
    @Column(name = "admission_condition", length = 20)
    private String admissionCondition;

    /** 治愈 / 好转 / 未愈 / 死亡 / 其他 */
    @Column(name = "discharge_condition", length = 20)
    private String dischargeCondition;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }
    public String getDiagType() { return diagType; }
    public void setDiagType(String diagType) { this.diagType = diagType; }
    public Integer getSeqNo() { return seqNo; }
    public void setSeqNo(Integer seqNo) { this.seqNo = seqNo; }
    public String getDiagnosisName() { return diagnosisName; }
    public void setDiagnosisName(String diagnosisName) { this.diagnosisName = diagnosisName; }
    public String getDiagnosisCode() { return diagnosisCode; }
    public void setDiagnosisCode(String diagnosisCode) { this.diagnosisCode = diagnosisCode; }
    public String getIcdVersion() { return icdVersion; }
    public void setIcdVersion(String icdVersion) { this.icdVersion = icdVersion; }
    public String getAdmissionCondition() { return admissionCondition; }
    public void setAdmissionCondition(String admissionCondition) { this.admissionCondition = admissionCondition; }
    public String getDischargeCondition() { return dischargeCondition; }
    public void setDischargeCondition(String dischargeCondition) { this.dischargeCondition = dischargeCondition; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
