package com.deepaudit.service;

import com.deepaudit.api.dto.RuleCreateRequest;
import com.deepaudit.api.dto.RuleUpdateRequest;
import com.deepaudit.api.exception.ConflictException;
import com.deepaudit.api.exception.NotFoundException;
import com.deepaudit.api.exception.ValidationException;
import com.deepaudit.engine.RuleDslValidator;
import com.deepaudit.engine.RuleDslValidator.ValidationResult;
import com.deepaudit.persistence.entity.QcRule;
import com.deepaudit.persistence.repository.QcRuleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Business layer for the QC rule lifecycle (plan §5). Owns transactions,
 * raises domain exceptions, and routes every persisted DSL through
 * {@link RuleDslValidator} so the evaluator never sees malformed input.
 *
 * <p>{@code description_embedding} is filled best-effort via
 * {@link EmbeddingService}: when the embedding model bean is absent
 * (no GEMINI_API_KEY) or the call fails, the rule is still saved with
 * a NULL embedding. Similarity-based dedup (plan §5.5) silently skips
 * rules without embeddings.
 */
@Service
public class RuleService {

    private static final Logger log = LoggerFactory.getLogger(RuleService.class);

    private static final Set<String> DIMENSIONS =
        Set.of("completeness", "logic", "standardization", "consistency");
    private static final Set<String> SEVERITIES =
        Set.of("mandatory", "deduction", "hint");

    private final QcRuleRepository ruleRepo;
    private final RuleDslValidator dslValidator;
    private final EmbeddingService embeddingService;

    public RuleService(QcRuleRepository ruleRepo,
                       RuleDslValidator dslValidator,
                       EmbeddingService embeddingService) {
        this.ruleRepo = ruleRepo;
        this.dslValidator = dslValidator;
        this.embeddingService = embeddingService;
    }

    @Transactional
    public QcRule create(RuleCreateRequest req) {
        validateInputs(
            req.code(), req.name(), req.dimension(), req.severity(),
            req.errorMessageTemplate(), req.expression(), /* codeIsRequired = */ true);

        if (ruleRepo.findByCode(req.code()).isPresent()) {
            throw new ConflictException("规则编码已存在：" + req.code());
        }

        QcRule rule = new QcRule();
        applyToEntity(rule, req.code(), req.name(), req.description(),
            req.dimension(), req.severity(), req.expression(),
            req.errorMessageTemplate(), req.enabled(),
            req.effectiveFrom(), req.effectiveTo());

        fillEmbedding(rule);
        return ruleRepo.save(rule);
    }

    @Transactional
    public QcRule update(Long id, RuleUpdateRequest req) {
        // code stays untouched in update -- skip the code check.
        validateInputs(
            null, req.name(), req.dimension(), req.severity(),
            req.errorMessageTemplate(), req.expression(), /* codeIsRequired = */ false);

        QcRule rule = ruleRepo.findById(id).orElseThrow(() ->
            new NotFoundException("规则不存在：id=" + id));

        String oldCorpus = corpusFor(rule);
        applyToEntity(rule, rule.getCode(), req.name(), req.description(),
            req.dimension(), req.severity(), req.expression(),
            req.errorMessageTemplate(), req.enabled(),
            req.effectiveFrom(), req.effectiveTo());

        // Re-embed only when name or description changed; embedding is the
        // expensive call on the write path (~150ms typical) and rule
        // metadata edits without semantic-text changes don't need it.
        if (!Objects.equals(oldCorpus, corpusFor(rule))) {
            fillEmbedding(rule);
        }
        return ruleRepo.save(rule);
    }

    @Transactional
    public void softDelete(Long id) {
        QcRule rule = ruleRepo.findById(id).orElseThrow(() ->
            new NotFoundException("规则不存在：id=" + id));

        if (Boolean.TRUE.equals(rule.getEnabled())) {
            throw new ConflictException(
                "请先停用规则再删除（编码：" + rule.getCode() + "）");
        }

        rule.setDeletedAt(OffsetDateTime.now());
        ruleRepo.save(rule);
    }

    @Transactional
    public QcRule setEnabled(Long id, boolean enabled) {
        QcRule rule = ruleRepo.findById(id).orElseThrow(() ->
            new NotFoundException("规则不存在：id=" + id));
        rule.setEnabled(enabled);
        return ruleRepo.save(rule);
    }

    // ---- shared internals -------------------------------------------------

    private void validateInputs(String code, String name, String dimension,
                                String severity, String errorMessageTemplate,
                                JsonNode expression, boolean codeIsRequired) {
        List<String> errors = new ArrayList<>();

        if (codeIsRequired) {
            if (code == null || code.isBlank()) {
                errors.add("code: required");
            } else if (code.length() > 64) {
                errors.add("code: must be ≤ 64 characters");
            }
        }
        if (name == null || name.isBlank()) {
            errors.add("name: required");
        } else if (name.length() > 200) {
            errors.add("name: must be ≤ 200 characters");
        }
        if (!DIMENSIONS.contains(dimension)) {
            errors.add("dimension: must be one of " + DIMENSIONS);
        }
        if (!SEVERITIES.contains(severity)) {
            errors.add("severity: must be one of " + SEVERITIES);
        }
        if (errorMessageTemplate == null || errorMessageTemplate.isBlank()) {
            errors.add("errorMessageTemplate: required");
        }

        // DSL structural validation -- contributes its own path-prefixed errors.
        ValidationResult dslResult = dslValidator.validate(expression);
        if (!dslResult.ok()) {
            errors.addAll(dslResult.errors());
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    private void fillEmbedding(QcRule rule) {
        String corpus = corpusFor(rule);
        if (corpus.isBlank()) {
            return;
        }
        float[] vec = embeddingService.embed(corpus);
        if (vec != null) {
            rule.setDescriptionEmbedding(vec);
        }
        // null result already logged inside EmbeddingService -- silently skip here.
    }

    private static String corpusFor(QcRule rule) {
        // Embedding source = name + description. Mirrors plan §5.5 dedup
        // intent (compare rules by what they semantically check, which lives
        // in name + free-text description, NOT the structural DSL tree).
        StringBuilder sb = new StringBuilder();
        if (rule.getName() != null) sb.append(rule.getName());
        if (rule.getDescription() != null) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(rule.getDescription());
        }
        return sb.toString();
    }

    private static void applyToEntity(QcRule rule, String code, String name,
                                      String description, String dimension,
                                      String severity, JsonNode expression,
                                      String errorMessageTemplate, Boolean enabled,
                                      LocalDate effectiveFrom, LocalDate effectiveTo) {
        rule.setCode(code);
        rule.setName(name);
        rule.setDescription(description);
        rule.setDimension(dimension);
        rule.setSeverity(severity);
        rule.setExpression(expression);
        rule.setErrorMessageTemplate(errorMessageTemplate);
        if (enabled != null) {
            rule.setEnabled(enabled);
        }
        rule.setEffectiveFrom(effectiveFrom);
        rule.setEffectiveTo(effectiveTo);
    }
}
