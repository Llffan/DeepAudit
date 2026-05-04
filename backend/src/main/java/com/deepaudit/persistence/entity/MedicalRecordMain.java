package com.deepaudit.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Curated 30-field medical record (V1 §4.4). PHI -- soft-delete only.
 */
@Entity
@Table(
    name = "medical_record_main",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_mrm_source_record",
        columnNames = {"source_hospital", "record_no"})
)
@SQLRestriction("deleted_at IS NULL")
public class MedicalRecordMain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "record_no", nullable = false, length = 64)
    private String recordNo;

    @Column(length = 100)
    private String name;

    @Column(length = 10)
    private String gender;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    private Integer age;

    @Column(name = "id_card_masked", length = 32)
    private String idCardMasked;

    @Column(name = "admission_date")
    private LocalDate admissionDate;

    @Column(name = "discharge_date")
    private LocalDate dischargeDate;

    @Column(name = "length_of_stay")
    private Integer lengthOfStay;

    @Column(name = "admission_dept", length = 100)
    private String admissionDept;

    @Column(name = "discharge_dept", length = 100)
    private String dischargeDept;

    @Column(name = "admission_route", length = 50)
    private String admissionRoute;

    @Column(name = "discharge_status", length = 50)
    private String dischargeStatus;

    @Column(name = "main_diagnosis_code", length = 32)
    private String mainDiagnosisCode;

    @Column(name = "main_diagnosis_name", length = 200)
    private String mainDiagnosisName;

    @Column(name = "main_diagnosis_icd_ver", length = 20)
    private String mainDiagnosisIcdVer;

    @Column(name = "other_diagnosis_count")
    private Integer otherDiagnosisCount;

    @Column(name = "pathological_diagnosis", length = 200)
    private String pathologicalDiagnosis;

    @Column(name = "main_operation_code", length = 32)
    private String mainOperationCode;

    @Column(name = "main_operation_name", length = 200)
    private String mainOperationName;

    @Column(name = "operation_date")
    private LocalDate operationDate;

    @Column(length = 100)
    private String operator;

    @Column(name = "anesthesia_method", length = 50)
    private String anesthesiaMethod;

    @Column(name = "total_cost", precision = 12, scale = 2)
    private BigDecimal totalCost;

    @Column(name = "drug_cost", precision = 12, scale = 2)
    private BigDecimal drugCost;

    @Column(name = "operation_cost", precision = 12, scale = 2)
    private BigDecimal operationCost;

    @Column(name = "medical_service_cost", precision = 12, scale = 2)
    private BigDecimal medicalServiceCost;

    @Column(name = "source_hospital", length = 200)
    private String sourceHospital;

    @Column(name = "source_pdf_path", length = 500)
    private String sourcePdfPath;

    @Column(name = "extraction_confidence", precision = 3, scale = 2)
    private BigDecimal extractionConfidence;

    /** draft | confirmed | checked */
    @Column(nullable = false, length = 20)
    private String status = "draft";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRecordNo() { return recordNo; }
    public void setRecordNo(String recordNo) { this.recordNo = recordNo; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
    public String getIdCardMasked() { return idCardMasked; }
    public void setIdCardMasked(String idCardMasked) { this.idCardMasked = idCardMasked; }
    public LocalDate getAdmissionDate() { return admissionDate; }
    public void setAdmissionDate(LocalDate admissionDate) { this.admissionDate = admissionDate; }
    public LocalDate getDischargeDate() { return dischargeDate; }
    public void setDischargeDate(LocalDate dischargeDate) { this.dischargeDate = dischargeDate; }
    public Integer getLengthOfStay() { return lengthOfStay; }
    public void setLengthOfStay(Integer lengthOfStay) { this.lengthOfStay = lengthOfStay; }
    public String getAdmissionDept() { return admissionDept; }
    public void setAdmissionDept(String admissionDept) { this.admissionDept = admissionDept; }
    public String getDischargeDept() { return dischargeDept; }
    public void setDischargeDept(String dischargeDept) { this.dischargeDept = dischargeDept; }
    public String getAdmissionRoute() { return admissionRoute; }
    public void setAdmissionRoute(String admissionRoute) { this.admissionRoute = admissionRoute; }
    public String getDischargeStatus() { return dischargeStatus; }
    public void setDischargeStatus(String dischargeStatus) { this.dischargeStatus = dischargeStatus; }
    public String getMainDiagnosisCode() { return mainDiagnosisCode; }
    public void setMainDiagnosisCode(String mainDiagnosisCode) { this.mainDiagnosisCode = mainDiagnosisCode; }
    public String getMainDiagnosisName() { return mainDiagnosisName; }
    public void setMainDiagnosisName(String mainDiagnosisName) { this.mainDiagnosisName = mainDiagnosisName; }
    public String getMainDiagnosisIcdVer() { return mainDiagnosisIcdVer; }
    public void setMainDiagnosisIcdVer(String mainDiagnosisIcdVer) { this.mainDiagnosisIcdVer = mainDiagnosisIcdVer; }
    public Integer getOtherDiagnosisCount() { return otherDiagnosisCount; }
    public void setOtherDiagnosisCount(Integer otherDiagnosisCount) { this.otherDiagnosisCount = otherDiagnosisCount; }
    public String getPathologicalDiagnosis() { return pathologicalDiagnosis; }
    public void setPathologicalDiagnosis(String pathologicalDiagnosis) { this.pathologicalDiagnosis = pathologicalDiagnosis; }
    public String getMainOperationCode() { return mainOperationCode; }
    public void setMainOperationCode(String mainOperationCode) { this.mainOperationCode = mainOperationCode; }
    public String getMainOperationName() { return mainOperationName; }
    public void setMainOperationName(String mainOperationName) { this.mainOperationName = mainOperationName; }
    public LocalDate getOperationDate() { return operationDate; }
    public void setOperationDate(LocalDate operationDate) { this.operationDate = operationDate; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getAnesthesiaMethod() { return anesthesiaMethod; }
    public void setAnesthesiaMethod(String anesthesiaMethod) { this.anesthesiaMethod = anesthesiaMethod; }
    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }
    public BigDecimal getDrugCost() { return drugCost; }
    public void setDrugCost(BigDecimal drugCost) { this.drugCost = drugCost; }
    public BigDecimal getOperationCost() { return operationCost; }
    public void setOperationCost(BigDecimal operationCost) { this.operationCost = operationCost; }
    public BigDecimal getMedicalServiceCost() { return medicalServiceCost; }
    public void setMedicalServiceCost(BigDecimal medicalServiceCost) { this.medicalServiceCost = medicalServiceCost; }
    public String getSourceHospital() { return sourceHospital; }
    public void setSourceHospital(String sourceHospital) { this.sourceHospital = sourceHospital; }
    public String getSourcePdfPath() { return sourcePdfPath; }
    public void setSourcePdfPath(String sourcePdfPath) { this.sourcePdfPath = sourcePdfPath; }
    public BigDecimal getExtractionConfidence() { return extractionConfidence; }
    public void setExtractionConfidence(BigDecimal extractionConfidence) { this.extractionConfidence = extractionConfidence; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }
}
