package com.deepaudit.api.controller;

import com.deepaudit.service.TestingAssistantService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试助手对话接口。Gemini 通过 SamplePdfTool 的 @Tool 方法
 * 在服务端自主决定调用哪个 Python 生成器命令。
 *
 * POST /api/testing-assistant/chat
 *   { "message": "帮我生成一个有 R001 手术信息缺失的测试病案" }
 *   →
 *   { "reply": "已生成 data/samples/case_R001_abc123.pdf ..." }
 *
 * LLM 不可用时（未配置 API Key）返回 503。
 */
@RestController
@RequestMapping("/api/testing-assistant")
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
