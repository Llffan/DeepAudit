package com.deepaudit.persistence.entity;

import com.deepaudit.persistence.type.VectorUserType;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * QC rule (V1 §4.3). Soft-deleted via {@code deleted_at}; @SQLRestriction
 * filters those rows out of every JPA query so callers don't have to.
 */
@Entity
@Table(name = "qc_rule")
@SQLRestriction("deleted_at IS NULL")
public class QcRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    /** completeness | logic | standardization | consistency */
    @Column(nullable = false, length = 20)
    private String dimension;

    /** mandatory | deduction | hint */
    @Column(nullable = false, length = 20)
    private String severity;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode expression;

    @Column(name = "error_message_template", nullable = false, columnDefinition = "text")
    private String errorMessageTemplate;

    @Column(nullable = false)
    private Boolean enabled = Boolean.TRUE;

    /** Domain version field (NOT JPA @Version optimistic-lock). */
    @Column(nullable = false)
    private Integer version = 1;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Type(VectorUserType.class)
    @Column(name = "description_embedding", columnDefinition = "vector(768)")
    private float[] descriptionEmbedding;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getDimension() { return dimension; }
    public void setDimension(String dimension) { this.dimension = dimension; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public JsonNode getExpression() { return expression; }
    public void setExpression(JsonNode expression) { this.expression = expression; }
    public String getErrorMessageTemplate() { return errorMessageTemplate; }
    public void setErrorMessageTemplate(String errorMessageTemplate) { this.errorMessageTemplate = errorMessageTemplate; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }
    public float[] getDescriptionEmbedding() { return descriptionEmbedding; }
    public void setDescriptionEmbedding(float[] descriptionEmbedding) { this.descriptionEmbedding = descriptionEmbedding; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }
}
