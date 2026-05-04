package com.deepaudit.persistence.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JSONB sidecar for record fields outside the curated 30 (V1 §4.4).
 * 1:1 with {@link MedicalRecordMain} via {@code record_id}.
 */
@Entity
@Table(name = "medical_record_extra")
public class MedicalRecordExtra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK to medical_record_main.id (UNIQUE, ON DELETE CASCADE in DDL). */
    @Column(name = "record_id", nullable = false, unique = true)
    private Long recordId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra_fields", nullable = false, columnDefinition = "jsonb")
    private JsonNode extraFields;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }
    public JsonNode getExtraFields() { return extraFields; }
    public void setExtraFields(JsonNode extraFields) { this.extraFields = extraFields; }
}
