package com.deepaudit.persistence.entity;

import com.deepaudit.persistence.type.VectorUserType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;

/**
 * ICD-10 / ICD-9-CM-3 reference data with semantic embedding (V1 §4.6).
 * Read-mostly; bulk-loaded by ICD seed scripts.
 */
@Entity
@Table(
    name = "icd_dict",
    uniqueConstraints = @UniqueConstraint(
        name = "icd_dict_code_category_version_key",
        columnNames = {"code", "category", "version"})
)
public class IcdDict {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String code;

    @Column(nullable = false, length = 500)
    private String name;

    /** icd10 | icd9cm3 */
    @Column(nullable = false, length = 20)
    private String category;

    @Column(nullable = false, length = 20)
    private String version;

    @Type(VectorUserType.class)
    @Column(name = "name_embedding", columnDefinition = "vector(1024)")
    private float[] nameEmbedding;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public float[] getNameEmbedding() { return nameEmbedding; }
    public void setNameEmbedding(float[] nameEmbedding) { this.nameEmbedding = nameEmbedding; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
