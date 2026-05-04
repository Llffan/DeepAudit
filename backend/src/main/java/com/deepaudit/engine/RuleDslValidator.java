package com.deepaudit.engine;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Static structural validator for the QC rule DSL (plan §5.3 / §8.7).
 *
 * <p>Two callers, one purpose -- never let a malformed DSL reach the
 * evaluator:
 * <ul>
 *   <li>T2.3 rule CRUD: validate user-submitted {@code expression}
 *       before {@code INSERT qc_rule}; on failure return 400 with the
 *       full error list (R3 defense).
 *   <li>T2.5 NL→Rule: validate LLM-generated DSL as a second-pass
 *       defense; on failure either auto-retry the LLM or surface the
 *       errors (R10 defense -- LLM hallucinated op or field).
 * </ul>
 *
 * <p>Errors accumulate rather than short-circuit so the user sees
 * every problem in one round-trip.
 *
 * <p>The op + field whitelists here are the canonical DSL language
 * definition; {@link RuleEvaluator} must not encounter anything that
 * passed validation here. Adding a new op requires touching this
 * whitelist, the evaluator switch, and the test suite -- three
 * intentional sites for review.
 */
@Component
public class RuleDslValidator {

    /** Operators that yield a boolean (top-level / nested in args / when / assert / arg). */
    public static final Set<String> BOOLEAN_OPS = Set.of(
        "and", "or", "not",
        "notNull", "isNull",
        "eq", "ne", "gt", "gte", "lt", "lte",
        "dateBefore", "dateAfter"
    );

    /** Operators that yield a value (only valid in rhs / from / to slots). */
    public static final Set<String> VALUE_OPS = Set.of(
        "dateDiffDays"
    );

    public ValidationResult validate(JsonNode dsl) {
        List<String> errors = new ArrayList<>();
        if (dsl == null || dsl.isNull()) {
            errors.add("$: DSL is null");
            return new ValidationResult(false, List.copyOf(errors));
        }
        if (!dsl.isObject()) {
            errors.add("$: DSL root must be a JSON object");
            return new ValidationResult(false, List.copyOf(errors));
        }

        boolean isGuarded = dsl.has("when") && dsl.has("assert");
        boolean isDirect  = dsl.has("op");

        if (isGuarded) {
            // {when, assert} form -- both must be boolean expressions.
            // 'op' at the root in this form would be ambiguous; reject.
            if (isDirect) {
                errors.add("$: cannot mix 'op' with {when, assert} at the root");
            }
            validateBool(dsl.get("when"),   "$.when",   errors);
            validateBool(dsl.get("assert"), "$.assert", errors);
        } else if (isDirect) {
            validateBool(dsl, "$", errors);
        } else {
            errors.add("$: DSL root must be either {when, assert} or {op, ...}");
        }

        return new ValidationResult(errors.isEmpty(), List.copyOf(errors));
    }

    // ----- boolean expression validation -----------------------------------

    private void validateBool(JsonNode n, String path, List<String> errors) {
        if (n == null || n.isNull()) {
            errors.add(path + ": missing");
            return;
        }
        if (!n.isObject()) {
            errors.add(path + ": expected an object boolean expression");
            return;
        }
        JsonNode opNode = n.get("op");
        if (opNode == null || !opNode.isTextual()) {
            errors.add(path + ": missing or non-textual 'op'");
            return;
        }
        String op = opNode.asText();
        if (!BOOLEAN_OPS.contains(op)) {
            errors.add(path + ": unknown op '" + op + "' (allowed: " + BOOLEAN_OPS + ")");
            return;
        }

        switch (op) {
            case "and", "or" -> {
                JsonNode args = n.get("args");
                if (args == null || !args.isArray()) {
                    errors.add(path + ".args: required array of boolean expressions");
                    return;
                }
                if (args.isEmpty()) {
                    errors.add(path + ".args: must contain at least one expression");
                }
                int i = 0;
                for (JsonNode child : args) {
                    validateBool(child, path + ".args[" + i + "]", errors);
                    i++;
                }
            }
            case "not" -> {
                JsonNode arg = n.get("arg");
                if (arg == null) {
                    errors.add(path + ".arg: required boolean expression");
                } else {
                    validateBool(arg, path + ".arg", errors);
                }
            }
            case "notNull", "isNull" -> {
                requireWhitelistedField(n, "field", path, errors);
            }
            case "eq", "ne", "gt", "gte", "lt", "lte", "dateBefore", "dateAfter" -> {
                requireWhitelistedField(n, "field", path, errors);
                JsonNode rhs = n.get("rhs");
                if (rhs == null) {
                    errors.add(path + ".rhs: required value expression");
                } else {
                    validateValue(rhs, path + ".rhs", errors);
                }
            }
            default -> errors.add(path + ": op '" + op + "' is whitelisted but unhandled (validator bug)");
        }
    }

    // ----- value expression validation (rhs / from / to slots) -------------

    private void validateValue(JsonNode n, String path, List<String> errors) {
        if (n == null) {
            errors.add(path + ": missing");
            return;
        }
        // literals
        if (n.isNumber() || n.isTextual() || n.isBoolean() || n.isNull()) {
            return;
        }
        if (!n.isObject()) {
            errors.add(path + ": expected literal, field reference, or computed value");
            return;
        }
        // bare field reference: {"field": "..."}
        if (n.has("field") && !n.has("op")) {
            requireWhitelistedField(n, "field", path, errors);
            return;
        }
        // computed value: {"op": <VALUE_OPS>, ...}
        JsonNode opNode = n.get("op");
        if (opNode == null || !opNode.isTextual()) {
            errors.add(path + ": value expression must be a literal, "
                + "{field: \"...\"}, or {op: <value-op>, ...}");
            return;
        }
        String op = opNode.asText();
        if (!VALUE_OPS.contains(op)) {
            // Reject boolean ops in value slots (e.g., putting `and` in rhs).
            String hint = BOOLEAN_OPS.contains(op)
                ? " (boolean op '" + op + "' cannot appear in a value slot)"
                : " (allowed value ops: " + VALUE_OPS + ")";
            errors.add(path + ": unknown value-op '" + op + "'" + hint);
            return;
        }
        if ("dateDiffDays".equals(op)) {
            requireWhitelistedField(n, "from", path, errors);
            requireWhitelistedField(n, "to",   path, errors);
        }
    }

    private static void requireWhitelistedField(
            JsonNode n, String key, String path, List<String> errors) {
        JsonNode f = n.get(key);
        if (f == null || !f.isTextual()) {
            errors.add(path + "." + key + ": required string field name");
            return;
        }
        String name = f.asText();
        if (!FieldAccessor.isKnown(name)) {
            errors.add(path + "." + key + ": unknown field '" + name
                + "' (must be one of the 30 curated fields)");
        }
    }

    /**
     * Outcome of validation. {@code ok=true} means the DSL is safe to
     * persist and run through {@link RuleEvaluator}. {@code errors} is
     * never null and is empty iff {@code ok} is true.
     */
    public record ValidationResult(boolean ok, List<String> errors) {
        public ValidationResult {
            errors = errors == null ? List.of() : List.copyOf(errors);
        }
    }
}
