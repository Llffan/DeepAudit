package com.deepaudit.service;

import com.deepaudit.api.dto.CheckResultItemDto;
import com.deepaudit.api.dto.CheckSummaryResponse;
import com.deepaudit.api.dto.CheckSummaryResponse.SummaryStats;
import com.deepaudit.api.dto.SkippedRuleDto;
import com.deepaudit.api.exception.NotFoundException;
import com.deepaudit.engine.FieldAccessor;
import com.deepaudit.engine.RuleEvaluator;
import com.deepaudit.persistence.entity.CheckResult;
import com.deepaudit.persistence.entity.MedicalRecordMain;
import com.deepaudit.persistence.entity.QcRule;
import com.deepaudit.persistence.repository.CheckResultRepository;
import com.deepaudit.persistence.repository.MedicalRecordMainRepository;
import com.deepaudit.persistence.repository.QcRuleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * T4.1 + T4.2 orchestrator. Wires the pure {@link RuleEvaluator} into
 * the storage layer:
 *
 * <ol>
 *   <li>{@link #check(long)} — runs every enabled rule against one
 *       record, persists hits, flips status to {@code checked}.
 *   <li>{@link #latestResults(long)} — replays the last persisted state
 *       without re-running anything (refresh-friendly, plan §7.6).
 * </ol>
 *
 * <p>Recheck strategy is "wipe and rewrite": the previous {@code check_result}
 * rows are deleted before new ones are written, so {@code latestResults}
 * always returns "the most recent run". Trade-off: a user's
 * {@code false_positive} marks (T4.4) get lost on recheck — accepted for
 * MVP, revisit when T4.4 lands.
 *
 * <p>Fault-tolerance: a single rule whose DSL throws is skipped, logged,
 * and reported back via {@code skippedRules[]} so one broken rule cannot
 * sink the whole check.
 */
@Service
public class CheckService {

    private static final Logger log = LoggerFactory.getLogger(CheckService.class);

    /** Matches Mustache-style {{fieldName}} placeholders. Identifier-only — no nested paths. */
    private static final Pattern TEMPLATE_VAR = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    private static final String MISSING_VALUE_LABEL = "（未填写）";

    private final MedicalRecordMainRepository mainRepo;
    private final QcRuleRepository ruleRepo;
    private final CheckResultRepository checkRepo;
    private final RuleEvaluator evaluator;

    public CheckService(MedicalRecordMainRepository mainRepo,
                        QcRuleRepository ruleRepo,
                        CheckResultRepository checkRepo,
                        RuleEvaluator evaluator) {
        this.mainRepo = mainRepo;
        this.ruleRepo = ruleRepo;
        this.checkRepo = checkRepo;
        this.evaluator = evaluator;
    }

    // -------------------------------------------------------------------------
    // T4.1  POST /api/medical-records/{id}/check
    // -------------------------------------------------------------------------
    @Transactional
    public CheckSummaryResponse check(long recordId) {
        MedicalRecordMain record = mainRepo.findById(recordId)
            .orElseThrow(() -> new NotFoundException("病案不存在：id=" + recordId));

        List<QcRule> rules = ruleRepo.findAllByEnabledTrue();

        // Wipe the previous run before evaluating — keeps "latest run" semantics
        // simple and avoids the (record_id, rule_id) UPSERT machinery.
        checkRepo.deleteByRecordId(recordId);

        List<CheckResult> hits = new ArrayList<>();
        List<SkippedRuleDto> skipped = new ArrayList<>();

        for (QcRule rule : rules) {
            boolean passed;
            try {
                passed = evaluator.evaluate(rule.getExpression(), record);
            } catch (RuntimeException ex) {
                log.warn("跳过规则 {} ({}): {}", rule.getCode(), rule.getName(), ex.getMessage());
                skipped.add(new SkippedRuleDto(rule.getCode(), ex.getMessage()));
                continue;
            }
            if (!passed) {
                hits.add(buildHit(record, rule));
            }
        }

        if (!hits.isEmpty()) {
            checkRepo.saveAll(hits);
        }

        record.setStatus("checked");
        mainRepo.save(record);

        // Re-read so the response carries DB-assigned ids and createdAt timestamps.
        List<CheckResult> persisted = checkRepo.findAllByRecordIdOrderByCreatedAtDesc(recordId);
        return buildResponse(record, rules.size(), persisted, skipped);
    }

    // -------------------------------------------------------------------------
    // T4.2  GET /api/check-results/{recordId}
    // -------------------------------------------------------------------------
    @Transactional(readOnly = true)
    public CheckSummaryResponse latestResults(long recordId) {
        MedicalRecordMain record = mainRepo.findById(recordId)
            .orElseThrow(() -> new NotFoundException("病案不存在：id=" + recordId));

        List<CheckResult> persisted = checkRepo.findAllByRecordIdOrderByCreatedAtDesc(recordId);

        // totalRulesEvaluated is unknown for a pure read — the rule set may
        // have changed since the last check. We surface "currently enabled"
        // as a best-effort denominator; the frontend only uses it for the
        // "通过 X / Y" badge, so an off-by-one between historical and current
        // rule counts is acceptable.
        int currentEnabled = (int) ruleRepo.findAllByEnabledTrue().size();
        return buildResponse(record, currentEnabled, persisted, List.of());
    }

    // -------------------------------------------------------------------------
    // hit construction
    // -------------------------------------------------------------------------
    private CheckResult buildHit(MedicalRecordMain record, QcRule rule) {
        String fieldPath = extractFirstField(rule.getExpression());
        Object fieldValue = (fieldPath != null && FieldAccessor.isKnown(fieldPath))
            ? FieldAccessor.get(fieldPath, record)
            : null;

        CheckResult hit = new CheckResult();
        hit.setRecordId(record.getId());
        hit.setRuleId(rule.getId());
        hit.setRuleCodeSnapshot(rule.getCode());
        hit.setRuleNameSnapshot(rule.getName());
        hit.setRuleSeveritySnapshot(rule.getSeverity());
        hit.setRuleDimensionSnapshot(rule.getDimension());
        hit.setFieldPath(fieldPath);
        hit.setFieldValueSnapshot(stringify(fieldValue));
        hit.setHitMessage(renderTemplate(rule.getErrorMessageTemplate(), record));
        hit.setStatus("open");
        return hit;
    }

    /**
     * BFS the DSL tree for the first {@code "field": "..."} literal.
     * Prefers the {@code assert} subtree when present (R001-shaped rules)
     * so the displayed field points at the failing assertion, not the
     * always-true precondition. Falls back to the whole tree otherwise.
     */
    static String extractFirstField(JsonNode dsl) {
        if (dsl == null) return null;
        JsonNode root = dsl.has("assert") ? dsl.get("assert") : dsl;

        Deque<JsonNode> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            JsonNode n = queue.poll();
            if (n == null) continue;
            if (n.isObject()) {
                JsonNode field = n.get("field");
                if (field != null && field.isTextual()) {
                    return field.asText();
                }
                Iterator<Map.Entry<String, JsonNode>> it = n.fields();
                while (it.hasNext()) {
                    JsonNode child = it.next().getValue();
                    if (child.isObject() || child.isArray()) queue.add(child);
                }
            } else if (n.isArray()) {
                n.forEach(queue::add);
            }
        }
        return null;
    }

    /**
     * Replace {@code {{xxx}}} placeholders with field values:
     * <ul>
     *   <li>known field, value present → string form (LocalDate ISO, BigDecimal plain).
     *   <li>known field, value null   → "（未填写）" (medical-form friendly fallback).
     *   <li>unknown field             → leave the literal {@code {{xxx}}} so
     *       a typo in the rule template is visible at first glance.
     * </ul>
     */
    static String renderTemplate(String template, MedicalRecordMain record) {
        if (template == null) return "";
        Matcher m = TEMPLATE_VAR.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String fieldName = m.group(1);
            String replacement;
            if (FieldAccessor.isKnown(fieldName)) {
                Object v = FieldAccessor.get(fieldName, record);
                replacement = (v == null) ? MISSING_VALUE_LABEL : String.valueOf(v);
            } else {
                replacement = m.group(0);  // keep "{{badName}}" verbatim
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String stringify(Object v) {
        if (v == null) return null;
        if (v instanceof LocalDate ld) return ld.toString();
        return String.valueOf(v);
    }

    // -------------------------------------------------------------------------
    // response assembly  (shared by POST /check and GET /check-results)
    // -------------------------------------------------------------------------
    private static CheckSummaryResponse buildResponse(MedicalRecordMain record,
                                                      int totalRulesEvaluated,
                                                      List<CheckResult> persisted,
                                                      List<SkippedRuleDto> skipped) {
        Map<String, Integer> byDimension = new HashMap<>();
        Map<String, Integer> bySeverity = new HashMap<>();
        for (String d : List.of("completeness", "logic", "standardization", "consistency")) {
            byDimension.put(d, 0);
        }
        for (String s : List.of("mandatory", "deduction", "hint")) {
            bySeverity.put(s, 0);
        }

        List<CheckResultItemDto> items = new ArrayList<>(persisted.size());
        for (CheckResult cr : persisted) {
            byDimension.merge(cr.getRuleDimensionSnapshot(), 1, Integer::sum);
            bySeverity.merge(cr.getRuleSeveritySnapshot(), 1, Integer::sum);
            items.add(new CheckResultItemDto(
                cr.getId(),
                cr.getRuleCodeSnapshot(),
                cr.getRuleNameSnapshot(),
                cr.getRuleDimensionSnapshot(),
                cr.getRuleSeveritySnapshot(),
                cr.getFieldPath(),
                cr.getFieldValueSnapshot(),
                cr.getHitMessage(),
                cr.getStatus(),
                cr.getLlmExplanation() != null,
                cr.getCreatedAt()
            ));
        }

        // checkedAt = the freshest hit's createdAt; null when the record has
        // never been checked OR was checked and produced zero hits but
        // status is still 'checked'. Combined with recordStatus the frontend
        // can disambiguate "未检查" from "全通过".
        OffsetDateTime checkedAt = persisted.isEmpty() ? null : persisted.get(0).getCreatedAt();

        return new CheckSummaryResponse(
            record.getId(),
            record.getStatus(),
            checkedAt,
            new SummaryStats(totalRulesEvaluated, items.size(), byDimension, bySeverity),
            items,
            skipped
        );
    }
}
