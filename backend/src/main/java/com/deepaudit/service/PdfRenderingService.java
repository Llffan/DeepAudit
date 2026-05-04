package com.deepaudit.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders the first N pages of an uploaded病案首页 PDF to PNG byte arrays so
 * they can be sent to Gemini's multimodal endpoint (T3.1 / plan §6.3).
 *
 * <p>Strategy: render at DPI 200 first; if any single page exceeds 2 MB
 * post-encoding (large color photos / scanned forms), drop to DPI 150 and
 * re-render that page only. Pages beyond {@link #MAX_PAGES} are silently
 * dropped — a病案首页 is normally 1–2 pages and Qwen-VL/Gemini token cost
 * grows linearly with images.
 */
@Service
public class PdfRenderingService {

    private static final Logger log = LoggerFactory.getLogger(PdfRenderingService.class);

    private static final int PRIMARY_DPI = 200;
    private static final int FALLBACK_DPI = 150;
    private static final int SOFT_BYTE_CAP = 2 * 1024 * 1024;   // 2 MB
    private static final int MAX_PAGES = 5;

    public List<byte[]> renderPagesAsPng(byte[] pdfBytes) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            int pageCount = Math.min(doc.getNumberOfPages(), MAX_PAGES);
            if (doc.getNumberOfPages() > MAX_PAGES) {
                log.warn("PDF has {} pages, only first {} will be sent to LLM",
                    doc.getNumberOfPages(), MAX_PAGES);
            }

            List<byte[]> images = new ArrayList<>(pageCount);
            for (int i = 0; i < pageCount; i++) {
                byte[] png = renderPage(renderer, i, PRIMARY_DPI);
                if (png.length > SOFT_BYTE_CAP) {
                    log.info("Page {} {} bytes > 2MB cap, re-rendering at DPI {}",
                        i, png.length, FALLBACK_DPI);
                    png = renderPage(renderer, i, FALLBACK_DPI);
                }
                images.add(png);
            }
            return images;
        }
    }

    private static byte[] renderPage(PDFRenderer renderer, int index, int dpi) throws IOException {
        BufferedImage img = renderer.renderImageWithDPI(index, dpi, ImageType.RGB);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(img, "PNG", out);
            return out.toByteArray();
        }
    }
}
