package com.deepaudit.api.controller;

import com.deepaudit.api.dto.MedicalRecordImportResponse;
import com.deepaudit.api.dto.MedicalRecordMainDto;
import com.deepaudit.api.dto.MedicalRecordSaveRequest;
import com.deepaudit.api.exception.NotFoundException;
import com.deepaudit.persistence.entity.MedicalRecordMain;
import com.deepaudit.persistence.repository.MedicalRecordMainRepository;
import com.deepaudit.service.ExtractionResult;
import com.deepaudit.service.FileStorageService;
import com.deepaudit.service.MedicalRecordImportService;
import com.deepaudit.service.MedicalRecordMockFiller;
import com.deepaudit.service.MedicalRecordSaveService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
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
    private final FileStorageService storage;

    public MedicalRecordController(MedicalRecordImportService importService,
                                   MedicalRecordSaveService saveService,
                                   MedicalRecordMockFiller mockFiller,
                                   MedicalRecordMainRepository mainRepo,
                                   FileStorageService storage) {
        this.importService = importService;
        this.saveService = saveService;
        this.mockFiller = mockFiller;
        this.mainRepo = mainRepo;
        this.storage = storage;
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

    @GetMapping("/{id}")
    public MedicalRecordMainDto get(@PathVariable Long id) {
        return mainRepo.findById(id)
            .map(MedicalRecordController::toDto)
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

    private static MedicalRecordMainDto toDto(MedicalRecordMain m) {
        return new MedicalRecordMainDto(
            m.getId(),
            m.getRecordNo(),
            m.getName(),
            m.getGender(),
            m.getBirthDate(),
            m.getAge(),
            m.getIdCardMasked(),
            m.getAdmissionDate(),
            m.getDischargeDate(),
            m.getLengthOfStay(),
            m.getAdmissionDept(),
            m.getDischargeDept(),
            m.getAdmissionRoute(),
            m.getDischargeStatus(),
            m.getMainDiagnosisCode(),
            m.getMainDiagnosisName(),
            m.getMainDiagnosisIcdVer(),
            m.getOtherDiagnosisCount(),
            m.getPathologicalDiagnosis(),
            m.getMainOperationCode(),
            m.getMainOperationName(),
            m.getOperationDate(),
            m.getOperator(),
            m.getAnesthesiaMethod(),
            m.getTotalCost(),
            m.getDrugCost(),
            m.getOperationCost(),
            m.getMedicalServiceCost(),
            m.getSourceHospital(),
            m.getSourcePdfPath(),
            m.getExtractionConfidence(),
            m.getStatus(),
            m.getCreatedAt(),
            m.getUpdatedAt()
        );
    }
}
