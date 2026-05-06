package com.deepaudit.api.controller;

import com.deepaudit.service.TestingAssistantService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 病案导出助手对话接口。DeepSeek 通过 SamplePdfTool 的 exportRecordAsPdf
 * @Tool 方法把数据库里已有的病案首页按 ID 渲染为 PDF。
 *
 * <p>路由保留旧名 {@code /testing-assistant} 以兼容前端引用；后续重构时
 * 再统一改为 {@code /record-export-assistant} 之类。
 *
 * POST /api/testing-assistant/chat  (context-path=/api + mapping=/testing-assistant)
 *   { "message": "把 ID=42 的病案导出成 PDF" }
 *   →
 *   { "reply": "已导出 data/samples/record_42_xxx.pdf ..." }
 *
 * LLM 不可用时（未配置 DEEPSEEK_API_KEY）返回 503。
 */
@RestController
@RequestMapping("/testing-assistant")
public class TestingAssistantController {

    private final ObjectProvider<TestingAssistantService> assistantProvider;

    public TestingAssistantController(ObjectProvider<TestingAssistantService> assistantProvider) {
        this.assistantProvider = assistantProvider;
    }

    public record ChatRequest(String message) {}
    public record ChatResponse(String reply) {}

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest req) {
        TestingAssistantService assistant = assistantProvider.getIfAvailable();
        if (assistant == null) {
            return ResponseEntity.status(503)
                .body(new ChatResponse("LLM 未配置（缺少 API Key），测试助手不可用"));
        }
        if (req.message() == null || req.message().isBlank()) {
            return ResponseEntity.badRequest()
                .body(new ChatResponse("消息不能为空"));
        }
        String reply = assistant.chat(req.message());
        return ResponseEntity.ok(new ChatResponse(reply));
    }
}
