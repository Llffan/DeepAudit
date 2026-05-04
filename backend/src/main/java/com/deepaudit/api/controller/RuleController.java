package com.deepaudit.api.controller;

import com.deepaudit.api.dto.DryRunRequest;
import com.deepaudit.api.dto.DryRunResponse;
import com.deepaudit.api.dto.QcRuleDto;
import com.deepaudit.api.dto.RuleCreateRequest;
import com.deepaudit.api.dto.RuleEnabledPatch;
import com.deepaudit.api.dto.RuleUpdateRequest;
import com.deepaudit.engine.RuleDslValidator;
import com.deepaudit.engine.RuleDslValidator.ValidationResult;
import com.deepaudit.engine.RuleEvaluator;
import com.deepaudit.persistence.entity.MedicalRecordMain;
import com.deepaudit.persistence.entity.QcRule;
import com.deepaudit.persistence.repository.QcRuleRepository;
import com.deepaudit.service.RuleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Rule API surface (plan §3.8 + §5.6). Controller stays thin -- the
 * {@link RuleService} owns transactions and validation, the inline
 * dry-run still uses {@link RuleEvaluator} + {@link RuleDslValidator}
 * directly because it has no persistence side-effects to coordinate.
 */
@RestController
@RequestMapping("/rules")
public class RuleController {

    private static final Logger log = LoggerFactory.getLogger(RuleController.class);

    private final RuleService ruleService;
    private final QcRuleRepository ruleRepo;
    private final RuleEvaluator evaluator;
    private final RuleDslValidator validator;
    private final ObjectMapper mapper;

    public RuleController(RuleService ruleService,
                          QcRuleRepository ruleRepo,
                          RuleEvaluator evaluator,
                          RuleDslValidator validator,
                          ObjectMapper mapper) {
        this.ruleService = ruleService;
        this.ruleRepo = ruleRepo;
        this.evaluator = evaluator;
        this.validator = validator;
        this.mapper = mapper;
    }

    @GetMapping
    public List<QcRuleDto> list(@RequestParam(required = false) Boolean enabled) {
        return ruleRepo.findAll().stream()
            .filter(r -> enabled == null || enabled.equals(r.getEnabled()))
            .map(RuleController::toDto)
            .toList();
    }

    @PostMapping
    public ResponseEntity<QcRuleDto> create(@RequestBody RuleCreateRequest req) {
        QcRule saved = ruleService.create(req);
        return ResponseEntity
            .created(URI.create("/api/rules/" + saved.getId()))
            .body(toDto(saved));
    }

    @PutMapping("/{id}")
    public QcRuleDto update(@PathVariable Long id, @RequestBody RuleUpdateRequest req) {
        return toDto(ruleService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        ruleService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/enabled")
    public QcRuleDto patchEnabled(@PathVariable Long id, @RequestBody RuleEnabledPatch req) {
        return toDto(ruleService.setEnabled(id, req.enabled()));
    }

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
