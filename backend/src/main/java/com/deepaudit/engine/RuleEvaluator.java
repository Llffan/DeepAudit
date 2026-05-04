package com.deepaudit.engine;

import com.deepaudit.persistence.entity.MedicalRecordMain;
import com.fasterxml.jackson.databind.JsonNode;
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
            // Computed value: dateDiffDays(from, to) -> int days
            if (n.has("op") && "dateDiffDays".equals(n.get("op").asText())) {
                Object from = FieldAccessor.get(requireText(n, "from", "dateDiffDays"), rec);
                Object to   = FieldAccessor.get(requireText(n, "to",   "dateDiffDays"), rec);
                if (!(from instanceof LocalDate fl) || !(to instanceof LocalDate tl)) {
                    return null;
                }
                return (int) ChronoUnit.DAYS.between(fl, tl);
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
}
