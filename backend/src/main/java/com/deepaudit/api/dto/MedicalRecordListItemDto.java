package com.deepaudit.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Slim row for the list page — only the columns shown in the table,
 * keeps the payload small when paging through hundreds of records.
 */
public record MedicalRecordListItemDto(
    Long id,
    String recordNo,
    String name,
    String gender,
    Integer age,
    LocalDate admissionDate,
    LocalDate dischargeDate,
    Integer lengthOfStay,
    String mainDiagnosisName,
    String status,
    BigDecimal extractionConfidence,
    boolean hasPdf,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
