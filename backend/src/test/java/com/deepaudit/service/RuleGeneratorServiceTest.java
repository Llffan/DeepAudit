package com.deepaudit.service;

import com.deepaudit.api.dto.NlRuleResponse;
import com.deepaudit.api.exception.ServiceUnavailableException;
import com.deepaudit.engine.RuleDslValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RuleGeneratorService} -- the LLM is mocked at
 * the {@link RuleDslGenerator} interface boundary so we can pin down
 * the parsing / validation / fence-stripping logic without making real
 * API calls.
 */
class RuleGeneratorServiceTest {

    private RuleDslGenerator generator;
    @SuppressWarnings("unchecked")
    private final ObjectProvider<RuleDslGenerator> provider =
        (ObjectProvider<RuleDslGenerator>) mock(ObjectProvider.class);
    private final RuleDslValidator validator = new RuleDslValidator();
    private final ObjectMapper mapper = new ObjectMapper();
    private RuleGeneratorService service;

    @BeforeEach
    void setUp() {
        generator = mock(RuleDslGenerator.class);
        when(provider.getIfAvailable()).thenReturn(generator);
        service = new RuleGeneratorService(provider, validator, mapper);
    }

    @Test
    @DisplayName("Clean JSON output produces a valid response with no errors")
    void clean_json_passes_through() {
        when(generator.generate(anyString())).thenReturn(
            "{\"op\":\"lte\",\"field\":\"age\",\"rhs\":120}");

        NlRuleResponse r = service.generate("年龄不超过 120");

        assertNotNull(r.dsl());
        assertEquals("lte", r.dsl().get("op").asText());
        assertTrue(r.validationErrors().isEmpty(),
            () -> "Expected no validation errors, got: " + r.validationErrors());
        assertNull(r.rawOutput());
    }

    @Test
    @DisplayName("Strips ```json ... ``` markdown fences")
    void strips_json_code_fence() {
        when(generator.generate(anyString())).thenReturn(
            "```json\n{\"op\":\"notNull\",\"field\":\"age\"}\n```");

        NlRuleResponse r = service.generate("年龄必填");

        assertNotNull(r.dsl());
        assertEquals("notNull", r.dsl().get("op").asText());
        assertTrue(r.validationErrors().isEmpty());
    }

    @Test
    @DisplayName("Strips bare ``` ... ``` markdown fences")
    void strips_bare_code_fence() {
        when(generator.generate(anyString())).thenReturn(
            "```\n{\"op\":\"isNull\",\"field\":\"name\"}\n```");

        NlRuleResponse r = service.generate("name 为空");

        assertNotNull(r.dsl());
        assertEquals("isNull", r.dsl().get("op").asText());
    }

    @Test
    @DisplayName("Invalid DSL still returns parsed JSON + validation errors")
    void invalid_dsl_returns_dsl_plus_errors() {
        when(generator.generate(anyString())).thenReturn(
            "{\"op\":\"foo\",\"args\":[]}");

        NlRuleResponse r = service.generate("无效规则");

        assertNotNull(r.dsl(), "Parsed JSON should be returned even when validation fails");
        assertFalse(r.validationErrors().isEmpty());
        assertTrue(r.validationErrors().stream().anyMatch(e -> e.contains("unknown op 'foo'")));
    }

    @Test
    @DisplayName("Non-JSON output returns null dsl + raw + parse error")
    void non_json_output_returns_raw() {
        when(generator.generate(anyString())).thenReturn("Sorry, I cannot help with that.");

        NlRuleResponse r = service.generate("胡乱描述");

        assertNull(r.dsl());
        assertNotNull(r.rawOutput());
        assertTrue(r.rawOutput().contains("Sorry"));
        assertTrue(r.validationErrors().stream().anyMatch(e -> e.contains("不是合法 JSON")));
    }

    @Test
    @DisplayName("LLM call exception is caught and returned as error")
    void llm_call_exception_returns_error() {
        when(generator.generate(anyString())).thenThrow(new RuntimeException("API timeout"));

        NlRuleResponse r = service.generate("任意输入");

        assertNull(r.dsl());
        assertTrue(r.validationErrors().stream().anyMatch(e -> e.contains("API timeout")));
    }

    @Test
    @DisplayName("Missing LLM provider throws ServiceUnavailableException")
    void missing_llm_throws_503() {
        when(provider.getIfAvailable()).thenReturn(null);

        assertThrows(ServiceUnavailableException.class,
            () -> service.generate("任意输入"));
    }

    @Test
    @DisplayName("Field-not-found sentinel surfaces as user-friendly error")
    void field_not_found_sentinel_recognised() {
        when(generator.generate(anyString())).thenReturn(
            "{\"_error\":\"field_not_found\",\"_requested\":\"医保结算金额\"}");

        NlRuleResponse r = service.generate("医保结算金额超过 5 万提示");

        assertNull(r.dsl());
        assertTrue(r.validationErrors().stream().anyMatch(e -> e.contains("医保结算金额")));
    }

    @Test
    @DisplayName("Blank input returns error without calling LLM")
    void blank_input_short_circuits() {
        NlRuleResponse r = service.generate("   ");
        assertNull(r.dsl());
        assertTrue(r.validationErrors().stream().anyMatch(e -> e.contains("不能为空")));
    }
}
