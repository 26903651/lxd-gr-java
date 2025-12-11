package com.gdin.inspection.graphrag.service;

import com.gdin.inspection.graphrag.store.MapChatMemoryStore;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class AssistantGenerator {
    @Resource
    @Qualifier("thinkCm")
    private ChatModel thinkChatModel;

    @Resource
    @Qualifier("thinkScm")
    private StreamingChatModel thinkStreamingChatModel;

    @Resource
    @Qualifier("commonCm")
    private ChatModel commonChatModel;

    @Resource
    @Qualifier("commonScm")
    private StreamingChatModel commonStreamingChatModel;

    @Resource
    private MapChatMemoryStore mapChatMemoryStore;

    public <T> T createTempAssistant(@NonNull Class<T> clazz) {
        return createTempAssistant(clazz, null);
    }

    /**
     * 创建临时会话的AI助手实例（使用内存窗口模式）
     * @param clazz 需要创建的AI服务接口类型
     * @param systemMessage 系统提示信息（可选）
     * @param <T> 泛型类型参数
     * @return 配置完成的临时AI服务实例
     */
    public <T> T createTempAssistant(@NonNull Class<T> clazz, String systemMessage) {
        // 创建基于消息窗口的临时聊天内存
        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(100);
        return createTempAssistant(clazz, chatMemory, systemMessage);
    }

    /**
     * 创建自定义内存的临时AI助手实例
     * @param clazz 需要创建的AI服务接口类型
     * @param chatMemory 预配置的聊天内存实例
     * @param systemMessage 系统提示信息（可选）
     * @param <T> 泛型类型参数
     * @return 配置完成的临时AI服务实例
     */
    public <T> T createTempAssistant(@NonNull Class<T> clazz, @NonNull ChatMemory chatMemory, String systemMessage) {
        // 创建固定内存提供者（忽略memoryId参数）
        ChatMemoryProvider chatMemoryProvider = memoryId -> chatMemory;
        return createAssistant(clazz, chatMemoryProvider, systemMessage);
    }

    /**
     * 核心助手构建方法（根据类名选择模型配置）
     * @param clazz 需要创建的AI服务接口类型
     * @param chatMemoryProvider 聊天内存提供者实例
     * @param systemMessage 系统提示信息（可选）
     * @param <T> 泛型类型参数
     * @return 完整配置的AI服务实例
     */
    public <T> T createAssistant(@NonNull Class<T> clazz, @NonNull ChatMemoryProvider chatMemoryProvider, String systemMessage) {
        // 根据接口类名选择对应的模型配置
        ChatModel chatModel;
        StreamingChatModel streamingChatModel;
        if(clazz.getName().toLowerCase().contains("think")){
            // 使用思考型模型配置
            chatModel = thinkChatModel;
            streamingChatModel = thinkStreamingChatModel;
        }
        else{
            // 使用通用型模型配置
            chatModel = commonChatModel;
            streamingChatModel = commonStreamingChatModel;
        }
        return createAssistant(clazz, chatModel, streamingChatModel, chatMemoryProvider, systemMessage);
    }

    /**
     * 最终构建方法（组合所有配置参数）
     * @param clazz 需要创建的AI服务接口类型
     * @param chatModel 聊天语言模型实例
     * @param streamingChatModel 流式聊天语言模型实例
     * @param chatMemoryProvider 聊天内存提供者实例
     * @param systemMessage 系统提示信息（可选）
     * @param <T> 泛型类型参数
     * @return 最终构建完成的AI服务实例
     */
    public <T> T createAssistant(@NonNull Class<T> clazz,
                                 @NonNull ChatModel chatModel,
                                 @NonNull StreamingChatModel streamingChatModel,
                                 @NonNull ChatMemoryProvider chatMemoryProvider,
                                 String systemMessage) {
        // 使用LangChain4j的AiServices构建器组合所有组件
        AiServices<T> builder = AiServices.builder(clazz)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .chatMemoryProvider(chatMemoryProvider);

        // 可选配置系统消息
        if (systemMessage != null) builder.systemMessageProvider(memoryId -> systemMessage);

        return builder.build();
    }
}
