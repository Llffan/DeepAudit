package com.deepaudit.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * One row per rule hit (V1 §4.5). Carries write-time snapshots so later
 * rule edits don't pollute history.
 */
@Entity
@Table(name = "check_result")
public class CheckResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "record_id", nullable = false)
    private Long recordId;

    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    @Column(name = "rule_code_snapshot", nullable = false, length = 64)
    private String ruleCodeSnapshot;

    @Column(name = "rule_name_snapshot", nullable = false, length = 200)
    private String ruleNameSnapshot;

    @Column(name = "rule_severity_snapshot", nullable = false, length = 20)
    private String ruleSeveritySnapshot;

    @Column(name = "rule_dimension_snapshot", nullable = false, length = 20)
    private String ruleDimensionSnapshot;

    @Column(name = "field_path", length = 200)
    private String fieldPath;

    @Column(name = "field_value_snapshot", columnDefinition = "text")
    private String fieldValueSnapshot;

    @Column(name = "hit_message", nullable = false, columnDefinition = "text")
    private String hitMessage;

    @Column(name = "llm_explanation", columnDefinition = "text")
    private String llmExplanation;

    @Column(name = "llm_explained_at")
    private OffsetDateTime llmExplainedAt;

    /** open | acknowledged | false_positive */
    @Column(nullable = false, length = 20)
    private String status = "open";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }
    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public String getRuleCodeSnapshot() { return ruleCodeSnapshot; }
    public void setRuleCodeSnapshot(String ruleCodeSnapshot) { this.ruleCodeSnapshot = ruleCodeSnapshot; }
    public String getRuleNameSnapshot() { return ruleNameSnapshot; }
    public void setRuleNameSnapshot(String ruleNameSnapshot) { this.ruleNameSnapshot = ruleNameSnapshot; }
    public String getRuleSeveritySnapshot() { return ruleSeveritySnapshot; }
    public void setRuleSeveritySnapshot(String ruleSeveritySnapshot) { this.ruleSeveritySnapshot = ruleSeveritySnapshot; }
    public String getRuleDimensionSnapshot() { return ruleDimensionSnapshot; }
    public void setRuleDimensionSnapshot(String ruleDimensionSnapshot) { this.ruleDimensionSnapshot = ruleDimensionSnapshot; }
    public String getFieldPath() { return fieldPath; }
    public void setFieldPath(String fieldPath) { this.fieldPath = fieldPath; }
    public String getFieldValueSnapshot() { return fieldValueSnapshot; }
    public void setFieldValueSnapshot(String fieldValueSnapshot) { this.fieldValueSnapshot = fieldValueSnapshot; }
    public String getHitMessage() { return hitMessage; }
    public void setHitMessage(String hitMessage) { this.hitMessage = hitMessage; }
    public String getLlmExplanation() { return llmExplanation; }
    public void setLlmExplanation(String llmExplanation) { this.llmExplanation = llmExplanation; }
    public OffsetDateTime getLlmExplainedAt() { return llmExplainedAt; }
    public void setLlmExplainedAt(OffsetDateTime llmExplainedAt) { this.llmExplainedAt = llmExplainedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
