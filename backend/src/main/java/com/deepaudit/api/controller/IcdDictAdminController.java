package com.deepaudit.api.controller;

import com.deepaudit.api.dto.ReembedResult;
import com.deepaudit.engine.IcdDictCache;
import com.deepaudit.persistence.repository.IcdDictRepository;
import com.deepaudit.service.IcdDictEmbeddingService;
import com.deepaudit.service.IcdDictLoader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Admin endpoints for the ICD dictionary table. Note: context-path is
 * {@code /api}, so the effective routes are {@code /api/admin/icd-dict/*}.
 *
 * <ul>
 *   <li>{@code GET  /admin/icd-dict/stats}   -- row counts by category, plus
 *                                              embedding coverage.
 *   <li>{@code POST /admin/icd-dict/reload}  -- force re-run of the JSONL
 *                                              loader; idempotent (UPSERT).
 *                                              Use after editing
 *                                              {@code docs/icd_dict/*.jsonl}.
 *   <li>{@code POST /admin/icd-dict/reembed} -- backfill name_embedding via
 *                                              DashScope. {@code mode=missing}
 *                                              (default) processes only NULL
 *                                              rows; {@code mode=all} clears
 *                                              every vector first then runs
 *                                              missing — use after switching
 *                                              embedding model / dimension.
 * </ul>
 *
 * <p>No auth gating — this MVP has no user model. Bind these behind a
 * reverse-proxy ACL in production, or add Spring Security later.
 */
@RestController
@RequestMapping("/admin/icd-dict")
public class IcdDictAdminController {

    private final IcdDictLoader loader;
    private final IcdDictEmbeddingService embeddingService;
    private final IcdDictRepository repository;
    private final IcdDictCache cache;

    public IcdDictAdminController(IcdDictLoader loader,
                                  IcdDictEmbeddingService embeddingService,
                                  IcdDictRepository repository,
                                  IcdDictCache cache) {
        this.loader = loader;
        this.embeddingService = embeddingService;
        this.repository = repository;
        this.cache = cache;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        long total = repository.count();
        long pending = repository.countByNameEmbeddingIsNull();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("icd9cm3", repository.countByCategory("icd9cm3"));
        out.put("icd10",   repository.countByCategory("icd10"));
        out.put("total",   total);
        // Embedding coverage tells the operator at a glance whether
        // R-Cons-001 vector recall will work for arbitrary records.
        out.put("embeddingPending",  pending);
        out.put("embeddingCoverage", total == 0 ? 0L : (total - pending));
        return out;
    }

    @PostMapping("/reload")
    public Map<String, Object> reload() throws IOException {
        int affected = loader.loadAll();
        // Refresh the in-memory (code,category) index used by RuleEvaluator's
        // icdCodeExists op so newly imported codes are immediately effective.
        cache.reload();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("upserted",       affected);
        out.put("totalAfterLoad", repository.count());
        out.put("icd9cm3",        repository.countByCategory("icd9cm3"));
        out.put("icd10",          repository.countByCategory("icd10"));
        // Surface the post-reload embedding gap so the operator knows
        // whether to follow up with /reembed.
        out.put("embeddingPending", repository.countByNameEmbeddingIsNull());
        return out;
    }

    /**
     * Backfill name_embedding for icd_dict rows.
     *
     * @param mode {@code missing} (default, idempotent) or {@code all}
     *             (wipes vectors first, full re-embed). Anything else
     *             returns 400.
     */
    @PostMapping("/reembed")
    public ReembedResult reembed(@RequestParam(defaultValue = "missing") String mode) {
        return switch (mode) {
            case "missing" -> embeddingService.reembedMissing();
            case "all"     -> embeddingService.reembedAll();
            default -> throw new IllegalArgumentException(
                "mode 必须是 'missing' 或 'all'，收到: " + mode);
        };
    }
}
