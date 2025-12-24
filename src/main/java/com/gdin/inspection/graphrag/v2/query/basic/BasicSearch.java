package com.gdin.inspection.graphrag.v2.query.basic;

import cn.hutool.core.util.IdUtil;
import com.gdin.inspection.graphrag.assistant.ThinkAssistant;
import com.gdin.inspection.graphrag.service.AssistantGenerator;
import com.gdin.inspection.graphrag.util.SseUtil;
import com.gdin.inspection.graphrag.v2.query.SearchResult;
import com.gdin.inspection.graphrag.v2.query.context.BasicSearchContext;
import com.gdin.inspection.graphrag.v2.query.context.ContextBuilderResult;
import com.gdin.inspection.graphrag.v2.query.prompts.BasicSearchSystemPromptZh;
import com.gdin.inspection.graphrag.v2.util.TokenUtil;
import com.gdin.inspection.graphrag.v2.query.callbacks.QueryCallbacks;
import dev.langchain4j.service.TokenStream;
import jakarta.annotation.Resource;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class BasicSearch {

    @Resource
    private AssistantGenerator assistantGenerator;

    @Resource
    private BasicSearchContext basicSearchContext;

    @Resource
    private TokenUtil tokenUtil;

    @Setter
    private String systemPromptTemplate = BasicSearchSystemPromptZh.BASIC_SEARCH_SYSTEM_PROMPT_ZH;

    @Setter
    private String responseType = "multiple paragraphs";

    private final List<QueryCallbacks> callbacks = new ArrayList<>();

    public void addCallback(QueryCallbacks callback) {
        if (callback != null) callbacks.add(callback);
    }

    public void clearCallbacks() {
        callbacks.clear();
    }

    /**
     * 对齐 Python: search() -> 返回 SearchResult（聚合完整 response）
     */
    public SearchResult search(
            String query,
            Integer k,
            Integer maxContextTokens,
            String filter
    ) {
        long startNs = System.nanoTime();

        String searchPrompt = "";
        Map<String, Integer> llmCallsCategories = new LinkedHashMap<>();
        Map<String, Integer> promptTokensCategories = new LinkedHashMap<>();
        Map<String, Integer> outputTokensCategories = new LinkedHashMap<>();

        ContextBuilderResult contextResult = basicSearchContext.buildContext(
                query,
                k,
                maxContextTokens,
                "Sources",
                "|",
                "source_id",
                "text",
                filter
        );

        llmCallsCategories.put("build_context", contextResult.getLlmCalls());
        promptTokensCategories.put("build_context", contextResult.getPromptTokens());
        outputTokensCategories.put("build_context", contextResult.getOutputTokens());

        try {
            searchPrompt = formatSystemPrompt(systemPromptTemplate, contextResult.getContextChunks(), responseType);

            // LLM 调用方式按你给的范式
            String memoryId = IdUtil.getSnowflakeNextIdStr();
            ThinkAssistant assistant = assistantGenerator.createTempAssistant(ThinkAssistant.class, searchPrompt);
            TokenStream tokenStream = assistant.streamChat(memoryId, query);

            String response = SseUtil.getResponseWithoutThink(null, tokenStream, memoryId);

            llmCallsCategories.put("response", 1);
            int promptTokens = tokenUtil.getTokenCount(searchPrompt);
            int outTokens = tokenUtil.getTokenCount(response);

            promptTokensCategories.put("response", promptTokens);
            outputTokensCategories.put("response", outTokens);

            // 对齐 Python：生成结束后 callback.on_context
            for (QueryCallbacks cb : callbacks) {
                cb.onContextRecords(contextResult.getContextRecords());
            }

            double elapsed = (System.nanoTime() - startNs) / 1_000_000_000.0;

            // Python: output_tokens = sum(output_tokens.values())
            int totalOut = 0;
            for (Integer v : outputTokensCategories.values()) totalOut += (v == null ? 0 : v);

            return new SearchResult(
                    response,
                    contextResult.getContextRecords(),
                    contextResult.getContextChunks(),
                    elapsed,
                    1,
                    promptTokens,
                    totalOut,
                    llmCallsCategories,
                    promptTokensCategories,
                    outputTokensCategories
            );
        } catch (Exception e) {
            log.error("Exception in BasicSearch.search", e);
            double elapsed = (System.nanoTime() - startNs) / 1_000_000_000.0;

            int promptTokens = tokenUtil.getTokenCount(searchPrompt);
            llmCallsCategories.put("response", 1);
            promptTokensCategories.put("response", promptTokens);
            outputTokensCategories.put("response", 0);

            return new SearchResult(
                    "",
                    contextResult.getContextRecords(),
                    contextResult.getContextChunks(),
                    elapsed,
                    1,
                    promptTokens,
                    0,
                    llmCallsCategories,
                    promptTokensCategories,
                    outputTokensCategories
            );
        }
    }

    /**
     * 对齐 Python: stream_search() -> 先 build_context，再返回流式 TokenStream
     * 你们 SSE 流式输出可以直接把 TokenStream 丢给 SseUtil 去推。
     */
    public BasicStreamResult streamSearch(
            String query,
            Integer k,
            Integer maxContextTokens,
            String filter
    ) {
        ContextBuilderResult contextResult = basicSearchContext.buildContext(
                query,
                k,
                maxContextTokens,
                "Sources",
                "|",
                "source_id",
                "text",
                filter
        );

        // 对齐 Python：stream_search 在开始输出前先 callback.on_context
        for (QueryCallbacks cb : callbacks) {
            cb.onContextRecords(contextResult.getContextRecords());
        }

        String searchPrompt = formatSystemPrompt(systemPromptTemplate, contextResult.getContextChunks(), responseType);

        String memoryId = IdUtil.getSnowflakeNextIdStr();
        ThinkAssistant assistant = assistantGenerator.createTempAssistant(ThinkAssistant.class, searchPrompt);
        TokenStream tokenStream = assistant.streamChat(memoryId, query);

        return new BasicStreamResult(memoryId, searchPrompt, contextResult, tokenStream);
    }

    private String formatSystemPrompt(String template, String contextData, String responseType) {
        // 对齐 Python str.format 的效果（这里只用到两个占位符）
        return template
                .replace("{context_data}", contextData == null ? "" : contextData)
                .replace("{response_type}", responseType == null ? "multiple paragraphs" : responseType);
    }

    public record BasicStreamResult(
            String memoryId,
            String systemPrompt,
            ContextBuilderResult context,
            TokenStream tokenStream
    ) {}
}
