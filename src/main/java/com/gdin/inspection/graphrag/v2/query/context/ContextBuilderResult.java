package com.gdin.inspection.graphrag.v2.query.context;

import java.util.Map;

public class ContextBuilderResult {

    private final String contextChunks;                 // CSV string
    private final Map<String, TableRecords> contextRecords;

    // basic_search 默认 0（对齐 Python）
    private final int llmCalls;
    private final int promptTokens;
    private final int outputTokens;

    public ContextBuilderResult(String contextChunks, Map<String, TableRecords> contextRecords) {
        this(contextChunks, contextRecords, 0, 0, 0);
    }

    public ContextBuilderResult(
            String contextChunks,
            Map<String, TableRecords> contextRecords,
            int llmCalls,
            int promptTokens,
            int outputTokens
    ) {
        this.contextChunks = contextChunks;
        this.contextRecords = contextRecords;
        this.llmCalls = llmCalls;
        this.promptTokens = promptTokens;
        this.outputTokens = outputTokens;
    }

    public String getContextChunks() { return contextChunks; }
    public Map<String, TableRecords> getContextRecords() { return contextRecords; }

    public int getLlmCalls() { return llmCalls; }
    public int getPromptTokens() { return promptTokens; }
    public int getOutputTokens() { return outputTokens; }
}
