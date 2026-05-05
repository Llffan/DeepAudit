package com.deepaudit.api.controller;

import com.deepaudit.api.exception.NotFoundException;
import com.deepaudit.api.exception.ValidationException;
import com.deepaudit.engine.CustomOperatorRegistry;
import com.deepaudit.persistence.entity.QcOperatorTemplate;
import com.deepaudit.persistence.repository.QcOperatorTemplateRepository;
import com.deepaudit.service.OperatorGeneratorService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * CRUD for custom operator templates.
 *
 * <pre>
 *   GET    /operators          — list all (incl. disabled)
 *   POST   /operators          — create
 *   PUT    /operators/{id}     — full update
 *   PATCH  /operators/{id}/toggle — enable / disable
 *   DELETE /operators/{id}     — permanent delete
 * </pre>
 *
 * <p>Every write calls {@link CustomOperatorRegistry#reload()} so changes
 * take effect immediately for all subsequent rule evaluations.
 */
@RestController
@RequestMapping("/operators")
public class OperatorTemplateController {

    private final QcOperatorTemplateRepository repository;
    private final CustomOperatorRegistry registry;
    private final OperatorGeneratorService generatorService;

    public OperatorTemplateController(QcOperatorTemplateRepository repository,
                                      CustomOperatorRegistry registry,
                                      OperatorGeneratorService generatorService) {
        this.repository = repository;
        this.registry = registry;
        this.generatorService = generatorService;
    }

    public record TemplateRequest(
        String code,
        String name,
        String description,
        List<String> parameterNames,
        JsonNode bodyDsl
    ) {}

    public record GenerateRequest(
        List<String> parameterNames,
        String description
    ) {}

    public record GenerateResponse(
        JsonNode bodyDsl,
        List<String> errors,
        String rawOutput,
        boolean ok
    ) {}

    public record TemplateDto(
        Long id,
        String code,
        String name,
        String description,
        List<String> parameterNames,
        JsonNode bodyDsl,
        boolean enabled,
        String createdAt,
        String updatedAt
    ) {}

    @GetMapping
    public List<TemplateDto> list() {
        return repository.findAllByOrderByCodeAsc().stream().map(this::toDto).toList();
    }

    @PostMapping
    public ResponseEntity<TemplateDto> create(@RequestBody TemplateRequest req) {
        validate(req, null);
        QcOperatorTemplate t = new QcOperatorTemplate();
        apply(req, t);
        repository.save(t);
        registry.reload();
        return ResponseEntity.created(URI.create("/api/operators/" + t.getId())).body(toDto(t));
    }

    @PutMapping("/{id}")
    public TemplateDto update(@PathVariable Long id, @RequestBody TemplateRequest req) {
        QcOperatorTemplate t = find(id);
        validate(req, id);
        apply(req, t);
        repository.save(t);
        registry.reload();
        return toDto(t);
    }

    @PatchMapping("/{id}/toggle")
    public TemplateDto toggle(@PathVariable Long id) {
        QcOperatorTemplate t = find(id);
        t.setEnabled(!Boolean.TRUE.equals(t.getEnabled()));
        repository.save(t);
        registry.reload();
        return toDto(t);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        find(id);
        repository.deleteById(id);
        registry.reload();
        return ResponseEntity.noContent().build();
    }

    /**
     * 让 LLM 根据"参数列表 + 自然语言说明"推荐一段 bodyDsl。前端在抽屉里
     * 选好参数和填好说明后调用此端点，把返回的 JSON 写入 body_dsl 编辑器。
     * 不落库 —— 用户可在编辑器里改完再点保存走 POST /operators。
     */
    @PostMapping("/generate-body-dsl")
    public GenerateResponse generateBodyDsl(@RequestBody GenerateRequest req) {
        OperatorGeneratorService.Response r = generatorService.generate(
            req.parameterNames(), req.description());
        return new GenerateResponse(r.bodyDsl(), r.errors(), r.rawOutput(), r.ok());
    }

    // -------------------------------------------------------------------------

    private void validate(TemplateRequest req, Long excludeId) {
        if (req.code() == null || req.code().isBlank()) {
            throw new ValidationException(List.of("code: 不能为空"));
        }
        if (!req.code().matches("[a-zA-Z][a-zA-Z0-9_]{0,62}")) {
            throw new ValidationException(List.of("code: 只允许字母/数字/下划线，首字母须为字母"));
        }
        if (req.name() == null || req.name().isBlank()) {
            throw new ValidationException(List.of("name: 不能为空"));
        }
        if (req.bodyDsl() == null || req.bodyDsl().isNull()) {
            throw new ValidationException(List.of("bodyDsl: 不能为空"));
        }
        boolean duplicate = excludeId == null
            ? repository.existsByCode(req.code())
            : repository.existsByCodeAndIdNot(req.code(), excludeId);
        if (duplicate) {
            throw new ValidationException(List.of("code: '" + req.code() + "' 已存在"));
        }
    }

    private void apply(TemplateRequest req, QcOperatorTemplate t) {
        t.setCode(req.code());
        t.setName(req.name());
        t.setDescription(req.description());
        t.setParameterNames(req.parameterNames() == null ? List.of() : req.parameterNames());
        t.setBodyDsl(req.bodyDsl());
    }

    private QcOperatorTemplate find(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new NotFoundException("算子模板 #" + id + " 不存在"));
    }

    private TemplateDto toDto(QcOperatorTemplate t) {
        return new TemplateDto(
            t.getId(), t.getCode(), t.getName(), t.getDescription(),
            t.getParameterNames(), t.getBodyDsl(),
            Boolean.TRUE.equals(t.getEnabled()),
            t.getCreatedAt() != null ? t.getCreatedAt().toString() : null,
            t.getUpdatedAt() != null ? t.getUpdatedAt().toString() : null
        );
    }
}
