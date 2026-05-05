package com.deepaudit.engine;

import com.deepaudit.persistence.entity.IcdDict;
import com.deepaudit.persistence.repository.IcdDictRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * In-memory index of {@code icd_dict} keyed on {@code (code, category)},
 * used by {@link RuleEvaluator} for ICD-related boolean ops.
 *
 * <p>Stored as a {@code Map<key, name>} so two ops can share one index:
 * <ul>
 *   <li>{@link #contains} — backs {@code icdCodeExists} (membership check).
 *   <li>{@link #getName}  — backs {@code icdNameMatches} (code → standard name lookup).
 * </ul>
 *
 * <p>Why a cache and not a direct repository call from the evaluator:
 * <ul>
 *   <li>{@link RuleEvaluator} is invoked once per (record × rule). Hitting
 *       JDBC inside the inner loop would dominate latency for batch checks.
 *   <li>The dictionary is read-mostly (loaded by {@link com.deepaudit.service.IcdDictLoader}).
 *       A full scan into a {@code HashMap} keyed on {@code code + "|" + category}
 *       gives O(1) lookups.
 *   <li>Mirrors the {@link CustomOperatorRegistry} pattern (volatile reference
 *       swap on reload) so reads are lock-free and reloads are atomic.
 * </ul>
 *
 * <p>Reload triggers (call {@link #reload()}):
 * <ul>
 *   <li>Startup ({@link PostConstruct}).
 *   <li>{@code POST /admin/icd-dict/reload} after the JSONL importer runs.
 * </ul>
 */
@Component
public class IcdDictCache {

    private static final Logger log = LoggerFactory.getLogger(IcdDictCache.class);

    private final IcdDictRepository repository;

    /** volatile so readers always see a fully-built map after reload. */
    private volatile Map<String, String> codeToName = Map.of();

    public IcdDictCache(IcdDictRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void reload() {
        Map<String, String> fresh = new HashMap<>();
        for (IcdDict d : repository.findAll()) {
            fresh.put(makeKey(d.getCode(), d.getCategory()), d.getName());
        }
        this.codeToName = Map.copyOf(fresh);
        log.info("IcdDictCache reloaded: {} entries", fresh.size());
    }

    /**
     * @return true iff {@code (code, category)} is present in the dictionary.
     *   Returns false when {@code code} is null/blank.
     */
    public boolean contains(String code, String category) {
        if (code == null || code.isBlank() || category == null) return false;
        return codeToName.containsKey(makeKey(code, category));
    }

    /**
     * @return the standard name for {@code (code, category)}, or {@code null}
     *   when the entry is not in the dictionary or any input is blank.
     */
    public String getName(String code, String category) {
        if (code == null || code.isBlank() || category == null) return null;
        return codeToName.get(makeKey(code, category));
    }

    private static String makeKey(String code, String category) {
        return code + "|" + category;
    }
}
