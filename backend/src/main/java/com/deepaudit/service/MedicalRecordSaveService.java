package com.deepaudit.service;

import com.deepaudit.api.dto.MedicalRecordSaveRequest;
import com.deepaudit.api.exception.ConflictException;
import com.deepaudit.api.exception.NotFoundException;
import com.deepaudit.api.exception.ValidationException;
import com.deepaudit.persistence.entity.MedicalRecordExtra;
import com.deepaudit.persistence.entity.MedicalRecordMain;
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

    public MedicalRecordSaveService(MedicalRecordMainRepository mainRepo,
                                    MedicalRecordExtraRepository extraRepo) {
        this.mainRepo = mainRepo;
        this.extraRepo = extraRepo;
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

        log.info("{} medical_record_main id={} status={}",
            isNew ? "Inserted" : "Updated", main.getId(), main.getStatus());
        return main;
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

        m.setAdmissionDate(r.admissionDate());
        m.setDischargeDate(r.dischargeDate());
        m.setLengthOfStay(r.lengthOfStay());
        m.setAdmissionDept(r.admissionDept());
        m.setDischargeDept(r.dischargeDept());
        m.setAdmissionRoute(r.admissionRoute());
        m.setDischargeStatus(r.dischargeStatus());

        m.setMainDiagnosisCode(r.mainDiagnosisCode());
        m.setMainDiagnosisName(r.mainDiagnosisName());
        m.setMainDiagnosisIcdVer(r.mainDiagnosisIcdVer());
        m.setOtherDiagnosisCount(r.otherDiagnosisCount());
        m.setPathologicalDiagnosis(r.pathologicalDiagnosis());

        m.setMainOperationCode(r.mainOperationCode());
        m.setMainOperationName(r.mainOperationName());
        m.setOperationDate(r.operationDate());
        m.setOperator(r.operator());
        m.setAnesthesiaMethod(r.anesthesiaMethod());

        m.setTotalCost(r.totalCost());
        m.setDrugCost(r.drugCost());
        m.setOperationCost(r.operationCost());
        m.setMedicalServiceCost(r.medicalServiceCost());

        m.setSourceHospital(r.sourceHospital());
        m.setSourcePdfPath(r.sourcePdfPath());
        m.setExtractionConfidence(r.extractionConfidence());

        if (r.status() != null) {
            m.setStatus(r.status());
        }
    }
}
