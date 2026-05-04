package com.deepaudit.service;

import com.deepaudit.api.dto.RuleCreateRequest;
import com.deepaudit.api.dto.RuleUpdateRequest;
import com.deepaudit.api.exception.ConflictException;
import com.deepaudit.api.exception.NotFoundException;
import com.deepaudit.api.exception.ValidationException;
import com.deepaudit.engine.RuleDslValidator;
import com.deepaudit.persistence.entity.QcRule;
import com.deepaudit.persistence.repository.QcRuleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mock-based tests for {@link RuleService}. The repository is stubbed
 * so we can assert behaviour without spinning up a database; the
 * {@link RuleDslValidator} is the real one because it is pure and
 * testing through it gives the same coverage as composing two units.
 */
class RuleServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private QcRuleRepository ruleRepo;
    private RuleDslValidator validator;
    private RuleService service;

    @BeforeEach
    void setUp() {
        ruleRepo = mock(QcRuleRepository.class);
        validator = new RuleDslValidator();
        service = new RuleService(ruleRepo, validator);
    }

    private static JsonNode dsl(String json) throws Exception {
        return MAPPER.readTree(json);
    }

    private static RuleCreateRequest validCreateReq(String code, JsonNode expression) {
        return new RuleCreateRequest(
            code, "Sample rule", "desc",
            "logic", "hint",
            expression, "Field {{age}} is wrong",
            true, null, null);
    }

    // ---------- create ----------

    @Test
    @DisplayName("create rejects malformed DSL via T2.2 validator")
    void create_rejects_invalid_dsl() throws Exception {
        var req = validCreateReq("R003", dsl("{\"op\":\"unknownOp\"}"));
        ValidationException e = assertThrows(ValidationException.class, () -> service.create(req));
        assertTrue(e.getErrors().stream().anyMatch(s -> s.contains("unknown op")));
        verify(ruleRepo, never()).save(any());
    }

    @Test
    @DisplayName("create rejects missing required fields with combined error list")
    void create_rejects_missing_fields() throws Exception {
        var req = new RuleCreateRequest(
            "", "", null,
            "bogusDimension", "bogusSeverity",
            dsl("{\"op\":\"notNull\",\"field\":\"age\"}"), "",
            true, null, null);
        ValidationException e = assertThrows(ValidationException.class, () -> service.create(req));
        // code, name, dimension, severity, errorMessageTemplate -- 5 distinct issues
        assertTrue(e.getErrors().size() >= 5,
            () -> "Expected ≥5 errors, got: " + e.getErrors());
    }

    @Test
    @DisplayName("create rejects duplicate code")
    void create_rejects_duplicate_code() throws Exception {
        when(ruleRepo.findByCode("R003")).thenReturn(Optional.of(new QcRule()));
        var req = validCreateReq("R003", dsl("{\"op\":\"notNull\",\"field\":\"age\"}"));
        ConflictException e = assertThrows(ConflictException.class, () -> service.create(req));
        assertTrue(e.getMessage().contains("R003"));
        verify(ruleRepo, never()).save(any());
    }

    @Test
    @DisplayName("create persists valid rule with all fields applied")
    void create_persists_valid_rule() throws Exception {
        when(ruleRepo.findByCode("R003")).thenReturn(Optional.empty());
        when(ruleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var req = new RuleCreateRequest(
            "R003", "年龄异常", "年龄超过 120 提示用户确认",
            "logic", "hint",
            dsl("{\"op\":\"lte\",\"field\":\"age\",\"rhs\":120}"),
            "{{age}} 异常",
            false, null, null);
        QcRule saved = service.create(req);

        assertEquals("R003", saved.getCode());
        assertEquals("年龄异常", saved.getName());
        assertEquals("logic", saved.getDimension());
        assertEquals("hint", saved.getSeverity());
        assertFalse(saved.getEnabled(), "enabled=false should propagate from request");
        verify(ruleRepo).save(any());
    }

    // ---------- update ----------

    @Test
    @DisplayName("update throws NotFound when id missing")
    void update_throws_404_when_not_found() throws Exception {
        when(ruleRepo.findById(99L)).thenReturn(Optional.empty());
        var req = new RuleUpdateRequest("name", null,
            "logic", "hint",
            dsl("{\"op\":\"notNull\",\"field\":\"age\"}"),
            "msg", true, null, null);
        assertThrows(NotFoundException.class, () -> service.update(99L, req));
        verify(ruleRepo, never()).save(any());
    }

    @Test
    @DisplayName("update preserves code (immutable post-create)")
    void update_preserves_code() throws Exception {
        QcRule existing = new QcRule();
        existing.setId(1L);
        existing.setCode("R001");
        when(ruleRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(ruleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var req = new RuleUpdateRequest("New name", "new desc",
            "completeness", "mandatory",
            dsl("{\"op\":\"notNull\",\"field\":\"age\"}"),
            "tpl", false, null, null);
        QcRule updated = service.update(1L, req);

        assertEquals("R001", updated.getCode(), "code should not change");
        assertEquals("New name", updated.getName());
        assertEquals("completeness", updated.getDimension());
    }

    // ---------- soft delete ----------

    @Test
    @DisplayName("delete throws Conflict when rule still enabled")
    void delete_throws_409_when_enabled() {
        QcRule r = new QcRule();
        r.setEnabled(true);
        r.setCode("R001");
        when(ruleRepo.findById(1L)).thenReturn(Optional.of(r));

        ConflictException e = assertThrows(ConflictException.class, () -> service.softDelete(1L));
        assertTrue(e.getMessage().contains("R001"));
        assertNull(r.getDeletedAt());
        verify(ruleRepo, never()).save(any());
    }

    @Test
    @DisplayName("delete sets deleted_at on disabled rule")
    void delete_soft_deletes_disabled_rule() {
        QcRule r = new QcRule();
        r.setEnabled(false);
        r.setCode("R001");
        when(ruleRepo.findById(1L)).thenReturn(Optional.of(r));
        when(ruleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.softDelete(1L);
        assertNotNull(r.getDeletedAt());
        verify(ruleRepo).save(r);
    }

    @Test
    @DisplayName("delete throws NotFound on unknown id")
    void delete_throws_404() {
        when(ruleRepo.findById(99L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.softDelete(99L));
    }

    // ---------- enable toggle ----------

    @Test
    @DisplayName("setEnabled toggles flag and saves")
    void setEnabled_toggles() {
        QcRule r = new QcRule();
        r.setEnabled(false);
        when(ruleRepo.findById(1L)).thenReturn(Optional.of(r));
        when(ruleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        QcRule result = service.setEnabled(1L, true);
        assertTrue(result.getEnabled());
        verify(ruleRepo).save(r);
    }

    @Test
    @DisplayName("setEnabled throws NotFound on unknown id")
    void setEnabled_throws_404() {
        when(ruleRepo.findById(99L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.setEnabled(99L, true));
    }
}
