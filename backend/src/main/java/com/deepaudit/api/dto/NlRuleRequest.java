package com.deepaudit.api.dto;

/** Request body for {@code POST /api/rules/from-natural-language}. */
public record NlRuleRequest(String naturalLanguage) {
}
