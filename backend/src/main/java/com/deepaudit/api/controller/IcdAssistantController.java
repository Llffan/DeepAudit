package com.deepaudit.api.controller;

import com.deepaudit.service.IcdAssistantService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ICD 编码查询助手对话接口。前端在病案录入或编码核查的对话窗口直接 POST
 * 用户的自然语言问题；DeepSeek 通过 IcdLookupTool 的 @Tool 方法在服务端
 * 自主决定调用 searchByName / lookupByCode / stats。
 *
 * <p>POST /api/icd-assistant/chat  (context-path=/api + mapping=/icd-assistant)
 * <pre>
 *   { "message": "腹腔镜阑尾切除是什么编码？" }
 *   →
 *   { "reply": "很可能是 47.0100 腹腔镜下阑尾切除术（相似度 0.92）。其他候选：..." }
 * </pre>
 *
 * <p>LLM 不可用时（DEEPSEEK_API_KEY 缺失）返回 503；DASHSCOPE_API_KEY 缺失时
 * 接口仍然可用（lookupByCode/stats 不依赖向量），searchByName 内部返回 ERROR
 * 由 LLM 在回复中如实转告。
 */
@RestController
@RequestMapping("/icd-assistant")
public class IcdAssistantController {

    private final ObjectProvider<IcdAssistantService> assistantProvider;

    public IcdAssistantController(ObjectProvider<IcdAssistantService> assistantProvider) {
        this.assistantProvider = assistantProvider;
    }

    public record ChatRequest(String message) {}
    public record ChatResponse(String reply) {}

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest req) {
        IcdAssistantService assistant = assistantProvider.getIfAvailable();
        if (assistant == null) {
            return ResponseEntity.status(503)
                .body(new ChatResponse("LLM 未配置（缺少 DEEPSEEK_API_KEY），编码助手不可用"));
        }
        if (req == null || req.message() == null || req.message().isBlank()) {
            return ResponseEntity.badRequest()
                .body(new ChatResponse("消息不能为空"));
        }
        String reply = assistant.chat(req.message());
        return ResponseEntity.ok(new ChatResponse(reply));
    }
}
