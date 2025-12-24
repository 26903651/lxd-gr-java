package com.gdin.inspection.graphrag.v2.query;

import com.gdin.inspection.graphrag.v2.query.context.TableRecords;

import java.util.Map;

public class SearchResult {

    private final String response;

    /** 对齐 Python: context_data 是 dict[str, pd.DataFrame] */
    private final Map<String, TableRecords> contextData;

    /** 对齐 Python: context_text 是 context_chunks (CSV string) */
    private final String contextText;

    private final double completionTimeSeconds;

    private final int llmCalls;
    private final int promptTokens;
    private final int outputTokens;

    private final Map<String, Integer> llmCallsCategories;
    private final Map<String, Integer> promptTokensCategories;
    private final Map<String, Integer> outputTokensCategories;

    public SearchResult(
            String response,
            Map<String, TableRecords> contextData,
            String contextText,
            double completionTimeSeconds,
            int llmCalls,
            int promptTokens,
            int outputTokens,
            Map<String, Integer> llmCallsCategories,
            Map<String, Integer> promptTokensCategories,
            Map<String, Integer> outputTokensCategories
    ) {
        this.response = response;
        this.contextData = contextData;
        this.contextText = contextText;
        this.completionTimeSeconds = completionTimeSeconds;
        this.llmCalls = llmCalls;
        this.promptTokens = promptTokens;
        this.outputTokens = outputTokens;
        this.llmCallsCategories = llmCallsCategories;
        this.promptTokensCategories = promptTokensCategories;
        this.outputTokensCategories = outputTokensCategories;
    }

    public String getResponse() { return response; }
    public Map<String, TableRecords> getContextData() { return contextData; }
    public String getContextText() { return contextText; }
    public double getCompletionTimeSeconds() { return completionTimeSeconds; }

    public int getLlmCalls() { return llmCalls; }
    public int getPromptTokens() { return promptTokens; }
    public int getOutputTokens() { return outputTokens; }

    public Map<String, Integer> getLlmCallsCategories() { return llmCallsCategories; }
    public Map<String, Integer> getPromptTokensCategories() { return promptTokensCategories; }
    public Map<String, Integer> getOutputTokensCategories() { return outputTokensCategories; }
}
