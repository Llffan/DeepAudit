package com.deepaudit.api.dto;

/**
 * Request body for {@code PATCH /api/rules/{id}/enabled}. Only the
 * single boolean field; DSL is not re-validated on a flag flip.
 */
public record RuleEnabledPatch(boolean enabled) {
}
