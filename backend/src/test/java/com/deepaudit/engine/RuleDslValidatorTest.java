package com.deepaudit.engine;

import com.deepaudit.engine.RuleDslValidator.ValidationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validator (T2.2) is the gate between user/LLM-authored DSL and the
 * evaluator (T2.1). These tests pin down two invariants:
 *
 * <ol>
 *   <li>Both seed rules (R001 + R002 from V1__init_schema.sql) must
 *       pass cleanly -- if they don't, the DB is shipping rules that
 *       can't survive their own validator.
 *   <li>Every malformed shape produces a path-prefixed error so the
 *       UI can highlight the offending sub-tree.
 * </ol>
 */
class RuleDslValidatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final RuleDslValidator validator = new RuleDslValidator();

    private ValidationResult validate(String json) throws Exception {
        JsonNode dsl = MAPPER.readTree(json);
        return validator.validate(dsl);
    }

    // ---------- happy path: seed rules round-trip cleanly ----------

    @Test
    @DisplayName("R001 (guarded notNull on V9 pathology fields) validates without error")
    void r001_valid() throws Exception {
        // V9 等价示例：原 R001 引用的手术字段在 V9 已下线
        String dsl = """
            { "when":   { "op": "notNull", "field": "pathologicalDiagnosis" },
              "assert": { "op": "and", "args": [
                  { "op": "notNull", "field": "pathologicalDiagnosisCode" },
                  { "op": "notNull", "field": "pathologyNumber" } ] } }
            """;
        ValidationResult r = validate(dsl);
        assertTrue(r.ok(), () -> "Expected ok, got errors: " + r.errors());
    }

    @Test
    @DisplayName("R002 (and + dateBefore + eq + dateDiffDays) validates without error")
    void r002_valid() throws Exception {
        String dsl = """
            { "op": "and", "args": [
                { "op": "dateBefore", "field": "admissionDate",
                  "rhs": { "field": "dischargeDate" } },
                { "op": "eq", "field": "lengthOfStay",
                  "rhs": { "op": "dateDiffDays",
                           "from": "admissionDate",
                           "to":   "dischargeDate" } } ] }
            """;
        ValidationResult r = validate(dsl);
        assertTrue(r.ok(), () -> "Expected ok, got errors: " + r.errors());
    }

    @Test
    @DisplayName("Literal rhs (number) validates")
    void literal_number_rhs_valid() throws Exception {
        ValidationResult r = validate("{\"op\":\"gt\",\"field\":\"age\",\"rhs\":120}");
        assertTrue(r.ok(), () -> r.errors().toString());
    }

    @Test
    @DisplayName("Literal rhs (string) validates")
    void literal_string_rhs_valid() throws Exception {
        ValidationResult r = validate("{\"op\":\"eq\",\"field\":\"gender\",\"rhs\":\"M\"}");
        assertTrue(r.ok(), () -> r.errors().toString());
    }

    // ---------- structural errors at the root ----------

    @Test
    @DisplayName("Root with neither {when,assert} nor 'op' is rejected")
    void root_missing_shape() throws Exception {
        ValidationResult r = validate("{\"foo\":\"bar\"}");
        assertFalse(r.ok());
        assertTrue(r.errors().getFirst().contains("must be either {when, assert} or {op"));
    }

    @Test
    @DisplayName("Root mixing 'op' with {when, assert} is rejected")
    void root_ambiguous_shape() throws Exception {
        String dsl = """
            { "op": "and", "args": [],
              "when": { "op": "notNull", "field": "age" },
              "assert": { "op": "notNull", "field": "name" } }
            """;
        ValidationResult r = validate(dsl);
        assertFalse(r.ok());
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("cannot mix 'op' with {when, assert}")));
    }

    // ---------- whitelist enforcement ----------

    @Test
    @DisplayName("Unknown boolean op is rejected with allowed-set hint")
    void unknown_op_rejected() throws Exception {
        ValidationResult r = validate("{\"op\":\"foo\",\"args\":[]}");
        assertFalse(r.ok());
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("unknown op 'foo'")));
    }

    @Test
    @DisplayName("Unknown field in notNull is rejected")
    void unknown_field_in_notNull_rejected() throws Exception {
        ValidationResult r = validate("{\"op\":\"notNull\",\"field\":\"definitelyNotAField\"}");
        assertFalse(r.ok());
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("unknown field 'definitelyNotAField'")));
    }

    @Test
    @DisplayName("Unknown field in bare-field rhs is rejected")
    void unknown_field_in_rhs_rejected() throws Exception {
        String dsl = """
            { "op": "eq", "field": "age", "rhs": { "field": "ghost" } }
            """;
        ValidationResult r = validate(dsl);
        assertFalse(r.ok());
        assertTrue(r.errors().stream().anyMatch(e ->
            e.contains("$.rhs.field: unknown field 'ghost'")));
    }

    @Test
    @DisplayName("Unknown 'from' field in dateDiffDays is rejected")
    void unknown_field_in_dateDiffDays_rejected() throws Exception {
        String dsl = """
            { "op": "eq", "field": "lengthOfStay",
              "rhs": { "op": "dateDiffDays",
                       "from": "phantomStart", "to": "dischargeDate" } }
            """;
        ValidationResult r = validate(dsl);
        assertFalse(r.ok());
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("phantomStart")));
    }

    @Test
    @DisplayName("Boolean op appearing in rhs slot is rejected with hint")
    void boolean_op_in_value_slot_rejected() throws Exception {
        String dsl = """
            { "op": "eq", "field": "age", "rhs": { "op": "and", "args": [] } }
            """;
        ValidationResult r = validate(dsl);
        assertFalse(r.ok());
        assertTrue(r.errors().stream().anyMatch(e ->
            e.contains("boolean op 'and' cannot appear in a value slot")));
    }

    // ---------- per-op operand requirements ----------

    @Test
    @DisplayName("and with empty args is rejected")
    void and_empty_args_rejected() throws Exception {
        ValidationResult r = validate("{\"op\":\"and\",\"args\":[]}");
        assertFalse(r.ok());
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("at least one expression")));
    }

    @Test
    @DisplayName("and missing 'args' is rejected")
    void and_missing_args_rejected() throws Exception {
        ValidationResult r = validate("{\"op\":\"and\"}");
        assertFalse(r.ok());
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("required array")));
    }

    @Test
    @DisplayName("not without 'arg' is rejected")
    void not_missing_arg_rejected() throws Exception {
        ValidationResult r = validate("{\"op\":\"not\"}");
        assertFalse(r.ok());
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("$.arg: required")));
    }

    @Test
    @DisplayName("eq missing 'rhs' is rejected")
    void eq_missing_rhs_rejected() throws Exception {
        ValidationResult r = validate("{\"op\":\"eq\",\"field\":\"age\"}");
        assertFalse(r.ok());
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("$.rhs: required")));
    }

    @Test
    @DisplayName("notNull missing 'field' is rejected")
    void notNull_missing_field_rejected() throws Exception {
        ValidationResult r = validate("{\"op\":\"notNull\"}");
        assertFalse(r.ok());
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("$.field: required")));
    }

    // ---------- error accumulation (don't short-circuit) ----------

    @Test
    @DisplayName("Multiple errors are collected, not short-circuited at first")
    void errors_accumulate() throws Exception {
        String dsl = """
            { "op": "and", "args": [
                { "op": "notNull", "field": "ghost1" },
                { "op": "eq", "field": "ghost2", "rhs": { "field": "ghost3" } } ] }
            """;
        ValidationResult r = validate(dsl);
        assertFalse(r.ok());
        assertEquals(3, r.errors().size(), () -> "Expected 3 unknown-field errors, got: " + r.errors());
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("ghost1")));
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("ghost2")));
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("ghost3")));
    }

    // ---------- null / non-object input ----------

    @Test
    @DisplayName("Null DSL is rejected, not NPE")
    void null_dsl_rejected() {
        ValidationResult r = validator.validate(null);
        assertFalse(r.ok());
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("DSL is null")));
    }

    @Test
    @DisplayName("Non-object root (array) is rejected")
    void non_object_root_rejected() throws Exception {
        ValidationResult r = validate("[]");
        assertFalse(r.ok());
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("must be a JSON object")));
    }
}
