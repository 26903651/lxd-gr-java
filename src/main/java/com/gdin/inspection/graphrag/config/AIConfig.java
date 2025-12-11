package com.gdin.inspection.graphrag.config;


import com.gdin.inspection.graphrag.assistant.CommonAssistant;
import com.gdin.inspection.graphrag.assistant.ThinkAssistant;
import com.gdin.inspection.graphrag.store.MapChatMemoryStore;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.community.model.dashscope.QwenChatRequestParameters;
import dev.langchain4j.community.model.dashscope.QwenStreamingChatModel;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AIConfig {
    @Resource
    private MapChatMemoryStore mapChatMemoryStore;

    private ChatModel buildChatModel(boolean thinking) {
        QwenChatRequestParameters qwenChatRequestParameters = QwenChatRequestParameters.builder()
                .enableThinking(thinking)
                .build();
        return QwenChatModel.builder()
                    .baseUrl("https://dashscope.aliyuncs.com/api/v1")
                    .defaultRequestParameters(qwenChatRequestParameters)
                    .modelName("qwen3-32b")
                    .apiKey("sk-bd99778012bc45e69248a496dccca05f")
                    .temperature(0.8f)
                    .build();
    }

    private StreamingChatModel buildStreamingChatModel(boolean thinking) {
        QwenChatRequestParameters qwenChatRequestParameters = QwenChatRequestParameters.builder()
                .enableThinking(thinking)
                .build();
        return QwenStreamingChatModel.builder()
                    .baseUrl("https://dashscope.aliyuncs.com/api/v1")
                    .defaultRequestParameters(qwenChatRequestParameters)
                    .modelName("qwen3-32b")
                    .apiKey("sk-bd99778012bc45e69248a496dccca05f")
                    .temperature(0.8f)
                    .build();
    }

    @Bean("thinkCm")
    public ChatModel thinkChatModel() {
        return buildChatModel(true);
    }

    @Bean("commonCm")
    public ChatModel commonChatModel() {
        return buildChatModel(false);
    }

    @Bean("thinkScm")
    public StreamingChatModel thinkStreamingChatModel() {
        return buildStreamingChatModel(true);
    }

    @Bean("commonScm")
    public StreamingChatModel commonStreamingChatModel() {
        return buildStreamingChatModel(false);
    }

    @Bean("thinkAssistant")
    public ThinkAssistant thinkAssistant(@Qualifier("thinkCm") ChatModel chatModel,
                                         @Qualifier("thinkScm") StreamingChatModel streamingChatModel) {
        return AiServices.builder(ThinkAssistant.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .maxMessages(100)
                        .id(memoryId)
                        .chatMemoryStore(mapChatMemoryStore)
                        .build()
                )
                .build();
    }

    @Bean("commonAssistant")
    public CommonAssistant commonAssistant(@Qualifier("commonCm") ChatModel chatModel,
                                           @Qualifier("commonScm") StreamingChatModel streamingChatModel) {
        return AiServices.builder(CommonAssistant.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .maxMessages(100)
                        .id(memoryId)
                        .chatMemoryStore(mapChatMemoryStore)
                        .build()
                )
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        return OllamaEmbeddingModel.builder()
                .baseUrl("http://192.168.1.31:11434/")
                .modelName("quentinz/bge-large-zh-v1.5:latest")
                .build();
    }
}
