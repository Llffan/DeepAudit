package com.deepaudit.engine;

import com.deepaudit.persistence.entity.MedicalRecordMain;
import com.deepaudit.service.EmbeddingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Recursive interpreter for the QC rule DSL (plan §7.2). Pure function:
 * given a parsed DSL tree and one curated medical record, returns
 * {@code true} when the rule is satisfied (no violation) or
 * {@code false} when it fires (write a check_result).
 *
 * <p>Top-level shapes:
 * <ul>
 *   <li><b>Direct assertion</b> (R002): the root node is a boolean
 *       expression evaluated against the record.
 *   <li><b>Guarded assertion</b> (R001): the root has {@code when} +
 *       {@code assert}; semantically it is logical implication
 *       {@code !when || assert} -- when the guard is false the rule
 *       does not apply.
 * </ul>
 *
 * <p>Stateless and thread-safe. Errors thrown from here indicate the
 * DSL is malformed and should have been rejected by RuleDslValidator
 * (T2.2) before persistence.
 */
@Component
public class RuleEvaluator {

    private static final Logger log = LoggerFactory.getLogger(RuleEvaluator.class);

    /** Default cosine-similarity floor for icdNameSimilar when DSL omits it. */
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.6;

    private final CustomOperatorRegistry customRegistry;
    private final IcdDictCache icdDictCache;
    private final EmbeddingService embeddingService;

    public RuleEvaluator(CustomOperatorRegistry customRegistry,
                         IcdDictCache icdDictCache,
                         EmbeddingService embeddingService) {
        this.customRegistry = customRegistry;
        this.icdDictCache = icdDictCache;
        this.embeddingService = embeddingService;
    }

    public boolean evaluate(JsonNode dsl, MedicalRecordMain record) {
        if (dsl == null) {
            throw new IllegalArgumentException("DSL is null");
        }
        if (dsl.has("when") && dsl.has("assert")) {
            boolean guard = evalBool(dsl.get("when"), record);
            return !guard || evalBool(dsl.get("assert"), record);
        }
        return evalBool(dsl, record);
    }

    // ----- boolean-yielding nodes ------------------------------------------

    private boolean evalBool(JsonNode n, MedicalRecordMain rec) {
        if (n == null || !n.has("op") || !n.get("op").isTextual()) {
            throw new IllegalArgumentException("Boolean DSL node missing 'op': " + n);
        }
        String op = n.get("op").asText();
        return switch (op) {
            case "and" -> streamArgs(n, op).allMatch(c -> evalBool(c, rec));
            case "or"  -> streamArgs(n, op).anyMatch(c -> evalBool(c, rec));
            case "not" -> !evalBool(requireChild(n, "arg", op), rec);

            case "notNull" -> FieldAccessor.get(requireField(n, op), rec) != null;
            case "isNull"  -> FieldAccessor.get(requireField(n, op), rec) == null;

            case "eq" -> equalsCoerced(
                FieldAccessor.get(requireField(n, op), rec),
                evalValue(requireChild(n, "rhs", op), rec));
            case "ne" -> !equalsCoerced(
                FieldAccessor.get(requireField(n, op), rec),
                evalValue(requireChild(n, "rhs", op), rec));

            case "gt", "gte", "lt", "lte" -> compareOrdered(op,
                FieldAccessor.get(requireField(n, op), rec),
                evalValue(requireChild(n, "rhs", op), rec));

            case "dateBefore" -> dateOrdered(
                FieldAccessor.get(requireField(n, op), rec),
                evalValue(requireChild(n, "rhs", op), rec), -1);
            case "dateAfter" -> dateOrdered(
                FieldAccessor.get(requireField(n, op), rec),
                evalValue(requireChild(n, "rhs", op), rec), 1);

            case "custom" -> {
                String code = requireText(n, "code", "custom");
                JsonNode bodyDsl = customRegistry.getBodyDsl(code);
                if (bodyDsl == null) {
                    throw new IllegalArgumentException("Unknown custom operator: '" + code + "'");
                }
                JsonNode args = n.has("args") ? n.get("args") : JsonNodeFactory.instance.objectNode();
                yield evalBool(substituteRefs(bodyDsl, args), rec);
            }

            // R-Std-001 / R004: dictionary-backed code validity check. The
            // cache is loaded from icd_dict at startup and refreshed when the
            // admin reload endpoint runs. A null field value yields false here
            // (rule fires), but production rules normally guard with
            // {when: notNull} so completeness is left to dedicated rules.
            case "icdCodeExists" -> {
                String fieldName = requireField(n, "icdCodeExists");
                String category  = requireText(n, "category", "icdCodeExists");
                Object v = FieldAccessor.get(fieldName, rec);
                yield v != null && icdDictCache.contains(v.toString(), category);
            }

            // R-Cons-001 / R005: code-name consistency check.
            // Compares the record's name field with the dictionary's standard
            // name for the given code. Whitespace is collapsed on both sides
            // before comparison so accidental double spaces / leading-trailing
            // blanks don't cause false negatives.
            //
            // When the code is missing from the dictionary, this op yields
            // true ("not our problem") -- code legality is R004's job, this op
            // only fires when both sides are present and disagree. Same for
            // null code or null name: pair with {when: and(notNull, notNull)}
            // in production to make intent explicit.
            case "icdNameMatches" -> {
                String codeField = requireText(n, "codeField", "icdNameMatches");
                String nameField = requireText(n, "nameField", "icdNameMatches");
                String category  = requireText(n, "category",  "icdNameMatches");
                Object codeVal = FieldAccessor.get(codeField, rec);
                Object nameVal = FieldAccessor.get(nameField, rec);
                if (codeVal == null || nameVal == null) yield true;
                String dictName = icdDictCache.getName(codeVal.toString(), category);
                if (dictName == null) yield true; // delegate to icdCodeExists
                yield normalizeName(dictName).equals(normalizeName(nameVal.toString()));
            }

            // R-Cons-001 / R006: semantic name-vs-code consistency via cosine
            // similarity against icd_dict.name_embedding. Used as a softer
            // fallback when icdNameMatches' exact-string check is too strict
            // (synonyms, alias names, abbreviations the operator typed in).
            //
            // Abstain semantics — this op deliberately yields TRUE in every
            // ambiguous case so it never produces false positives on its own:
            //   * code or name field null         → delegate to R001 / completeness
            //   * dict row missing for that code  → delegate to icdCodeExists (R004)
            //   * dict row's vector is NULL       → embedding hasn't been backfilled yet
            //                                       (POST /admin/icd-dict/reembed),
            //                                       not a quality issue with the record
            //   * EmbeddingModel bean absent      → DASHSCOPE_API_KEY unset, log once
            //                                       and pass — operator config issue,
            //                                       not record content issue
            //   * online embedding call failed    → transient, conservative pass
            //
            // Threshold: optional "threshold" arg in [0, 1]. Default 0.6 — picked
            // so that synonym pairs ("阑尾切除"↔"其他阑尾切除术") clear it but
            // unrelated names ("阑尾切除"↔"剖宫产术") fall well below. Operators
            // can tune per-rule; lowering increases tolerance, raising tightens.
            case "icdNameSimilar" -> {
                String codeField = requireText(n, "codeField", "icdNameSimilar");
                String nameField = requireText(n, "nameField", "icdNameSimilar");
                String category  = requireText(n, "category",  "icdNameSimilar");
                double threshold = n.has("threshold") && n.get("threshold").isNumber()
                    ? n.get("threshold").asDouble()
                    : DEFAULT_SIMILARITY_THRESHOLD;
                if (threshold < 0.0 || threshold > 1.0) {
                    throw new IllegalArgumentException(
                        "icdNameSimilar.threshold must be in [0,1], got " + threshold);
                }
                Object codeVal = FieldAccessor.get(codeField, rec);
                Object nameVal = FieldAccessor.get(nameField, rec);
                if (codeVal == null || nameVal == null) yield true;
                float[] dictVec = icdDictCache.getEmbedding(codeVal.toString(), category);
                if (dictVec == null) {
                    log.debug("icdNameSimilar abstain: no dict vector for {} ({})",
                        codeVal, category);
                    yield true;
                }
                if (!embeddingService.isAvailable()) {
                    log.debug("icdNameSimilar abstain: EmbeddingModel bean absent");
                    yield true;
                }
                float[] queryVec = embeddingService.embed(nameVal.toString());
                if (queryVec == null) {
                    log.debug("icdNameSimilar abstain: online embed of '{}' failed/empty",
                        nameVal);
                    yield true;
                }
                double sim = cosineSimilarity(queryVec, dictVec);
                if (log.isDebugEnabled()) {
                    log.debug("icdNameSimilar code={} name='{}' sim={} threshold={}",
                        codeVal, nameVal, String.format("%.4f", sim), threshold);
                }
                yield sim >= threshold;
            }

            default -> throw new IllegalArgumentException("Unknown op: " + op);
        };
    }

    // ----- value-yielding nodes (RHS slots) --------------------------------

    private Object evalValue(JsonNode n, MedicalRecordMain rec) {
        if (n.isNumber())  return n.numberValue();
        if (n.isTextual()) return n.asText();
        if (n.isBoolean()) return n.booleanValue();
        if (n.isNull())    return null;

        if (n.isObject()) {
            // Bare field reference: {"field": "fooBar"}
            if (n.has("field") && !n.has("op")) {
                return FieldAccessor.get(n.get("field").asText(), rec);
            }
            // Computed values: dateDiffDays / ageYears
            String vop = n.has("op") ? n.get("op").asText() : "";
            if ("dateDiffDays".equals(vop)) {
                Object from = resolveField(n, "from", "dateDiffDays", rec);
                Object to   = resolveField(n, "to",   "dateDiffDays", rec);
                if (!(from instanceof LocalDate fl) || !(to instanceof LocalDate tl)) return null;
                return (int) ChronoUnit.DAYS.between(fl, tl);
            }
            if ("ageYears".equals(vop)) {
                Object bd = resolveField(n, "birthDate", "ageYears", rec);
                Object rd = resolveField(n, "refDate",   "ageYears", rec);
                if (!(bd instanceof LocalDate bdl) || !(rd instanceof LocalDate rdl)) return null;
                return (int) ChronoUnit.YEARS.between(bdl, rdl);
            }
        }
        throw new IllegalArgumentException("Cannot evaluate value expression: " + n);
    }

    // ----- comparison helpers ----------------------------------------------

    /**
     * Equality with cross-numeric coercion. Integer 7 equals BigDecimal
     * 7.0 equals Long 7L equals Double 7.0. Outside Number, falls back
     * to {@link Object#equals}. Two nulls are equal.
     */
    private static boolean equalsCoerced(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a instanceof Number na && b instanceof Number nb) {
            return new BigDecimal(na.toString()).compareTo(new BigDecimal(nb.toString())) == 0;
        }
        return a.equals(b);
    }

    /**
     * gt/gte/lt/lte over Number (BigDecimal-coerced) and LocalDate.
     * Null on either side returns false (conservative -- value missing is
     * the completeness rules' job, not the comparison rules').
     */
    private static boolean compareOrdered(String op, Object a, Object b) {
        if (a == null || b == null) return false;
        int cmp;
        if (a instanceof Number na && b instanceof Number nb) {
            cmp = new BigDecimal(na.toString()).compareTo(new BigDecimal(nb.toString()));
        } else if (a instanceof LocalDate la && b instanceof LocalDate lb) {
            cmp = la.compareTo(lb);
        } else {
            throw new IllegalArgumentException(
                "Op '" + op + "' requires Number or LocalDate operands, got "
                + a.getClass().getSimpleName() + " vs " + b.getClass().getSimpleName());
        }
        return switch (op) {
            case "gt"  -> cmp > 0;
            case "gte" -> cmp >= 0;
            case "lt"  -> cmp < 0;
            case "lte" -> cmp <= 0;
            default -> throw new IllegalArgumentException("Not an ordered op: " + op);
        };
    }

    /**
     * dateBefore / dateAfter on two LocalDate operands. Either side
     * non-LocalDate or null returns false.
     *
     * @param expectedSign -1 for before, +1 for after
     */
    private static boolean dateOrdered(Object a, Object b, int expectedSign) {
        if (a instanceof LocalDate la && b instanceof LocalDate lb) {
            int cmp = la.compareTo(lb);
            return expectedSign < 0 ? cmp < 0 : cmp > 0;
        }
        return false;
    }

    /**
     * Collapse runs of whitespace to a single space and trim. Used by
     * {@code icdNameMatches} so trivial formatting differences ("阑尾切除  术 "
     * vs "阑尾切除 术") don't produce false mismatches. Does NOT touch
     * full-width vs half-width punctuation, simplified vs traditional, or
     * synonyms — those are path-B (vector) territory.
     */
    private static String normalizeName(String s) {
        if (s == null) return "";
        return s.trim().replaceAll("\\s+", " ");
    }

    /**
     * Cosine similarity in {@code [-1, 1]} for two equal-length float
     * vectors. Throws on length mismatch — that indicates the embedding
     * dimension drifted between the dict-side build (DashScope batch via
     * IcdDictEmbeddingService) and the query-side call (online embed of
     * the record's name field) and the operator must reconcile config
     * before similarity numbers are meaningful.
     *
     * <p>For zero-length or zero-magnitude inputs returns 0.0 — treated
     * as "no signal" by the caller, which already yields {@code true}
     * (abstain) when the dict-side vector is missing.
     */
    private static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0) return 0.0;
        if (a.length != b.length) {
            throw new IllegalStateException(
                "Embedding dimension mismatch: query=" + a.length + " dict=" + b.length
                + " — re-run /admin/icd-dict/reembed?mode=all after a model/dim change");
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot   += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    // ----- DSL structure helpers (better errors than raw NPE) --------------

    private static Stream<JsonNode> streamArgs(JsonNode n, String op) {
        JsonNode args = n.get("args");
        if (args == null || !args.isArray()) {
            throw new IllegalArgumentException("Op '" + op + "' requires 'args' array: " + n);
        }
        return StreamSupport.stream(args.spliterator(), false);
    }

    private static JsonNode requireChild(JsonNode n, String key, String op) {
        JsonNode child = n.get(key);
        if (child == null) {
            throw new IllegalArgumentException("Op '" + op + "' missing '" + key + "' operand: " + n);
        }
        return child;
    }

    private static String requireField(JsonNode n, String op) {
        return requireText(n, "field", op);
    }

    private static String requireText(JsonNode n, String key, String op) {
        JsonNode child = n.get(key);
        if (child == null || !child.isTextual()) {
            throw new IllegalArgumentException(
                "Op '" + op + "' requires textual '" + key + "' operand: " + n);
        }
        return child.asText();
    }

    // ----- custom operator template expansion --------------------------------

    /**
     * Recursively replaces every {@code {"$ref":"paramName"}} node in the
     * template body with the corresponding string value from {@code args}.
     * All other nodes are returned as-is (structurally shared, not copied).
     */
    private static JsonNode substituteRefs(JsonNode node, JsonNode args) {
        if (node.isObject()) {
            if (node.size() == 1 && node.has("$ref")) {
                String param = node.get("$ref").asText();
                JsonNode val = args.get(param);
                if (val == null) {
                    throw new IllegalArgumentException(
                        "Custom op: missing argument '" + param + "'");
                }
                return val;
            }
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            node.fieldNames().forEachRemaining(k ->
                result.set(k, substituteRefs(node.get(k), args)));
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            node.forEach(item -> result.add(substituteRefs(item, args)));
            return result;
        }
        return node;
    }

    /**
     * Reads a field name from a DSL node key, then fetches the field value
     * from the record. The key's value may be either a plain string (field name)
     * or already a resolved TextNode after {@link #substituteRefs}.
     */
    private static Object resolveField(JsonNode n, String key, String op, MedicalRecordMain rec) {
        JsonNode child = n.get(key);
        if (child == null) {
            throw new IllegalArgumentException("Op '" + op + "' missing '" + key + "': " + n);
        }
        String fieldName = child.isTextual() ? child.asText()
            : child instanceof TextNode tn ? tn.asText()
            : child.asText();
        return FieldAccessor.get(fieldName, rec);
    }
}
