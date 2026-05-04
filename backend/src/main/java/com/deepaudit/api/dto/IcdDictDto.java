package com.deepaudit.api.dto;

import java.time.OffsetDateTime;

/**
 * Read-shape DTO for {@code icd_dict}. {@code nameEmbedding} omitted on
 * purpose -- it's an internal index column for pgvector similarity search,
 * not something we'd return to the UI.
 */
public record IcdDictDto(
    Long id,
    String code,
    String name,
    String category,
    String version,
    OffsetDateTime createdAt
) {
}
