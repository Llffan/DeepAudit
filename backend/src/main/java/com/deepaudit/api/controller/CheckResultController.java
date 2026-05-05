package com.deepaudit.api.controller;

import com.deepaudit.api.dto.CheckResultItemDto;
import com.deepaudit.api.dto.CheckSummaryResponse;
import com.deepaudit.api.exception.NotFoundException;
import com.deepaudit.api.exception.ValidationException;
import com.deepaudit.persistence.entity.CheckResult;
import com.deepaudit.persistence.repository.CheckResultRepository;
import com.deepaudit.service.CheckService;
import com.deepaudit.service.ViolationExplainerService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * check_result 读取 + 解释端点。
 *
 * <pre>
 *   GET  /check-results/{recordId}          — T4.2 最近一次结果
 *   GET  /check-results/{id}/explain        — T4.3 SSE 流式解释（命中缓存时一次性返回）
 *   PATCH /check-results/{id}/status        — T4.4 三态切换
 * </pre>
 *
 * <p>context-path = /api，所以对外路径是 /api/check-results/…
 */
@RestController
@RequestMapping("/check-results")
public class CheckResultController {

    private static final Set<String> VALID_STATUSES = Set.of("open", "acknowledged", "false_positive");

    private final CheckService checkService;
    private final ViolationExplainerService explainerService;
    private final CheckResultRepository checkResultRepository;

    public CheckResultController(
        CheckService checkService,
        ViolationExplainerService explainerService,
        CheckResultRepository checkResultRepository
    ) {
        this.checkService = checkService;
        this.explainerService = explainerService;
        this.checkResultRepository = checkResultRepository;
    }

    // -------------------------------------------------------------------------
    // T4.2
    // -------------------------------------------------------------------------

    @GetMapping("/{recordId}")
    public CheckSummaryResponse getLatest(@PathVariable Long recordId) {
        return checkService.latestResults(recordId);
    }

    // -------------------------------------------------------------------------
    // T4.3 — SSE 流式解释
    // -------------------------------------------------------------------------

    /**
     * GET /api/check-results/{id}/explain
     *
     * <p>缓存命中：发一条 {@code event:cached} 后关闭流。<br>
     * 首次生成：每个 token 发 {@code event:token}，结束发 {@code event:done}。<br>
     * 异常：发 {@code event:error} 后关闭流。
     *
     * <p>Nginx 侧需配置 {@code proxy_buffering off} 防止 SSE 被缓冲
     * （已在 nginx/default.conf 中对 /explain 端点设置）。
     */
    @GetMapping(value = "/{id}/explain", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter explain(@PathVariable Long id) {
        SseEmitter emitter = new SseEmitter(60_000L);

        Optional<String> cached = explainerService.getCached(id);
        if (cached.isPresent()) {
            try {
                emitter.send(SseEmitter.event().name("cached").data(cached.get()));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }

        explainerService.streamExplanation(
            id,
            token -> {
                try {
                    emitter.send(SseEmitter.event().name("token").data(token));
                } catch (IOException e) {
                    emitter.completeWithError(e);
                }
            },
            () -> {
                try {
                    emitter.send(SseEmitter.event().name("done").data(""));
                    emitter.complete();
                } catch (IOException e) {
                    emitter.completeWithError(e);
                }
            },
            err -> {
                try {
                    emitter.send(SseEmitter.event().name("error").data(err.getMessage()));
                } catch (IOException ignored) {}
                emitter.completeWithError(err);
            }
        );

        return emitter;
    }

    // -------------------------------------------------------------------------
    // T4.4 — 违规状态切换
    // -------------------------------------------------------------------------

    /**
     * PATCH /api/check-results/{id}/status
     * Body: { "status": "acknowledged" | "false_positive" | "open" }
     */
    @PatchMapping("/{id}/status")
    public CheckResultItemDto patchStatus(
        @PathVariable Long id,
        @RequestBody Map<String, String> body
    ) {
        String newStatus = body.get("status");
        if (newStatus == null || !VALID_STATUSES.contains(newStatus)) {
            throw new ValidationException(
                List.of("status: 必须是 open / acknowledged / false_positive 之一"));
        }
        CheckResult result = checkResultRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("check_result#" + id + " 不存在"));
        result.setStatus(newStatus);
        checkResultRepository.save(result);
        return toDto(result);
    }

    // -------------------------------------------------------------------------

    private static CheckResultItemDto toDto(CheckResult r) {
        return new CheckResultItemDto(
            r.getId(),
            r.getRuleCodeSnapshot(),
            r.getRuleNameSnapshot(),
            r.getRuleDimensionSnapshot(),
            r.getRuleSeveritySnapshot(),
            r.getFieldPath(),
            r.getFieldValueSnapshot(),
            r.getHitMessage(),
            r.getStatus(),
            r.getLlmExplanation() != null && !r.getLlmExplanation().isBlank(),
            r.getCreatedAt()
        );
    }
}
