package com.deepaudit.engine;

import com.deepaudit.persistence.entity.MedicalRecordMain;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 62-field whitelist for the rule DSL (V1 + V6 HQMS + V7 main-diag attrs + V8 outpatient extras).
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
        Map.entry("extractionConfidence",  MedicalRecordMain::getExtractionConfidence),

        // V6 — Demographics expansion (10)
        Map.entry("nationality",             MedicalRecordMain::getNationality),
        Map.entry("ethnicity",               MedicalRecordMain::getEthnicity),
        Map.entry("maritalStatus",           MedicalRecordMain::getMaritalStatus),
        Map.entry("occupation",              MedicalRecordMain::getOccupation),
        Map.entry("ageDays",                 MedicalRecordMain::getAgeDays),
        Map.entry("newbornBirthWeight",      MedicalRecordMain::getNewbornBirthWeight),
        Map.entry("newbornAdmissionWeight",  MedicalRecordMain::getNewbornAdmissionWeight),
        Map.entry("idCardType",              MedicalRecordMain::getIdCardType),
        Map.entry("birthPlace",              MedicalRecordMain::getBirthPlace),
        Map.entry("nativePlace",             MedicalRecordMain::getNativePlace),

        // V6 — Address & contacts (12)
        Map.entry("currentAddress",          MedicalRecordMain::getCurrentAddress),
        Map.entry("currentPhone",            MedicalRecordMain::getCurrentPhone),
        Map.entry("currentZip",              MedicalRecordMain::getCurrentZip),
        Map.entry("registeredAddress",       MedicalRecordMain::getRegisteredAddress),
        Map.entry("registeredZip",           MedicalRecordMain::getRegisteredZip),
        Map.entry("workplace",               MedicalRecordMain::getWorkplace),
        Map.entry("workPhone",               MedicalRecordMain::getWorkPhone),
        Map.entry("workZip",                 MedicalRecordMain::getWorkZip),
        Map.entry("contactName",             MedicalRecordMain::getContactName),
        Map.entry("contactRelation",         MedicalRecordMain::getContactRelation),
        Map.entry("contactAddress",          MedicalRecordMain::getContactAddress),
        Map.entry("contactPhone",            MedicalRecordMain::getContactPhone),

        // V6 — Ward / specialty (3)
        Map.entry("admissionWard",           MedicalRecordMain::getAdmissionWard),
        Map.entry("dischargeWard",           MedicalRecordMain::getDischargeWard),
        Map.entry("specialtyDept",           MedicalRecordMain::getSpecialtyDept),

        // V6 — Outpatient diagnosis (2)
        Map.entry("outpatientDiagnosis",     MedicalRecordMain::getOutpatientDiagnosis),
        Map.entry("outpatientDiagnosisCode", MedicalRecordMain::getOutpatientDiagnosisCode),

        // V7 — Main diagnosis attributes flat-mirrored from subtable (3)
        Map.entry("mainAdmissionCondition",  MedicalRecordMain::getMainAdmissionCondition),
        Map.entry("mainDischargeCondition",  MedicalRecordMain::getMainDischargeCondition),
        Map.entry("mainNote",                MedicalRecordMain::getMainNote),

        // V8 — Outpatient diagnosis extras (2)
        Map.entry("outpatientAdmissionCondition", MedicalRecordMain::getOutpatientAdmissionCondition),
        Map.entry("confirmedAfterAdmissionDate",  MedicalRecordMain::getConfirmedAfterAdmissionDate)
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
