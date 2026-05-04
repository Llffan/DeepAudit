package com.deepaudit.api.controller;

import com.deepaudit.api.dto.DryRunRequest;
import com.deepaudit.api.dto.DryRunResponse;
import com.deepaudit.api.dto.QcRuleDto;
import com.deepaudit.engine.RuleDslValidator;
import com.deepaudit.engine.RuleDslValidator.ValidationResult;
import com.deepaudit.engine.RuleEvaluator;
import com.deepaudit.persistence.entity.MedicalRecordMain;
import com.deepaudit.persistence.entity.QcRule;
import com.deepaudit.persistence.repository.QcRuleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * MVP slice of the rule API surface (plan §3.8). Only the read-list
 * and dry-run endpoints exist today -- enough for the sandbox UI to
 * pick a rule and replay it against arbitrary record data without
 * waiting for the full CRUD work in T2.3.
 *
 * <p>Endpoints landing later (T2.3 + T2.5):
 * <ul>
 *   <li>POST /rules            -- create rule (validator + embedding)
 *   <li>PUT /rules/{id}        -- update rule (re-validate)
 *   <li>DELETE /rules/{id}     -- soft-delete (deny if enabled)
 *   <li>PATCH /rules/{id}/enabled
 *   <li>POST /rules/from-natural-language -- LLM NL->DSL
 * </ul>
 */
@RestController
@RequestMapping("/rules")
public class RuleController {

    private static final Logger log = LoggerFactory.getLogger(RuleController.class);

    private final QcRuleRepository ruleRepo;
    private final RuleEvaluator evaluator;
    private final RuleDslValidator validator;
    private final ObjectMapper mapper;

    public RuleController(QcRuleRepository ruleRepo,
                          RuleEvaluator evaluator,
                          RuleDslValidator validator,
                          ObjectMapper mapper) {
        this.ruleRepo = ruleRepo;
        this.evaluator = evaluator;
        this.validator = validator;
        this.mapper = mapper;
    }

    /**
     * List rules. Soft-deleted rows are filtered out by the entity-level
     * {@code @SQLRestriction("deleted_at IS NULL")} so we don't repeat
     * the predicate here.
     */
    @GetMapping
    public List<QcRuleDto> list(@RequestParam(required = false) Boolean enabled) {
        return ruleRepo.findAll().stream()
            .filter(r -> enabled == null || enabled.equals(r.getEnabled()))
            .map(RuleController::toDto)
            .toList();
    }

    /**
     * Evaluate a DSL against an ad-hoc record without persisting either.
     * Drives the sandbox UI; also exposed for T2.6 Tab4.
     */
    @PostMapping("/dry-run")
    public DryRunResponse dryRun(@RequestBody DryRunRequest req) {
        ValidationResult vr = validator.validate(req.expression());
        if (!vr.ok()) {
            return DryRunResponse.invalid(vr.errors());
        }

        MedicalRecordMain record;
        try {
            record = req.record() == null
                ? new MedicalRecordMain()
                : mapper.convertValue(req.record(), MedicalRecordMain.class);
        } catch (IllegalArgumentException e) {
            // Jackson conversion failure (e.g. bad date format)
            return DryRunResponse.error("Cannot bind record JSON: " + e.getMessage());
        }

        try {
            boolean ok = evaluator.evaluate(req.expression(), record);
            return ok ? DryRunResponse.pass() : DryRunResponse.hit();
        } catch (RuntimeException e) {
            log.warn("Dry-run evaluation failed", e);
            return DryRunResponse.error(e.getMessage());
        }
    }

    private static QcRuleDto toDto(QcRule r) {
        return new QcRuleDto(
            r.getId(),
            r.getCode(),
            r.getName(),
            r.getDescription(),
            r.getDimension(),
            r.getSeverity(),
            r.getExpression(),
            r.getErrorMessageTemplate(),
            r.getEnabled(),
            r.getVersion(),
            r.getEffectiveFrom(),
            r.getEffectiveTo(),
            r.getCreatedAt(),
            r.getUpdatedAt()
        );
    }
}
