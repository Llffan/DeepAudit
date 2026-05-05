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
 * fields for HQMS front-page; V9 (2026-05-05) dropped the operation + cost
 * blocks (9 fields) and added supplementary 损伤/病理/过敏/血型/医生/质控
 * (21 fields). PHI — soft-delete only.
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

    // ---- V9 supplementary: 损伤、中毒（2） --------------------------------
    @Column(name = "injury_poisoning_cause", length = 300)
    private String injuryPoisoningCause;

    @Column(name = "injury_poisoning_code", length = 32)
    private String injuryPoisoningCode;

    // ---- V9 supplementary: 病理扩展（2；诊断名沿用旧列）---------------------
    @Column(name = "pathological_diagnosis_code", length = 32)
    private String pathologicalDiagnosisCode;

    @Column(name = "pathology_number", length = 64)
    private String pathologyNumber;

    // ---- V9 supplementary: 过敏 / 尸检 / 血型（5）-------------------------
    /** 1=无 / 2=有 */
    @Column(name = "drug_allergy", length = 20)
    private String drugAllergy;

    @Column(name = "allergy_drugs", length = 300)
    private String allergyDrugs;

    /** 1=是 / 2=否（仅死亡患者填写） */
    @Column(length = 20)
    private String autopsy;

    /** A / B / O / AB / 不详 / 未查 */
    @Column(name = "blood_type", length = 20)
    private String bloodType;

    /** 阴 / 阳 / 不详 / 未查 */
    @Column(name = "rh_blood_type", length = 20)
    private String rhBloodType;

    // ---- V9 supplementary: 医生（8） --------------------------------------
    @Column(name = "department_director", length = 100)
    private String departmentDirector;

    @Column(name = "chief_physician", length = 100)
    private String chiefPhysician;

    @Column(name = "attending_physician", length = 100)
    private String attendingPhysician;

    @Column(name = "resident_physician", length = 100)
    private String residentPhysician;

    @Column(name = "responsible_nurse", length = 100)
    private String responsibleNurse;

    @Column(name = "trainee_physician", length = 100)
    private String traineePhysician;

    @Column(name = "intern_physician", length = 100)
    private String internPhysician;

    @Column(length = 100)
    private String coder;

    // ---- V9 supplementary: 质控（4） --------------------------------------
    /** 甲 / 乙 / 丙 */
    @Column(name = "record_quality", length = 20)
    private String recordQuality;

    @Column(name = "qc_physician", length = 100)
    private String qcPhysician;

    @Column(name = "qc_nurse", length = 100)
    private String qcNurse;

    @Column(name = "qc_date")
    private LocalDate qcDate;

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
    // ---- V9 supplementary accessors ---------------------------------------
    public String getInjuryPoisoningCause() { return injuryPoisoningCause; }
    public void setInjuryPoisoningCause(String injuryPoisoningCause) { this.injuryPoisoningCause = injuryPoisoningCause; }
    public String getInjuryPoisoningCode() { return injuryPoisoningCode; }
    public void setInjuryPoisoningCode(String injuryPoisoningCode) { this.injuryPoisoningCode = injuryPoisoningCode; }
    public String getPathologicalDiagnosisCode() { return pathologicalDiagnosisCode; }
    public void setPathologicalDiagnosisCode(String pathologicalDiagnosisCode) { this.pathologicalDiagnosisCode = pathologicalDiagnosisCode; }
    public String getPathologyNumber() { return pathologyNumber; }
    public void setPathologyNumber(String pathologyNumber) { this.pathologyNumber = pathologyNumber; }
    public String getDrugAllergy() { return drugAllergy; }
    public void setDrugAllergy(String drugAllergy) { this.drugAllergy = drugAllergy; }
    public String getAllergyDrugs() { return allergyDrugs; }
    public void setAllergyDrugs(String allergyDrugs) { this.allergyDrugs = allergyDrugs; }
    public String getAutopsy() { return autopsy; }
    public void setAutopsy(String autopsy) { this.autopsy = autopsy; }
    public String getBloodType() { return bloodType; }
    public void setBloodType(String bloodType) { this.bloodType = bloodType; }
    public String getRhBloodType() { return rhBloodType; }
    public void setRhBloodType(String rhBloodType) { this.rhBloodType = rhBloodType; }
    public String getDepartmentDirector() { return departmentDirector; }
    public void setDepartmentDirector(String departmentDirector) { this.departmentDirector = departmentDirector; }
    public String getChiefPhysician() { return chiefPhysician; }
    public void setChiefPhysician(String chiefPhysician) { this.chiefPhysician = chiefPhysician; }
    public String getAttendingPhysician() { return attendingPhysician; }
    public void setAttendingPhysician(String attendingPhysician) { this.attendingPhysician = attendingPhysician; }
    public String getResidentPhysician() { return residentPhysician; }
    public void setResidentPhysician(String residentPhysician) { this.residentPhysician = residentPhysician; }
    public String getResponsibleNurse() { return responsibleNurse; }
    public void setResponsibleNurse(String responsibleNurse) { this.responsibleNurse = responsibleNurse; }
    public String getTraineePhysician() { return traineePhysician; }
    public void setTraineePhysician(String traineePhysician) { this.traineePhysician = traineePhysician; }
    public String getInternPhysician() { return internPhysician; }
    public void setInternPhysician(String internPhysician) { this.internPhysician = internPhysician; }
    public String getCoder() { return coder; }
    public void setCoder(String coder) { this.coder = coder; }
    public String getRecordQuality() { return recordQuality; }
    public void setRecordQuality(String recordQuality) { this.recordQuality = recordQuality; }
    public String getQcPhysician() { return qcPhysician; }
    public void setQcPhysician(String qcPhysician) { this.qcPhysician = qcPhysician; }
    public String getQcNurse() { return qcNurse; }
    public void setQcNurse(String qcNurse) { this.qcNurse = qcNurse; }
    public LocalDate getQcDate() { return qcDate; }
    public void setQcDate(LocalDate qcDate) { this.qcDate = qcDate; }
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
