package com.deepaudit.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record MedicalRecordExtraDto(
    Long id,
    Long recordId,
    JsonNode extraFields
) {
}
