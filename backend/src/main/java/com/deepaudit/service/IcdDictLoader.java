package com.deepaudit.service;

import com.deepaudit.engine.IcdDictCache;
import com.deepaudit.persistence.repository.IcdDictRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Loads ICD-9-CM-3 / ICD-10 dictionary entries from JSONL resources into
 * the {@code icd_dict} table.
 *
 * <p>Source: {@code classpath:data/icd_dict/*.jsonl}. The Maven build
 * mirrors {@code docs/icd_dict/*.jsonl} into this classpath path so the
 * JSONL is single-sourced (see backend/pom.xml &lt;resources&gt; block).
 *
 * <p>Two entry points:
 * <ul>
 *   <li>Auto-load on startup (ApplicationRunner) -- only if the table is
 *       empty AND {@code deepaudit.icd.auto-load=true} (default true).
 *       Skips silently when already populated so multi-instance / restart
 *       boots are cheap.
 *   <li>{@link #loadAll()} -- callable from the admin controller for
 *       forced reload after editing the JSONL files.
 * </ul>
 *
 * <p>Insert path is {@code ON CONFLICT DO UPDATE name} per
 * {@link IcdDictRepository#upsert}, which makes both calls idempotent.
 * {@code name_embedding} is intentionally left untouched here -- vector
 * generation is an offline task with separate cost / failure semantics.
 */
@Service
public class IcdDictLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IcdDictLoader.class);

    private static final List<String> JSONL_FILES = List.of(
        "icd9cm3_common.jsonl",
        "icd10_common.jsonl"
    );

    private final IcdDictRepository repository;
    private final IcdDictCache cache;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper mapper;

    @Value("${deepaudit.icd.auto-load:true}")
    private boolean autoLoad;

    @Value("${deepaudit.icd.resource-dir:classpath:data/icd_dict}")
    private String resourceDir;

    public IcdDictLoader(IcdDictRepository repository,
                         IcdDictCache cache,
                         ResourceLoader resourceLoader,
                         ObjectMapper mapper) {
        this.repository = repository;
        this.cache = cache;
        this.resourceLoader = resourceLoader;
        this.mapper = mapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!autoLoad) {
            log.info("ICD dict auto-load disabled (deepaudit.icd.auto-load=false)");
            return;
        }
        long existing = repository.count();
        if (existing > 0) {
            log.info("icd_dict has {} rows, skipping auto-load (use POST /admin/icd-dict/reload to force)", existing);
            return;
        }
        try {
            int n = loadAll();
            log.info("ICD dict auto-load complete: {} rows from {} file(s)", n, JSONL_FILES.size());
            // IcdDictCache.@PostConstruct ran BEFORE this ApplicationRunner and
            // saw an empty table -- refresh it now so RuleEvaluator's
            // icdCodeExists op picks up the freshly imported rows on the very
            // first check after boot.
            cache.reload();
        } catch (Exception e) {
            // Don't crash the app over a dictionary preload — degrade gracefully,
            // the rule engine itself doesn't depend on icd_dict being populated.
            log.error("ICD dict auto-load failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Force-load every JSONL file. Idempotent (UPSERT). Returns the total
     * number of rows successfully inserted-or-updated.
     *
     * <p>Intentionally NOT {@code @Transactional}: each
     * {@link IcdDictRepository#upsert} call runs in its own short
     * transaction (Spring Data JPA default), so a single bad row only
     * skips itself rather than poisoning the whole batch. For ~230 rows
     * the per-statement overhead is trivial.
     */
    public int loadAll() throws IOException {
        int total = 0;
        for (String fileName : JSONL_FILES) {
            total += loadOne(fileName);
        }
        return total;
    }

    private int loadOne(String fileName) throws IOException {
        String path = resourceDir + "/" + fileName;
        Resource res = resourceLoader.getResource(path);
        if (!res.exists()) {
            log.warn("ICD dict resource missing: {}", path);
            return 0;
        }
        int loaded = 0;
        int skipped = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("//")) continue;
                try {
                    JsonNode n = mapper.readTree(trimmed);
                    String code = textOrEmpty(n, "code");
                    String name = textOrEmpty(n, "name");
                    String category = textOrEmpty(n, "category");
                    String version = textOrEmpty(n, "version");
                    if (code.isEmpty() || name.isEmpty() || category.isEmpty() || version.isEmpty()) {
                        log.warn("{}:{} missing required field(s), skipped", fileName, lineNo);
                        skipped++;
                        continue;
                    }
                    // embedding_text is optional in the JSONL — fall back to
                    // "code name" so the column is never NULL (V11 makes it
                    // NOT NULL). Aliases-rich source files yield much better
                    // recall, so the warning helps spot drift.
                    String embeddingText = textOrEmpty(n, "embedding_text");
                    if (embeddingText.isEmpty()) {
                        embeddingText = code + " " + name;
                        log.debug("{}:{} no embedding_text in JSONL, falling back to '{}'",
                            fileName, lineNo, embeddingText);
                    }
                    repository.upsert(code, name, category, version, embeddingText);
                    loaded++;
                } catch (Exception parseErr) {
                    log.warn("{}:{} parse failure: {}", fileName, lineNo, parseErr.getMessage());
                    skipped++;
                }
            }
        }
        log.info("Loaded {} ({} rows, {} skipped)", fileName, loaded, skipped);
        return loaded;
    }

    private static String textOrEmpty(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return (v == null || v.isNull()) ? "" : v.asText().trim();
    }
}
