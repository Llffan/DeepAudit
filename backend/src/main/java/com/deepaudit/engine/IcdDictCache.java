package com.deepaudit.engine;

import com.deepaudit.api.dto.IcdMatch;
import com.deepaudit.persistence.entity.IcdDict;
import com.deepaudit.persistence.repository.IcdDictRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
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

    /**
     * Vector index parallel to {@link #codeToName}. Same key, value is the
     * row's {@code name_embedding} or {@code null} if not yet embedded.
     * Backs the {@code icdNameSimilar} op (R-Cons-001). Kept on the same
     * volatile-swap reload pattern so the two maps are always observed
     * from the same snapshot — no risk of seeing a name without its vector
     * or vice versa.
     */
    private volatile Map<String, float[]> codeToEmbedding = Map.of();

    public IcdDictCache(IcdDictRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void reload() {
        Map<String, String> freshNames = new HashMap<>();
        Map<String, float[]> freshVecs = new HashMap<>();
        for (IcdDict d : repository.findAll()) {
            String key = makeKey(d.getCode(), d.getCategory());
            freshNames.put(key, d.getName());
            float[] v = d.getNameEmbedding();
            if (v != null) freshVecs.put(key, v);
        }
        this.codeToName = Map.copyOf(freshNames);
        this.codeToEmbedding = Map.copyOf(freshVecs);
        log.info("IcdDictCache reloaded: {} names, {} embeddings",
            freshNames.size(), freshVecs.size());
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

    /**
     * @return the dictionary row's {@code name_embedding} for
     *   {@code (code, category)}, or {@code null} when the row is missing
     *   from the dictionary OR its vector hasn't been backfilled yet
     *   (POST /admin/icd-dict/reembed). The {@code icdNameSimilar} op
     *   treats both null cases as "abstain — pass" so missing vectors
     *   don't generate false positives.
     */
    public float[] getEmbedding(String code, String category) {
        if (code == null || code.isBlank() || category == null) return null;
        return codeToEmbedding.get(makeKey(code, category));
    }

    /**
     * Brute-force in-memory cosine ranking over every dict row whose vector
     * was loaded at the last reload. Used by {@code IcdLookupTool.searchByName}
     * to back the chat-style "what code is this?" question.
     *
     * <p>O(N·D) where N = embedded rows in {@code category}, D = vector
     * dimension. For ~300 rows × 1024 dims that's ~300k float multiplies,
     * sub-millisecond on commodity hardware. Even at full-spec ICD-10
     * (~30 000 rows) it stays under 30 ms — still cheaper than a single
     * round-trip to pgvector for the same query, and avoids the bind-side
     * complexity of CASTing a {@code float[]} into a {@code vector}
     * parameter via JPA. Switch to pgvector ANN only if the dict grows
     * past ~100k rows or this method shows up in profiling.
     *
     * @param queryVec  the query embedding; must match the dict-side
     *                  dimension or {@code IllegalStateException} is raised
     *                  ({@link RuleEvaluator#cosineSimilarity})
     * @param category  {@code icd9cm3} or {@code icd10}
     * @param topK      maximum results to return; non-positive yields empty
     * @return Top-K rows by descending similarity. Empty when no rows in
     *         {@code category} have a vector populated yet (call
     *         {@code POST /admin/icd-dict/reembed} first).
     */
    public List<IcdMatch> topKByCosine(float[] queryVec, String category, int topK) {
        if (queryVec == null || queryVec.length == 0 || category == null || topK <= 0) {
            return List.of();
        }
        // Snapshot reads — both maps are volatile so we either see a fully
        // built pair or the previous one; never half-rebuilt state.
        Map<String, String> namesSnapshot = this.codeToName;
        Map<String, float[]> vecsSnapshot = this.codeToEmbedding;

        List<IcdMatch> ranked = new ArrayList<>();
        String suffix = "|" + category;
        for (Map.Entry<String, float[]> e : vecsSnapshot.entrySet()) {
            String key = e.getKey();
            if (!key.endsWith(suffix)) continue;
            String code = key.substring(0, key.length() - suffix.length());
            String name = namesSnapshot.get(key);
            if (name == null) continue; // shouldn't happen; defensive
            double sim = cosine(queryVec, e.getValue());
            ranked.add(new IcdMatch(code, name, category, sim));
        }
        ranked.sort(Comparator.comparingDouble(IcdMatch::similarity).reversed());
        if (ranked.size() > topK) {
            return ranked.subList(0, topK);
        }
        return ranked;
    }

    /**
     * One-line summary for the chat assistant's {@code stats} tool —
     * total rows per category and how many have a backfilled vector.
     */
    public String statsSummary() {
        Map<String, String> namesSnapshot = this.codeToName;
        Map<String, float[]> vecsSnapshot = this.codeToEmbedding;
        long icd9Total = namesSnapshot.keySet().stream().filter(k -> k.endsWith("|icd9cm3")).count();
        long icd10Total = namesSnapshot.keySet().stream().filter(k -> k.endsWith("|icd10")).count();
        long icd9Embed  = vecsSnapshot.keySet().stream().filter(k -> k.endsWith("|icd9cm3")).count();
        long icd10Embed = vecsSnapshot.keySet().stream().filter(k -> k.endsWith("|icd10")).count();
        return String.format(
            "icd9cm3: %d 条 / 已向量化 %d 条; icd10: %d 条 / 已向量化 %d 条",
            icd9Total, icd9Embed, icd10Total, icd10Embed);
    }

    /**
     * Cosine similarity for the in-memory ranker. Duplicated from
     * RuleEvaluator (kept private there) rather than introducing a shared
     * util class for one method — if a third caller needs it, lift to
     * a {@code MathUtil} then.
     */
    private static double cosine(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalStateException(
                "Embedding dimension mismatch: query=" + a.length + " dict=" + b.length
                + " — re-run /admin/icd-dict/reembed?mode=all");
        }
        double dot = 0.0, na = 0.0, nb = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na  += (double) a[i] * a[i];
            nb  += (double) b[i] * b[i];
        }
        if (na == 0.0 || nb == 0.0) return 0.0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private static String makeKey(String code, String category) {
        return code + "|" + category;
    }
}
