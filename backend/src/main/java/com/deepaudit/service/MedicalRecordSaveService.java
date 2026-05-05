package com.deepaudit.service;

import com.deepaudit.api.dto.DiagnosisDto;
import com.deepaudit.api.dto.MedicalRecordSaveRequest;
import com.deepaudit.api.exception.ConflictException;
import com.deepaudit.api.exception.NotFoundException;
import com.deepaudit.api.exception.ValidationException;
import com.deepaudit.persistence.entity.MedicalRecordDiagnosis;
import com.deepaudit.persistence.entity.MedicalRecordExtra;
import com.deepaudit.persistence.entity.MedicalRecordMain;
import com.deepaudit.persistence.repository.MedicalRecordDiagnosisRepository;
import com.deepaudit.persistence.repository.MedicalRecordExtraRepository;
import com.deepaudit.persistence.repository.MedicalRecordMainRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Persists病案首页 records on {@code POST /api/medical-records} (T3.3).
 *
 * <p>Behaviour:
 * <ul>
 *   <li>{@code id} present in the request → update that row (NotFoundException 404 if missing).
 *   <li>{@code id} absent → insert a new row.
 *   <li>{@code (source_hospital, record_no)} uniqueness violation → 409 Conflict.
 *   <li>extra is upserted alongside; null in the request leaves any existing extra untouched.
 * </ul>
 */
@Service
public class MedicalRecordSaveService {

    private static final Logger log = LoggerFactory.getLogger(MedicalRecordSaveService.class);

    private static final Set<String> ALLOWED_STATUS = Set.of("draft", "confirmed", "checked");

    private final MedicalRecordMainRepository mainRepo;
    private final MedicalRecordExtraRepository extraRepo;
    private final MedicalRecordDiagnosisRepository diagRepo;

    public MedicalRecordSaveService(MedicalRecordMainRepository mainRepo,
                                    MedicalRecordExtraRepository extraRepo,
                                    MedicalRecordDiagnosisRepository diagRepo) {
        this.mainRepo = mainRepo;
        this.extraRepo = extraRepo;
        this.diagRepo = diagRepo;
    }

    @Transactional
    public MedicalRecordMain save(MedicalRecordSaveRequest req) {
        validate(req);

        MedicalRecordMain main;
        boolean isNew;
        if (req.id() != null) {
            main = mainRepo.findById(req.id()).orElseThrow(() ->
                new NotFoundException("病案不存在：id=" + req.id()));
            isNew = false;
        } else {
            main = new MedicalRecordMain();
            isNew = true;
        }
        applyToEntity(main, req);

        try {
            main = mainRepo.save(main);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(
                "同一来源医院下的病案号 '" + req.recordNo() + "' 已存在，请检查是否重复导入");
        }

        upsertExtra(main.getId(), req.extra(), isNew);
        syncDiagnoses(main, req.diagnoses(), isNew);

        log.info("{} medical_record_main id={} status={}",
            isNew ? "Inserted" : "Updated", main.getId(), main.getStatus());
        return main;
    }

    /**
     * Replaces the diagnosis sub-rows for {@code main}. The main row itself is
     * always (re)written from {@code main.mainDiagnosis*} flat columns; the
     * incoming {@code diagnosesIn} list provides the {@code 'other'} rows.
     *
     * <p>Strategy is delete-then-insert: for ~tens of rows the cost is trivial
     * and avoids the diff/upsert machinery. Wrapped in the outer @Transactional
     * so a partial failure rolls back together with the main upsert.
     *
     * <p>{@code diagnosesIn == null} on update means "leave the subtable
     * untouched". On insert (isNew=true) we still write the main subtable row
     * if the main_diagnosis_* fields are populated.
     */
    private void syncDiagnoses(MedicalRecordMain main, List<DiagnosisDto> diagnosesIn, boolean isNew) {
        boolean explicitOthers = diagnosesIn != null;

        // For an update with no incoming list and no main code change, leave
        // the existing rows alone. The main row is rewritten regardless of
        // flag because the user might have edited main_diagnosis_* fields.
        diagRepo.deleteByRecordId(main.getId());

        List<MedicalRecordDiagnosis> rows = new ArrayList<>();

        // Mirror main_diagnosis_* into a synthetic 'main' row when the main
        // diagnosis name/code is populated. Skipped when only the code/name
        // is null (not a useful subtable row).
        if (main.getMainDiagnosisName() != null && !main.getMainDiagnosisName().isBlank()) {
            MedicalRecordDiagnosis m = new MedicalRecordDiagnosis();
            m.setRecordId(main.getId());
            m.setDiagType("main");
            m.setSeqNo(1);
            m.setDiagnosisName(main.getMainDiagnosisName());
            m.setDiagnosisCode(main.getMainDiagnosisCode());
            m.setIcdVersion(main.getMainDiagnosisIcdVer());
            m.setAdmissionCondition(main.getMainAdmissionCondition());
            m.setDischargeCondition(main.getMainDischargeCondition());
            m.setNote(main.getMainNote());
            rows.add(m);
        }

        // Other diagnoses come from the request. Filter out rows that the
        // frontend mistakenly marked as 'main' (those belong to the flattened
        // main fields above) — silently dropped to keep the contract simple.
        if (explicitOthers) {
            int seq = 1;
            for (DiagnosisDto d : diagnosesIn) {
                if (d == null) continue;
                if ("main".equals(d.diagType())) continue; // Authority is main.* columns.
                if (d.diagnosisName() == null || d.diagnosisName().isBlank()) continue;

                MedicalRecordDiagnosis o = new MedicalRecordDiagnosis();
                o.setRecordId(main.getId());
                o.setDiagType("other");
                o.setSeqNo(d.seqNo() != null ? d.seqNo() : seq);
                o.setDiagnosisName(d.diagnosisName());
                o.setDiagnosisCode(d.diagnosisCode());
                o.setIcdVersion(d.icdVersion());
                o.setAdmissionCondition(d.admissionCondition());
                o.setDischargeCondition(d.dischargeCondition());
                o.setNote(d.note());
                rows.add(o);
                seq++;
            }
        }

        if (!rows.isEmpty()) {
            diagRepo.saveAll(rows);
        }

        // Refresh the cached count column so existing rules referencing
        // otherDiagnosisCount stay accurate.
        long otherCount = diagRepo.countByRecordIdAndDiagType(main.getId(), "other");
        main.setOtherDiagnosisCount((int) otherCount);
        mainRepo.save(main);
    }

    private void upsertExtra(Long recordId, JsonNode extra, boolean isNewMain) {
        if (extra == null) {
            // Frontend doesn't always round-trip extra. For brand-new rows we
            // still need a sidecar row (DDL has FK + UNIQUE on record_id);
            // for updates, leave the existing one alone.
            if (isNewMain) {
                writeExtra(recordId, JsonNodeFactory.instance.objectNode());
            }
            return;
        }
        writeExtra(recordId, extra);
    }

    private void writeExtra(Long recordId, JsonNode extra) {
        MedicalRecordExtra row = extraRepo.findByRecordId(recordId)
            .orElseGet(MedicalRecordExtra::new);
        row.setRecordId(recordId);
        row.setExtraFields(extra);
        extraRepo.save(row);
    }

    private static void validate(MedicalRecordSaveRequest req) {
        List<String> errors = new ArrayList<>();
        if (req.recordNo() == null || req.recordNo().isBlank()) {
            errors.add("recordNo: required");
        } else if (req.recordNo().length() > 64) {
            errors.add("recordNo: must be ≤ 64 characters");
        }
        if (req.status() != null && !ALLOWED_STATUS.contains(req.status())) {
            errors.add("status: must be one of " + ALLOWED_STATUS);
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    private static void applyToEntity(MedicalRecordMain m, MedicalRecordSaveRequest r) {
        m.setRecordNo(r.recordNo());
        m.setName(r.name());
        m.setGender(r.gender());
        m.setBirthDate(r.birthDate());
        m.setAge(r.age());
        m.setIdCardMasked(r.idCardMasked());

        // V6 demographics
        m.setNationality(r.nationality());
        m.setEthnicity(r.ethnicity());
        m.setMaritalStatus(r.maritalStatus());
        m.setOccupation(r.occupation());
        m.setAgeDays(r.ageDays());
        m.setNewbornBirthWeight(r.newbornBirthWeight());
        m.setNewbornAdmissionWeight(r.newbornAdmissionWeight());
        m.setIdCardType(r.idCardType());
        m.setBirthPlace(r.birthPlace());
        m.setNativePlace(r.nativePlace());

        // V6 contacts
        m.setCurrentAddress(r.currentAddress());
        m.setCurrentPhone(r.currentPhone());
        m.setCurrentZip(r.currentZip());
        m.setRegisteredAddress(r.registeredAddress());
        m.setRegisteredZip(r.registeredZip());
        m.setWorkplace(r.workplace());
        m.setWorkPhone(r.workPhone());
        m.setWorkZip(r.workZip());
        m.setContactName(r.contactName());
        m.setContactRelation(r.contactRelation());
        m.setContactAddress(r.contactAddress());
        m.setContactPhone(r.contactPhone());

        m.setAdmissionDate(r.admissionDate());
        m.setDischargeDate(r.dischargeDate());
        m.setLengthOfStay(r.lengthOfStay());
        m.setAdmissionDept(r.admissionDept());
        m.setDischargeDept(r.dischargeDept());
        m.setAdmissionRoute(r.admissionRoute());
        m.setDischargeStatus(r.dischargeStatus());

        // V6 ward / specialty
        m.setAdmissionWard(r.admissionWard());
        m.setDischargeWard(r.dischargeWard());
        m.setSpecialtyDept(r.specialtyDept());

        // V6 outpatient diagnosis + V8 extras
        m.setOutpatientDiagnosis(r.outpatientDiagnosis());
        m.setOutpatientDiagnosisCode(r.outpatientDiagnosisCode());
        m.setOutpatientAdmissionCondition(r.outpatientAdmissionCondition());
        m.setConfirmedAfterAdmissionDate(r.confirmedAfterAdmissionDate());

        m.setMainDiagnosisCode(r.mainDiagnosisCode());
        m.setMainDiagnosisName(r.mainDiagnosisName());
        m.setMainDiagnosisIcdVer(r.mainDiagnosisIcdVer());
        m.setMainAdmissionCondition(r.mainAdmissionCondition());
        m.setMainDischargeCondition(r.mainDischargeCondition());
        m.setMainNote(r.mainNote());
        m.setOtherDiagnosisCount(r.otherDiagnosisCount());
        m.setPathologicalDiagnosis(r.pathologicalDiagnosis());

        // V9 supplementary — 损伤、中毒
        m.setInjuryPoisoningCause(r.injuryPoisoningCause());
        m.setInjuryPoisoningCode(r.injuryPoisoningCode());

        // V9 supplementary — 病理扩展
        m.setPathologicalDiagnosisCode(r.pathologicalDiagnosisCode());
        m.setPathologyNumber(r.pathologyNumber());

        // V9 supplementary — 过敏 / 尸检 / 血型
        m.setDrugAllergy(r.drugAllergy());
        m.setAllergyDrugs(r.allergyDrugs());
        m.setAutopsy(r.autopsy());
        m.setBloodType(r.bloodType());
        m.setRhBloodType(r.rhBloodType());

        // V9 supplementary — 医生
        m.setDepartmentDirector(r.departmentDirector());
        m.setChiefPhysician(r.chiefPhysician());
        m.setAttendingPhysician(r.attendingPhysician());
        m.setResidentPhysician(r.residentPhysician());
        m.setResponsibleNurse(r.responsibleNurse());
        m.setTraineePhysician(r.traineePhysician());
        m.setInternPhysician(r.internPhysician());
        m.setCoder(r.coder());

        // V9 supplementary — 质控
        m.setRecordQuality(r.recordQuality());
        m.setQcPhysician(r.qcPhysician());
        m.setQcNurse(r.qcNurse());
        m.setQcDate(r.qcDate());

        m.setSourceHospital(r.sourceHospital());
        m.setSourcePdfPath(r.sourcePdfPath());
        m.setExtractionConfidence(r.extractionConfidence());

        if (r.status() != null) {
            m.setStatus(r.status());
        }
    }
}
