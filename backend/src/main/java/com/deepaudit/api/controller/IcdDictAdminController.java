package com.deepaudit.api.controller;

import com.deepaudit.engine.IcdDictCache;
import com.deepaudit.persistence.repository.IcdDictRepository;
import com.deepaudit.service.IcdDictLoader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Admin endpoints for the ICD dictionary table. Note: context-path is
 * {@code /api}, so the effective routes are {@code /api/admin/icd-dict/*}.
 *
 * <ul>
 *   <li>{@code GET  /admin/icd-dict/stats}  -- row counts by category
 *   <li>{@code POST /admin/icd-dict/reload} -- force re-run of the JSONL
 *       loader; idempotent (UPSERT). Use after editing
 *       {@code docs/icd_dict/*.jsonl}.
 * </ul>
 *
 * <p>No auth gating — this MVP has no user model. Bind these behind a
 * reverse-proxy ACL in production, or add Spring Security later.
 */
@RestController
@RequestMapping("/admin/icd-dict")
public class IcdDictAdminController {

    private final IcdDictLoader loader;
    private final IcdDictRepository repository;
    private final IcdDictCache cache;

    public IcdDictAdminController(IcdDictLoader loader,
                                  IcdDictRepository repository,
                                  IcdDictCache cache) {
        this.loader = loader;
        this.repository = repository;
        this.cache = cache;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("icd9cm3", repository.countByCategory("icd9cm3"));
        out.put("icd10",   repository.countByCategory("icd10"));
        out.put("total",   repository.count());
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
        return out;
    }
}
