package com.deepaudit.service;

import com.deepaudit.api.exception.NotFoundException;
import com.deepaudit.persistence.entity.CheckResult;
import com.deepaudit.persistence.repository.CheckResultRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * T4.3 — 违规解释服务。
 *
 * <p>两条路径：
 * <ol>
 *   <li>缓存命中：直接返回 {@code llm_explanation}，调用方发 {@code event:cached}
 *   <li>首次生成：调 {@link StreamingChatLanguageModel} 流式输出，每 token 回调
 *       {@code onToken}，完成后写回缓存并调 {@code onDone}
 * </ol>
 *
 * <p>LLM 不可用（API Key 未配置）时，通过 {@code onError} 回传异常，
 * 业务流不阻断。
 */
@Service
public class ViolationExplainerService {

    private static final Logger log = LoggerFactory.getLogger(ViolationExplainerService.class);

    private final CheckResultRepository repository;
    private final ObjectProvider<StreamingChatLanguageModel> streamingModelProvider;
    private final String systemPrompt;

    public ViolationExplainerService(
        CheckResultRepository repository,
        ObjectProvider<StreamingChatLanguageModel> streamingModelProvider
    ) throws IOException {
        this.repository = repository;
        this.streamingModelProvider = streamingModelProvider;
        try (InputStream in = new ClassPathResource("prompts/explainer.system.txt").getInputStream()) {
            this.systemPrompt = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        }
        log.info("ViolationExplainerService loaded system prompt ({} chars)", systemPrompt.length());
    }

    /** 返回已缓存的解释，若无缓存返回空。 */
    public Optional<String> getCached(Long resultId) {
        return repository.findById(resultId)
            .map(CheckResult::getLlmExplanation)
            .filter(s -> s != null && !s.isBlank());
    }

    /**
     * 流式生成违规解释。
     *
     * @param resultId check_result 行的主键
     * @param onToken  每个 token 回调（在 LLM 回调线程执行）
     * @param onDone   生成完毕回调（缓存已写回）
     * @param onError  异常回调
     */
    public void streamExplanation(
        Long resultId,
        Consumer<String> onToken,
        Runnable onDone,
        Consumer<Throwable> onError
    ) {
        StreamingChatLanguageModel model = streamingModelProvider.getIfAvailable();
        if (model == null) {
            onError.accept(new IllegalStateException("LLM 未配置，无法生成解释（检查 API Key）"));
            return;
        }

        CheckResult result = repository.findById(resultId)
            .orElseThrow(() -> new NotFoundException("check_result#" + resultId + " 不存在"));

        String userMessage = buildUserMessage(result);
        StringBuilder accumulated = new StringBuilder();

        log.info("Starting streaming explanation for check_result#{}", resultId);
        model.generate(
            List.of(SystemMessage.from(systemPrompt), UserMessage.from(userMessage)),
            new StreamingResponseHandler<>() {
                @Override
                public void onNext(String token) {
                    accumulated.append(token);
                    onToken.accept(token);
                }

                @Override
                public void onComplete(Response<AiMessage> response) {
                    String explanation = accumulated.toString();
                    try {
                        result.setLlmExplanation(explanation);
                        result.setLlmExplainedAt(OffsetDateTime.now());
                        repository.save(result);
                        log.info("Cached explanation for check_result#{} ({} chars)",
                            resultId, explanation.length());
                    } catch (Exception e) {
                        log.warn("Failed to cache explanation for check_result#{}: {}", resultId, e.toString());
                    }
                    onDone.run();
                }

                @Override
                public void onError(Throwable error) {
                    log.error("LLM streaming error for check_result#{}", resultId, error);
                    onError.accept(error);
                }
            }
        );
    }

    private static String buildUserMessage(CheckResult r) {
        return String.format(
            "规则：%s（%s）%n严重程度：%s%n质控维度：%s%n问题字段：%s%n字段当前值：%s%n错误说明：%s",
            r.getRuleNameSnapshot(), r.getRuleCodeSnapshot(),
            r.getRuleSeveritySnapshot(),
            r.getRuleDimensionSnapshot(),
            r.getFieldPath() != null ? r.getFieldPath() : "—",
            r.getFieldValueSnapshot() != null ? r.getFieldValueSnapshot() : "（空）",
            r.getHitMessage()
        );
    }
}
