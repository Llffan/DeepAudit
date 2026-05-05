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
 * Curated medical record. Originally 30 fields (V1 §4.4); V6 expanded to 57
 * fields to cover the full HQMS-mandated front-page identity / contact /
 * outpatient-diagnosis blocks. PHI — soft-delete only.
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

    // ---- V6 expansion: demographics (10) ----------------------------------
    @Column(length = 50)
    private String nationality;

    @Column(length = 50)
    private String ethnicity;

    @Column(name = "marital_status", length = 20)
    private String maritalStatus;

    @Column(length = 100)
    private String occupation;

    /** 不足 1 周岁患者的年龄(天)。≥1 岁时为 null。 */
    @Column(name = "age_days")
    private Integer ageDays;

    @Column(name = "newborn_birth_weight")
    private Integer newbornBirthWeight;

    @Column(name = "newborn_admission_weight")
    private Integer newbornAdmissionWeight;

    @Column(name = "id_card_type", length = 20)
    private String idCardType;

    @Column(name = "birth_place", length = 200)
    private String birthPlace;

    @Column(name = "native_place", length = 200)
    private String nativePlace;

    // ---- V6 expansion: addresses & contacts (12) --------------------------
    @Column(name = "current_address", length = 300)
    private String currentAddress;

    @Column(name = "current_phone", length = 30)
    private String currentPhone;

    @Column(name = "current_zip", length = 10)
    private String currentZip;

    @Column(name = "registered_address", length = 300)
    private String registeredAddress;

    @Column(name = "registered_zip", length = 10)
    private String registeredZip;

    @Column(length = 300)
    private String workplace;

    @Column(name = "work_phone", length = 30)
    private String workPhone;

    @Column(name = "work_zip", length = 10)
    private String workZip;

    @Column(name = "contact_name", length = 100)
    private String contactName;

    @Column(name = "contact_relation", length = 50)
    private String contactRelation;

    @Column(name = "contact_address", length = 300)
    private String contactAddress;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

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

    // ---- V6 expansion: ward / specialty (3) -------------------------------
    @Column(name = "admission_ward", length = 50)
    private String admissionWard;

    @Column(name = "discharge_ward", length = 50)
    private String dischargeWard;

    @Column(name = "specialty_dept", length = 100)
    private String specialtyDept;

    // ---- V6 expansion: outpatient diagnosis (2) ---------------------------
    @Column(name = "outpatient_diagnosis", length = 200)
    private String outpatientDiagnosis;

    @Column(name = "outpatient_diagnosis_code", length = 32)
    private String outpatientDiagnosisCode;

    /** V8: 门急诊诊断的入院情况（HQMS RC014 同字典） */
    @Column(name = "outpatient_admission_condition", length = 20)
    private String outpatientAdmissionCondition;

    /** V8: 入院后确诊日期 */
    @Column(name = "confirmed_after_admission_date")
    private LocalDate confirmedAfterAdmissionDate;

    @Column(name = "main_diagnosis_code", length = 32)
    private String mainDiagnosisCode;

    @Column(name = "main_diagnosis_name", length = 200)
    private String mainDiagnosisName;

    @Column(name = "main_diagnosis_icd_ver", length = 20)
    private String mainDiagnosisIcdVer;

    /** V7: 主诊入院病况（有 / 临床未确定 / 情况不明 / 无） */
    @Column(name = "main_admission_condition", length = 20)
    private String mainAdmissionCondition;

    /** V7: 主诊出院情况（治愈 / 好转 / 未愈 / 死亡 / 其他） */
    @Column(name = "main_discharge_condition", length = 20)
    private String mainDischargeCondition;

    /** V7: 主诊备注 */
    @Column(name = "main_note", columnDefinition = "text")
    private String mainNote;

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

    // ---- V6 accessors ------------------------------------------------------
    public String getNationality() { return nationality; }
    public void setNationality(String nationality) { this.nationality = nationality; }
    public String getEthnicity() { return ethnicity; }
    public void setEthnicity(String ethnicity) { this.ethnicity = ethnicity; }
    public String getMaritalStatus() { return maritalStatus; }
    public void setMaritalStatus(String maritalStatus) { this.maritalStatus = maritalStatus; }
    public String getOccupation() { return occupation; }
    public void setOccupation(String occupation) { this.occupation = occupation; }
    public Integer getAgeDays() { return ageDays; }
    public void setAgeDays(Integer ageDays) { this.ageDays = ageDays; }
    public Integer getNewbornBirthWeight() { return newbornBirthWeight; }
    public void setNewbornBirthWeight(Integer newbornBirthWeight) { this.newbornBirthWeight = newbornBirthWeight; }
    public Integer getNewbornAdmissionWeight() { return newbornAdmissionWeight; }
    public void setNewbornAdmissionWeight(Integer newbornAdmissionWeight) { this.newbornAdmissionWeight = newbornAdmissionWeight; }
    public String getIdCardType() { return idCardType; }
    public void setIdCardType(String idCardType) { this.idCardType = idCardType; }
    public String getBirthPlace() { return birthPlace; }
    public void setBirthPlace(String birthPlace) { this.birthPlace = birthPlace; }
    public String getNativePlace() { return nativePlace; }
    public void setNativePlace(String nativePlace) { this.nativePlace = nativePlace; }
    public String getCurrentAddress() { return currentAddress; }
    public void setCurrentAddress(String currentAddress) { this.currentAddress = currentAddress; }
    public String getCurrentPhone() { return currentPhone; }
    public void setCurrentPhone(String currentPhone) { this.currentPhone = currentPhone; }
    public String getCurrentZip() { return currentZip; }
    public void setCurrentZip(String currentZip) { this.currentZip = currentZip; }
    public String getRegisteredAddress() { return registeredAddress; }
    public void setRegisteredAddress(String registeredAddress) { this.registeredAddress = registeredAddress; }
    public String getRegisteredZip() { return registeredZip; }
    public void setRegisteredZip(String registeredZip) { this.registeredZip = registeredZip; }
    public String getWorkplace() { return workplace; }
    public void setWorkplace(String workplace) { this.workplace = workplace; }
    public String getWorkPhone() { return workPhone; }
    public void setWorkPhone(String workPhone) { this.workPhone = workPhone; }
    public String getWorkZip() { return workZip; }
    public void setWorkZip(String workZip) { this.workZip = workZip; }
    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public String getContactRelation() { return contactRelation; }
    public void setContactRelation(String contactRelation) { this.contactRelation = contactRelation; }
    public String getContactAddress() { return contactAddress; }
    public void setContactAddress(String contactAddress) { this.contactAddress = contactAddress; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public String getAdmissionWard() { return admissionWard; }
    public void setAdmissionWard(String admissionWard) { this.admissionWard = admissionWard; }
    public String getDischargeWard() { return dischargeWard; }
    public void setDischargeWard(String dischargeWard) { this.dischargeWard = dischargeWard; }
    public String getSpecialtyDept() { return specialtyDept; }
    public void setSpecialtyDept(String specialtyDept) { this.specialtyDept = specialtyDept; }
    public String getOutpatientDiagnosis() { return outpatientDiagnosis; }
    public void setOutpatientDiagnosis(String outpatientDiagnosis) { this.outpatientDiagnosis = outpatientDiagnosis; }
    public String getOutpatientDiagnosisCode() { return outpatientDiagnosisCode; }
    public void setOutpatientDiagnosisCode(String outpatientDiagnosisCode) { this.outpatientDiagnosisCode = outpatientDiagnosisCode; }
    public String getMainAdmissionCondition() { return mainAdmissionCondition; }
    public void setMainAdmissionCondition(String mainAdmissionCondition) { this.mainAdmissionCondition = mainAdmissionCondition; }
    public String getMainDischargeCondition() { return mainDischargeCondition; }
    public void setMainDischargeCondition(String mainDischargeCondition) { this.mainDischargeCondition = mainDischargeCondition; }
    public String getMainNote() { return mainNote; }
    public void setMainNote(String mainNote) { this.mainNote = mainNote; }
    public String getOutpatientAdmissionCondition() { return outpatientAdmissionCondition; }
    public void setOutpatientAdmissionCondition(String outpatientAdmissionCondition) { this.outpatientAdmissionCondition = outpatientAdmissionCondition; }
    public LocalDate getConfirmedAfterAdmissionDate() { return confirmedAfterAdmissionDate; }
    public void setConfirmedAfterAdmissionDate(LocalDate confirmedAfterAdmissionDate) { this.confirmedAfterAdmissionDate = confirmedAfterAdmissionDate; }
}
