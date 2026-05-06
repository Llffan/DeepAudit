package com.deepaudit.service;

import com.deepaudit.api.dto.IcdMatch;
import com.deepaudit.engine.IcdDictCache;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * langchain4j {@code @Tool} adapter that lets the chat LLM look up ICD
 * codes for the user. Three tools, narrow on purpose so the model picks
 * the right one without ambiguity:
 *
 * <ul>
 *   <li>{@link #searchByName} — fuzzy name → top-K candidate codes
 *       (vector recall via {@link IcdDictCache#topKByCosine}).
 *   <li>{@link #lookupByCode} — exact code → standard name (deterministic
 *       map lookup, no embedding call).
 *   <li>{@link #stats} — coverage probe so the assistant can answer
 *       "how many codes do you know" honestly.
 * </ul>
 *
 * <p>This component must be passed to {@code AiServices.builder().tools(...)}
 * for the LLM to actually invoke it; merely registering as a Spring bean
 * isn't enough (same constraint as {@link SamplePdfTool}).
 */
@Component
public class IcdLookupTool {

    private static final Logger log = LoggerFactory.getLogger(IcdLookupTool.class);

    private final EmbeddingService embeddingService;
    private final IcdDictCache cache;

    public IcdLookupTool(EmbeddingService embeddingService, IcdDictCache cache) {
        this.embeddingService = embeddingService;
        this.cache = cache;
    }

    /**
     * Embed the user's free-text term and return the top-K dictionary rows
     * by cosine similarity. The LLM will compose a natural-language reply
     * that surfaces the recommended code(s) — DON'T pre-format here, just
     * give the model the raw structured candidates.
     *
     * @return formatted multi-line text the LLM reads. Each line is
     *         {@code code | name | similarity}. Empty list yields a clear
     *         "no candidates" string so the model doesn't hallucinate.
     */
    @Tool("根据自然语言术式或疾病名称在 ICD 字典中检索最相似的若干编码。" +
          "category 仅可填 icd9cm3（手术）或 icd10（诊断）。" +
          "topK 建议 3-5。返回每行 code|name|similarity，相似度越接近 1 越像。")
    public String searchByName(
        @P("用户输入的术式或疾病名称，如 '腹腔镜阑尾切除' 或 '感染性腹泻'") String query,
        @P("字典类别，icd9cm3 或 icd10") String category,
        @P("返回候选条数，建议 3-5") int topK
    ) {
        log.info("IcdLookupTool.searchByName query='{}' category={} topK={}", query, category, topK);
        if (query == null || query.isBlank()) {
            return "ERROR: query 不能为空。";
        }
        if (!"icd9cm3".equals(category) && !"icd10".equals(category)) {
            return "ERROR: category 只能是 icd9cm3 或 icd10，收到: " + category;
        }
        if (topK <= 0) topK = 5;
        if (topK > 20) topK = 20;
        if (!embeddingService.isAvailable()) {
            return "ERROR: 向量服务未配置（DASHSCOPE_API_KEY 缺失），无法做语义检索。" +
                   "请运维配置后重启，或先用 lookupByCode 工具按编码反查。";
        }
        float[] q = embeddingService.embed(query);
        if (q == null) {
            return "ERROR: embedding 调用失败，请稍后再试或换一个查询词。";
        }
        List<IcdMatch> matches = cache.topKByCosine(q, category, topK);
        if (matches.isEmpty()) {
            return "未在 " + category + " 字典中找到任何候选。"
                + "可能字典向量尚未灌入（运维需调用 POST /admin/icd-dict/reembed）。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Top-").append(matches.size()).append(" 候选 (category=").append(category).append("):\n");
        for (IcdMatch m : matches) {
            sb.append(String.format("%s | %s | %.4f%n", m.code(), m.name(), m.similarity()));
        }
        return sb.toString();
    }

    /**
     * Exact code → standard name. Useful when the user already has a code
     * and wants its canonical name (or wants to confirm "47.0900 是什么手术").
     */
    @Tool("精确按 ICD 编码反查标准名称。" +
          "category 仅可填 icd9cm3 或 icd10。" +
          "找不到时返回明确的未命中提示，不要编造。")
    public String lookupByCode(
        @P("完整的 ICD 编码，如 '47.0900' 或 'A09.901'") String code,
        @P("字典类别，icd9cm3 或 icd10") String category
    ) {
        log.info("IcdLookupTool.lookupByCode code='{}' category={}", code, category);
        if (code == null || code.isBlank()) {
            return "ERROR: code 不能为空。";
        }
        if (!"icd9cm3".equals(category) && !"icd10".equals(category)) {
            return "ERROR: category 只能是 icd9cm3 或 icd10，收到: " + category;
        }
        String name = cache.getName(code, category);
        if (name == null) {
            return "未命中: " + category + " 字典中没有编码 " + code
                + "。可能编码错误，或字典未覆盖该项（当前为 MVP 精选集）。";
        }
        return code + " | " + name;
    }

    /**
     * Coverage stats — useful when the user asks meta-questions like
     * "你字典里都有哪些类别" or the model wants to know if a category
     * is empty before fan-out.
     */
    @Tool("查询当前 ICD 字典的统计：每个 category 各有多少条编码、" +
          "其中多少条已经向量化（可被 searchByName 检索到）。")
    public String stats() {
        return cache.statsSummary();
    }
}
