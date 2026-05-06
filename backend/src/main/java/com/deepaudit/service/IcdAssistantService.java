package com.deepaudit.service;

import dev.langchain4j.service.UserMessage;

/**
 * langchain4j AiService — ICD 编码查询助手。用户在对话窗口里自然语言提问，
 * 由 LLM 自主决定调用 {@link IcdLookupTool} 的哪个工具方法（按名搜、按码反查、
 * 看库存量），最后用中文给出综合回复。
 *
 * <p>装配位置：{@link com.deepaudit.config.LlmAutoConfiguration#icdAssistantService}。
 * 单轮对话语义（无历史）—— 多轮上下文需要在前端拼好或后续扩展。
 */
public interface IcdAssistantService {
    String chat(@UserMessage String message);
}
