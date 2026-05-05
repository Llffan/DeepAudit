package com.deepaudit.engine;

import com.deepaudit.persistence.entity.MedicalRecordMain;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan §10 T2.1 mandates "约 200 行，含单元测试覆盖 R1/R2". This suite
 * locks in the seed-rule semantics plus the corner cases that broke
 * the design discussion: cross-numeric eq, null-on-comparison, guard
 * semantics, malformed DSL.
 */
class RuleEvaluatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    // null repositories are safe: CustomOperatorRegistry.cache and
    // IcdDictCache.keys both default to empty collections; this suite does
    // not exercise the 'custom' or 'icdCodeExists' ops, so the registries
    // are never asked to call back into the (null) repository.
    private final RuleEvaluator evaluator = new RuleEvaluator(
        new CustomOperatorRegistry(null),
        new IcdDictCache(null));

    /** R001 -- 主手术编码非空时，手术医生与手术日期不能为空（V1 seed）. */
    private static final String R001 = """
        {
          "when":   { "op": "notNull", "field": "mainOperationCode" },
          "assert": {
            "op": "and",
            "args": [
              { "op": "notNull", "field": "operator" },
              { "op": "notNull", "field": "operationDate" }
            ]
          }
        }
        """;

    /** R002 -- 入院日期早于出院日期 且 住院天数 = 出院日期 - 入院日期（V1 seed）. */
    private static final String R002 = """
        {
          "op": "and",
          "args": [
            { "op": "dateBefore", "field": "admissionDate",
              "rhs": { "field": "dischargeDate" } },
            { "op": "eq", "field": "lengthOfStay",
              "rhs": { "op": "dateDiffDays",
                       "from": "admissionDate", "to": "dischargeDate" } }
          ]
        }
        """;

    private boolean eval(String json, MedicalRecordMain r) throws Exception {
        JsonNode dsl = MAPPER.readTree(json);
        return evaluator.evaluate(dsl, r);
    }

    // ---------- R001 (guarded, completeness) ----------

    @Test
    @DisplayName("R001 passes when guard is false (no main operation present)")
    void r001_passes_when_guard_false() throws Exception {
        MedicalRecordMain r = new MedicalRecordMain();
        // mainOperationCode null -> guard false -> rule does not apply
        assertTrue(eval(R001, r));
    }

    @Test
    @DisplayName("R001 passes when guard true and both required fields present")
    void r001_passes_when_guard_true_and_assert_satisfied() throws Exception {
        MedicalRecordMain r = new MedicalRecordMain();
        r.setMainOperationCode("0392");
        r.setOperator("张三");
        r.setOperationDate(LocalDate.of(2026, 4, 1));
        assertTrue(eval(R001, r));
    }

    @Test
    @DisplayName("R001 hits when guard true and operator missing")
    void r001_hits_when_operator_missing() throws Exception {
        MedicalRecordMain r = new MedicalRecordMain();
        r.setMainOperationCode("0392");
        r.setOperator(null);
        r.setOperationDate(LocalDate.of(2026, 4, 1));
        assertFalse(eval(R001, r));
    }

    @Test
    @DisplayName("R001 hits when guard true and operationDate missing")
    void r001_hits_when_operation_date_missing() throws Exception {
        MedicalRecordMain r = new MedicalRecordMain();
        r.setMainOperationCode("0392");
        r.setOperator("张三");
        r.setOperationDate(null);
        assertFalse(eval(R001, r));
    }

    // ---------- R002 (direct, logic) ----------

    @Test
    @DisplayName("R002 passes when dates ordered and length matches")
    void r002_passes_on_valid_record() throws Exception {
        MedicalRecordMain r = new MedicalRecordMain();
        r.setAdmissionDate(LocalDate.of(2026, 4, 1));
        r.setDischargeDate(LocalDate.of(2026, 4, 8));
        r.setLengthOfStay(7);
        assertTrue(eval(R002, r));
    }

    @Test
    @DisplayName("R002 hits when length_of_stay disagrees with day diff")
    void r002_hits_on_length_mismatch() throws Exception {
        MedicalRecordMain r = new MedicalRecordMain();
        r.setAdmissionDate(LocalDate.of(2026, 4, 1));
        r.setDischargeDate(LocalDate.of(2026, 4, 8));
        r.setLengthOfStay(10);  // wrong: should be 7
        assertFalse(eval(R002, r));
    }

    @Test
    @DisplayName("R002 hits when discharge precedes admission")
    void r002_hits_on_inverted_dates() throws Exception {
        MedicalRecordMain r = new MedicalRecordMain();
        r.setAdmissionDate(LocalDate.of(2026, 4, 8));
        r.setDischargeDate(LocalDate.of(2026, 4, 1));
        r.setLengthOfStay(-7);  // even matching diff fails because dateBefore false
        assertFalse(eval(R002, r));
    }

    // ---------- operator coverage ----------

    @Test
    @DisplayName("eq compares numbers across Integer / BigDecimal cleanly")
    void eq_cross_numeric_types() throws Exception {
        MedicalRecordMain r = new MedicalRecordMain();
        r.setAge(30);
        assertTrue(eval("{\"op\":\"eq\",\"field\":\"age\",\"rhs\":30}", r));
        assertFalse(eval("{\"op\":\"eq\",\"field\":\"age\",\"rhs\":31}", r));

        r.setTotalCost(new BigDecimal("999.50"));
        assertTrue(eval("{\"op\":\"eq\",\"field\":\"totalCost\",\"rhs\":999.50}", r));
    }

    @Test
    @DisplayName("gt works on BigDecimal field vs JSON int rhs")
    void gt_decimal_field_vs_int_rhs() throws Exception {
        MedicalRecordMain r = new MedicalRecordMain();
        r.setTotalCost(new BigDecimal("999.50"));
        assertTrue(eval("{\"op\":\"gt\",\"field\":\"totalCost\",\"rhs\":500}", r));
        assertFalse(eval("{\"op\":\"gt\",\"field\":\"totalCost\",\"rhs\":1000}", r));
    }

    @Test
    @DisplayName("gt with null field returns false (does not throw)")
    void gt_with_null_field_is_false() throws Exception {
        MedicalRecordMain r = new MedicalRecordMain();
        r.setAge(null);
        assertFalse(eval("{\"op\":\"gt\",\"field\":\"age\",\"rhs\":120}", r));
    }

    @Test
    @DisplayName("not inverts inner expression")
    void not_inverts() throws Exception {
        MedicalRecordMain r = new MedicalRecordMain();
        // operator is null -> notNull(operator) is false -> not(notNull) is true
        assertTrue(eval("{\"op\":\"not\",\"arg\":{\"op\":\"notNull\",\"field\":\"operator\"}}", r));
    }

    @Test
    @DisplayName("or short-circuits to true on first satisfied branch")
    void or_short_circuit() throws Exception {
        MedicalRecordMain r = new MedicalRecordMain();
        r.setAge(45);
        String dsl = """
            {"op":"or","args":[
              {"op":"eq","field":"age","rhs":45},
              {"op":"eq","field":"age","rhs":99}
            ]}
            """;
        assertTrue(eval(dsl, r));
    }

    // ---------- malformed DSL ----------

    @Test
    @DisplayName("Unknown op throws IllegalArgumentException")
    void unknown_op_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            eval("{\"op\":\"foo\",\"args\":[]}", new MedicalRecordMain()));
    }

    @Test
    @DisplayName("Unknown field throws IllegalArgumentException")
    void unknown_field_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            eval("{\"op\":\"notNull\",\"field\":\"definitelyNotAField\"}", new MedicalRecordMain()));
    }

    @Test
    @DisplayName("Missing 'op' on a boolean node is reported")
    void missing_op_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            eval("{\"field\":\"age\"}", new MedicalRecordMain()));
    }

    @Test
    @DisplayName("Missing 'field' operand on notNull is reported")
    void missing_field_operand_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            eval("{\"op\":\"notNull\"}", new MedicalRecordMain()));
    }
}
