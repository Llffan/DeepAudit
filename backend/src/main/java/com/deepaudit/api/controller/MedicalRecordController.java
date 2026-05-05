package com.deepaudit.api.controller;

import com.deepaudit.api.dto.CheckSummaryResponse;
import com.deepaudit.api.dto.DiagnosisDto;
import com.deepaudit.api.dto.MedicalRecordImportResponse;
import com.deepaudit.api.dto.MedicalRecordListItemDto;
import com.deepaudit.api.dto.MedicalRecordMainDto;
import com.deepaudit.api.dto.MedicalRecordSaveRequest;
import com.deepaudit.api.dto.PageResponse;
import com.deepaudit.api.exception.NotFoundException;
import com.deepaudit.persistence.entity.MedicalRecordDiagnosis;
import com.deepaudit.persistence.entity.MedicalRecordMain;
import com.deepaudit.persistence.repository.MedicalRecordDiagnosisRepository;
import com.deepaudit.persistence.repository.MedicalRecordMainRepository;
import com.deepaudit.service.CheckService;
import com.deepaudit.service.ExtractionResult;
import com.deepaudit.service.FileStorageService;
import com.deepaudit.service.MedicalRecordImportService;
import com.deepaudit.service.MedicalRecordMockFiller;
import com.deepaudit.service.MedicalRecordSaveService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Phase-3 medical-record API surface (T3.3 / plan §6.6).
 *
 * <pre>
 *   POST   /api/medical-records/import   multipart  -> render + extract, no DB
 *   POST   /api/medical-records          JSON       -> insert (or update if id)
 *   POST   /api/medical-records/{id}/check          -> run rule engine, persist hits (T4.1)
 *   GET    /api/medical-records          query      -> paged list
 *   GET    /api/medical-records/{id}                -> read one record
 *   GET    /api/medical-records/{id}/pdf            -> stream the source PDF for embed preview
 * </pre>
 */
@RestController
@RequestMapping("/medical-records")
public class MedicalRecordController {

    private final MedicalRecordImportService importService;
    private final MedicalRecordSaveService saveService;
    private final MedicalRecordMockFiller mockFiller;
    private final MedicalRecordMainRepository mainRepo;
    private final MedicalRecordDiagnosisRepository diagRepo;
    private final FileStorageService storage;
    private final CheckService checkService;

    public MedicalRecordController(MedicalRecordImportService importService,
                                   MedicalRecordSaveService saveService,
                                   MedicalRecordMockFiller mockFiller,
                                   MedicalRecordMainRepository mainRepo,
                                   MedicalRecordDiagnosisRepository diagRepo,
                                   FileStorageService storage,
                                   CheckService checkService) {
        this.importService = importService;
        this.saveService = saveService;
        this.mockFiller = mockFiller;
        this.mainRepo = mainRepo;
        this.diagRepo = diagRepo;
        this.storage = storage;
        this.checkService = checkService;
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MedicalRecordImportResponse importPdf(@RequestParam("file") MultipartFile file) {
        return importService.handle(file);
    }

    /**
     * Dev/demo helper: ask Gemini to fabricate a coherent fake病案首页 so
     * the operator can fill the form with one click instead of typing 30
     * fields by hand. Returns the same shape as {@code /import} so the
     * frontend reuses the same merge code path.
     */
    @PostMapping("/mock-fill")
    public MedicalRecordImportResponse mockFill() {
        ExtractionResult er = mockFiller.generate();
        BigDecimal confidence = BigDecimal.valueOf(er.confidence())
            .setScale(2, RoundingMode.HALF_UP);
        return new MedicalRecordImportResponse(
            er.main(),
            er.extra(),
            confidence,
            null,            // no source PDF — this is synthetic data
            false,
            null
        );
    }

    @PostMapping
    public ResponseEntity<MedicalRecordMainDto> save(@RequestBody MedicalRecordSaveRequest req) {
        MedicalRecordMain saved = saveService.save(req);
        MedicalRecordMainDto body = toDto(saved);
        if (req.id() == null) {
            return ResponseEntity
                .created(URI.create("/api/medical-records/" + saved.getId()))
                .body(body);
        }
        return ResponseEntity.ok(body);
    }

    @GetMapping
    public PageResponse<MedicalRecordListItemDto> list(
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String s = (status == null || status.isBlank()) ? null : status.trim();
        String k = (keyword == null || keyword.isBlank()) ? null : keyword.trim();

        Page<MedicalRecordMain> rows = mainRepo.search(
            s, k,
            PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "id"))
        );
        return PageResponse.of(rows.map(MedicalRecordController::toListItem));
    }

    @PostMapping("/{id}/check")
    public CheckSummaryResponse runCheck(@PathVariable Long id) {
        return checkService.check(id);
    }

    @GetMapping("/{id}")
    public MedicalRecordMainDto get(@PathVariable Long id) {
        return mainRepo.findById(id)
            .map(this::toDto)
            .orElseThrow(() -> new NotFoundException("病案不存在：id=" + id));
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<Resource> getPdf(@PathVariable Long id) {
        MedicalRecordMain m = mainRepo.findById(id).orElseThrow(() ->
            new NotFoundException("病案不存在：id=" + id));
        if (m.getSourcePdfPath() == null || m.getSourcePdfPath().isBlank()) {
            throw new NotFoundException("该病案没有原始 PDF（可能为手填录入）");
        }
        Path absolute = storage.resolve(m.getSourcePdfPath());
        if (absolute == null || !Files.isRegularFile(absolute)) {
            throw new NotFoundException("PDF 文件已丢失：" + m.getSourcePdfPath());
        }
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + absolute.getFileName() + "\"")
            .body(new FileSystemResource(absolute));
    }

    private static MedicalRecordListItemDto toListItem(MedicalRecordMain m) {
        boolean hasPdf = m.getSourcePdfPath() != null && !m.getSourcePdfPath().isBlank();
        return new MedicalRecordListItemDto(
            m.getId(),
            m.getRecordNo(),
            m.getName(),
            m.getGender(),
            m.getAge(),
            m.getAdmissionDate(),
            m.getDischargeDate(),
            m.getLengthOfStay(),
            m.getMainDiagnosisName(),
            m.getStatus(),
            m.getExtractionConfidence(),
            hasPdf,
            m.getCreatedAt(),
            m.getUpdatedAt()
        );
    }

    private MedicalRecordMainDto toDto(MedicalRecordMain m) {
        var diagnoses = diagRepo.findByRecordIdOrderByDiagTypeAscSeqNoAsc(m.getId())
            .stream()
            .map(MedicalRecordController::toDiagnosisDto)
            .toList();
        return new MedicalRecordMainDto(
            m.getId(),
            m.getRecordNo(),

            // Identity (6)
            m.getName(),
            m.getGender(),
            m.getBirthDate(),
            m.getAge(),
            m.getIdCardMasked(),

            // V6 demographics (10)
            m.getNationality(),
            m.getEthnicity(),
            m.getMaritalStatus(),
            m.getOccupation(),
            m.getAgeDays(),
            m.getNewbornBirthWeight(),
            m.getNewbornAdmissionWeight(),
            m.getIdCardType(),
            m.getBirthPlace(),
            m.getNativePlace(),

            // V6 contacts (12)
            m.getCurrentAddress(),
            m.getCurrentPhone(),
            m.getCurrentZip(),
            m.getRegisteredAddress(),
            m.getRegisteredZip(),
            m.getWorkplace(),
            m.getWorkPhone(),
            m.getWorkZip(),
            m.getContactName(),
            m.getContactRelation(),
            m.getContactAddress(),
            m.getContactPhone(),

            // Admission / discharge (7)
            m.getAdmissionDate(),
            m.getDischargeDate(),
            m.getLengthOfStay(),
            m.getAdmissionDept(),
            m.getDischargeDept(),
            m.getAdmissionRoute(),
            m.getDischargeStatus(),

            // V6 ward / specialty (3)
            m.getAdmissionWard(),
            m.getDischargeWard(),
            m.getSpecialtyDept(),

            // V6 outpatient diagnosis (2) + V8 extras (2)
            m.getOutpatientDiagnosis(),
            m.getOutpatientDiagnosisCode(),
            m.getOutpatientAdmissionCondition(),
            m.getConfirmedAfterAdmissionDate(),

            // Diagnoses — main flattened (8)
            m.getMainDiagnosisCode(),
            m.getMainDiagnosisName(),
            m.getMainDiagnosisIcdVer(),
            m.getMainAdmissionCondition(),
            m.getMainDischargeCondition(),
            m.getMainNote(),
            m.getOtherDiagnosisCount(),
            m.getPathologicalDiagnosis(),

            // V9 supplementary — 损伤、中毒
            m.getInjuryPoisoningCause(),
            m.getInjuryPoisoningCode(),

            // V9 supplementary — 病理扩展
            m.getPathologicalDiagnosisCode(),
            m.getPathologyNumber(),

            // V9 supplementary — 过敏 / 尸检 / 血型
            m.getDrugAllergy(),
            m.getAllergyDrugs(),
            m.getAutopsy(),
            m.getBloodType(),
            m.getRhBloodType(),

            // V9 supplementary — 医生
            m.getDepartmentDirector(),
            m.getChiefPhysician(),
            m.getAttendingPhysician(),
            m.getResidentPhysician(),
            m.getResponsibleNurse(),
            m.getTraineePhysician(),
            m.getInternPhysician(),
            m.getCoder(),

            // V9 supplementary — 质控
            m.getRecordQuality(),
            m.getQcPhysician(),
            m.getQcNurse(),
            m.getQcDate(),

            // Source / extraction (3)
            m.getSourceHospital(),
            m.getSourcePdfPath(),
            m.getExtractionConfidence(),

            // V7 — full diagnoses subtable (含主诊在内)
            diagnoses,

            m.getStatus(),
            m.getCreatedAt(),
            m.getUpdatedAt()
        );
    }

    private static DiagnosisDto toDiagnosisDto(MedicalRecordDiagnosis d) {
        return new DiagnosisDto(
            d.getDiagType(),
            d.getSeqNo(),
            d.getDiagnosisName(),
            d.getDiagnosisCode(),
            d.getIcdVersion(),
            d.getAdmissionCondition(),
            d.getDischargeCondition(),
            d.getNote()
        );
    }
}
