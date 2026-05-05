package com.deepaudit.service;

import dev.langchain4j.service.UserMessage;

/**
 * langchain4j AiService — 测试助手，通过工具调用驱动 SamplePdfTool
 * 生成带质控陷阱的病案首页测试 PDF。
 *
 * <p>通过 {@code AiServices.builder} 在 LlmAutoConfiguration 中装配，
 * 注入 {@link SamplePdfTool} 的 @Tool 方法后由 Gemini 按需调用。
 */
public interface TestingAssistantService {
    String chat(@UserMessage String message);
}
