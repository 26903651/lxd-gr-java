package com.gdin.inspection.graphrag.assistant;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

/**
 * 普通的对话助手
 */
public interface CommonAssistant {

    String chat(@MemoryId String memoryId, @UserMessage String prompt);

    TokenStream streamChat(@MemoryId String memoryId, @UserMessage String prompt);
}
