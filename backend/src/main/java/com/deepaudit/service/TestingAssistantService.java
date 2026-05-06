package com.deepaudit.service;

import dev.langchain4j.service.UserMessage;

/**
 * langchain4j AiService — 病案导出助手，通过工具调用驱动 SamplePdfTool
 * 把数据库里已有的病案首页按 ID 导出为 PDF。
 *
 * <p>通过 {@code AiServices.builder} 在 LlmAutoConfiguration 中装配，
 * 注入 {@link SamplePdfTool} 的 @Tool 方法后由 DeepSeek 按需调用。
 *
 * <p>历史命名：曾经服务于"生成带质控陷阱的测试 PDF"场景，故类名仍叫
 * {@code TestingAssistantService}。后续如要更名一并改前端 ref/路由。
 */
public interface TestingAssistantService {
    String chat(@UserMessage String message);
}
