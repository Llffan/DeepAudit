package com.deepaudit.service;

import com.deepaudit.api.dto.MedicalRecordImportResponse;
import com.deepaudit.api.exception.ValidationException;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Orchestrates the {@code POST /api/medical-records/import} pipeline (T3.3):
 *
 * <pre>
 *   multipart -> validate -> save PDF to disk -> PDFBox text extract ->
 *   DeepSeek text-based extract -> shape response.
 * </pre>
 *
 * <p>Per plan §6.7 / §8.7, this stage NEVER persists to the medical-record
 * tables. The PDF lives on disk so the user can preview it via
 * {@link FileStorageService#resolve(String)}; the DB row only appears when
 * the operator hits "save draft" or "确认提交" — that path goes through
 * {@link MedicalRecordSaveService}.
 *
 * <p>Failure modes:
 * <ul>
 *   <li>415 — non-PDF uploads (extension + magic-bytes mismatch)
 *   <li>400 — empty file
 *   <li>413 — handled upstream by Spring's {@code MaxUploadSizeExceededException}
 *   <li>500 — disk write failure (rare)
 *   <li>200 + {@code degraded=true} — extraction itself failed; operator
 *       drops into manual-fill mode rather than getting an error toast
 * </ul>
 */
@Service
public class MedicalRecordImportService {

    private static final Logger log = LoggerFactory.getLogger(MedicalRecordImportService.class);

    private static final long MAX_BYTES = 10L * 1024 * 1024;            // 10 MB hard cap
    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};

    private final FileStorageService storage;
    private final MedicalRecordExtractor extractor;

    public MedicalRecordImportService(FileStorageService storage,
                                      MedicalRecordExtractor extractor) {
        this.storage = storage;
        this.extractor = extractor;
    }

    public MedicalRecordImportResponse handle(MultipartFile file) {
        validate(file);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ValidationException(List.of("file: 读取上传内容失败 — " + e.getMessage()));
        }
        validateMagicBytes(bytes);
        validateSize(bytes.length);

        FileStorageService.StoredFile stored;
        try {
            stored = storage.saveAsPdf(bytes);
            log.info("Saved upload {} ({} bytes)", stored.relativePath(), stored.size());
        } catch (IOException e) {
            log.error("Failed to write upload to disk", e);
            throw new RuntimeException("无法保存上传文件：" + e.getMessage(), e);
        }

        // From here on, any failure should NOT be a hard error — the PDF
        // has been persisted and the user can still edit fields manually.
        String pdfText;
        try (PDDocument doc = PDDocument.load(bytes)) {
            pdfText = new PDFTextStripper().getText(doc);
        } catch (IOException e) {
            log.warn("PDF text extraction failed for {}: {}", stored.relativePath(), e.toString());
            return degraded(stored.relativePath(), "PDF 文字提取失败：" + e.getMessage());
        }

        ExtractionResult er = extractor.extractFromText(pdfText);
        if (er.degraded()) {
            log.warn("Extractor degraded for {}: {}", stored.relativePath(), er.degradedReason());
            return new MedicalRecordImportResponse(
                er.main(),
                er.extra(),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                stored.relativePath(),
                true,
                er.degradedReason()
            );
        }

        BigDecimal confidence = BigDecimal.valueOf(er.confidence())
            .setScale(2, RoundingMode.HALF_UP);
        return new MedicalRecordImportResponse(
            er.main(),
            er.extra(),
            confidence,
            stored.relativePath(),
            false,
            null
        );
    }

    private static void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException(List.of("file: 上传文件不能为空"));
        }
        String name = file.getOriginalFilename();
        String contentType = file.getContentType();
        boolean nameOk = name != null && name.toLowerCase().endsWith(".pdf");
        boolean typeOk = contentType == null
            || contentType.equalsIgnoreCase("application/pdf")
            || contentType.equalsIgnoreCase("application/x-pdf")
            || contentType.equalsIgnoreCase("application/octet-stream");
        if (!nameOk || !typeOk) {
            throw new UnsupportedFileTypeException("仅支持 PDF 文件（.pdf）");
        }
    }

    private static void validateMagicBytes(byte[] bytes) {
        if (bytes.length < PDF_MAGIC.length) {
            throw new UnsupportedFileTypeException("文件过小，不是合法 PDF");
        }
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            if (bytes[i] != PDF_MAGIC[i]) {
                throw new UnsupportedFileTypeException("文件头不是 %PDF-，不是合法 PDF");
            }
        }
    }

    private static void validateSize(int size) {
        if (size > MAX_BYTES) {
            // The 12 MB Spring/nginx limit usually catches this first; this
            // check covers the off-by-2-MB band and surfaces a friendly 413.
            throw new PayloadTooLargeException(
                "文件超过 10 MB 上限（实际 "
                    + (size / 1024 / 1024) + " MB）");
        }
    }

    private static MedicalRecordImportResponse degraded(String pdfPath, String reason) {
        return new MedicalRecordImportResponse(
            JsonNodeFactory.instance.objectNode(),
            JsonNodeFactory.instance.objectNode(),
            BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
            pdfPath,
            true,
            reason
        );
    }

    /** 415 mapped in {@link com.deepaudit.api.exception.ApiExceptionHandler}. */
    public static class UnsupportedFileTypeException extends RuntimeException {
        public UnsupportedFileTypeException(String message) { super(message); }
    }

    /** 413 mapped in {@link com.deepaudit.api.exception.ApiExceptionHandler}. */
    public static class PayloadTooLargeException extends RuntimeException {
        public PayloadTooLargeException(String message) { super(message); }
    }
}
