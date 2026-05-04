package com.deepaudit.engine;

import com.deepaudit.persistence.entity.MedicalRecordMain;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 30-field whitelist for the rule DSL (plan §4.4 curated fields).
 *
 * <p>Acts as both:
 * <ul>
 *   <li>The runtime accessor for {@link RuleEvaluator} -- field name string
 *       to typed getter on {@link MedicalRecordMain}.
 *   <li>The static whitelist consulted by RuleDslValidator (T2.2) so
 *       rule authors / LLM-generated DSL cannot reference fields outside
 *       this set.
 * </ul>
 *
 * <p>Method references (not reflection) on purpose: they are an order of
 * magnitude faster, work without {@code --add-opens} JVM flags, and act
 * as a compile-time check that every whitelisted field still exists on
 * the entity.
 *
 * <p>The {@code status} workflow column is intentionally NOT in the
 * whitelist -- it is workflow state, not business data, and should not
 * appear in QC rules.
 */
public final class FieldAccessor {

    private FieldAccessor() {}

    private static final Map<String, Function<MedicalRecordMain, Object>> ACCESSORS = Map.ofEntries(
        // Basic identity (6)
        Map.entry("recordNo",              MedicalRecordMain::getRecordNo),
        Map.entry("name",                  MedicalRecordMain::getName),
        Map.entry("gender",                MedicalRecordMain::getGender),
        Map.entry("birthDate",             MedicalRecordMain::getBirthDate),
        Map.entry("age",                   MedicalRecordMain::getAge),
        Map.entry("idCardMasked",          MedicalRecordMain::getIdCardMasked),

        // Admission / discharge (7)
        Map.entry("admissionDate",         MedicalRecordMain::getAdmissionDate),
        Map.entry("dischargeDate",         MedicalRecordMain::getDischargeDate),
        Map.entry("lengthOfStay",          MedicalRecordMain::getLengthOfStay),
        Map.entry("admissionDept",         MedicalRecordMain::getAdmissionDept),
        Map.entry("dischargeDept",         MedicalRecordMain::getDischargeDept),
        Map.entry("admissionRoute",        MedicalRecordMain::getAdmissionRoute),
        Map.entry("dischargeStatus",       MedicalRecordMain::getDischargeStatus),

        // Diagnoses (5)
        Map.entry("mainDiagnosisCode",     MedicalRecordMain::getMainDiagnosisCode),
        Map.entry("mainDiagnosisName",     MedicalRecordMain::getMainDiagnosisName),
        Map.entry("mainDiagnosisIcdVer",   MedicalRecordMain::getMainDiagnosisIcdVer),
        Map.entry("otherDiagnosisCount",   MedicalRecordMain::getOtherDiagnosisCount),
        Map.entry("pathologicalDiagnosis", MedicalRecordMain::getPathologicalDiagnosis),

        // Operations (5)
        Map.entry("mainOperationCode",     MedicalRecordMain::getMainOperationCode),
        Map.entry("mainOperationName",     MedicalRecordMain::getMainOperationName),
        Map.entry("operationDate",         MedicalRecordMain::getOperationDate),
        Map.entry("operator",              MedicalRecordMain::getOperator),
        Map.entry("anesthesiaMethod",      MedicalRecordMain::getAnesthesiaMethod),

        // Cost categories (4)
        Map.entry("totalCost",             MedicalRecordMain::getTotalCost),
        Map.entry("drugCost",              MedicalRecordMain::getDrugCost),
        Map.entry("operationCost",         MedicalRecordMain::getOperationCost),
        Map.entry("medicalServiceCost",    MedicalRecordMain::getMedicalServiceCost),

        // Source / extraction metadata (3)
        Map.entry("sourceHospital",        MedicalRecordMain::getSourceHospital),
        Map.entry("sourcePdfPath",         MedicalRecordMain::getSourcePdfPath),
        Map.entry("extractionConfidence",  MedicalRecordMain::getExtractionConfidence)
    );

    /**
     * Read {@code fieldName} from {@code record}.
     *
     * @throws IllegalArgumentException if {@code fieldName} is not in the
     *         whitelist. RuleDslValidator should reject such DSL before
     *         it ever reaches the evaluator; reaching this means the rule
     *         is malformed.
     */
    public static Object get(String fieldName, MedicalRecordMain record) {
        Function<MedicalRecordMain, Object> fn = ACCESSORS.get(fieldName);
        if (fn == null) {
            throw new IllegalArgumentException("Unknown field: " + fieldName);
        }
        return fn.apply(record);
    }

    public static boolean isKnown(String fieldName) {
        return ACCESSORS.containsKey(fieldName);
    }

    public static Set<String> knownFields() {
        return ACCESSORS.keySet();
    }
}
