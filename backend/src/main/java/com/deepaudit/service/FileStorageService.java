package com.deepaudit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Local-disk storage for uploaded病案首页 PDFs (T3.3 / plan §6.3).
 *
 * <p>Files are written under {@code deepaudit.upload-dir} (default
 * {@code ./uploads}, resolved relative to the JVM working directory).
 * The returned {@link StoredFile#relativePath} is a stable token like
 * {@code uploads/{uuid}.pdf} that gets persisted on
 * {@code medical_record_main.source_pdf_path}; later flows (字段确认页
 * embed preview / 解释页 trace) resolve it back to a real path via
 * {@link #resolve(String)}.
 *
 * <p>This class deliberately stays thin so future swaps to S3 / MinIO
 * only need a new implementation behind the same surface.
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    public record StoredFile(String relativePath, Path absolutePath, long size) {}

    private final Path uploadRoot;

    public FileStorageService(@Value("${deepaudit.upload-dir:./uploads}") String uploadDir) throws IOException {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(this.uploadRoot);
        log.info("FileStorageService root = {}", this.uploadRoot);
    }

    public StoredFile saveAsPdf(byte[] bytes) throws IOException {
        String uuid = UUID.randomUUID().toString();
        String filename = uuid + ".pdf";
        Path absolute = uploadRoot.resolve(filename);
        Files.write(absolute, bytes);
        // The relative form ("uploads/<uuid>.pdf") is what we persist; it
        // tolerates the upload-dir being moved between dev and prod.
        return new StoredFile("uploads/" + filename, absolute, bytes.length);
    }

    public Path resolve(String relativePath) {
        if (relativePath == null) return null;
        // Strip any "uploads/" prefix; treat the remainder as a filename only
        // (block path-traversal — relativePath came from the DB, but defense
        // in depth never hurts).
        String name = relativePath;
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        return uploadRoot.resolve(name);
    }

    public void deleteQuietly(Path absolute) {
        if (absolute == null) return;
        try {
            Files.deleteIfExists(absolute);
        } catch (IOException e) {
            log.warn("Failed to delete {}: {}", absolute, e.toString());
        }
    }
}
