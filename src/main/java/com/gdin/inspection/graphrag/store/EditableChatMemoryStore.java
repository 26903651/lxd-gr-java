package com.gdin.inspection.graphrag.store;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

import java.util.ArrayList;
import java.util.List;

public interface EditableChatMemoryStore extends ChatMemoryStore {
    /**
     * 删除最后几次的记忆
     * @param count
     * @return 删除的记忆
     */
    default List<ChatMessage> popMessages(Object memoryId, int count){
        List<ChatMessage> messages = getMessages(memoryId);
        List<ChatMessage> removedMessages = new ArrayList<>();
        if (messages.size() >= count) {
            for (int i = 0; i < count; i++) {
                removedMessages.add(messages.remove(messages.size()-1));
            }
        }
        updateMessages(memoryId, messages);
        return removedMessages;
    }

    /**
     * 添加新的记忆
     * @param memoryId
     * @param messages
     */
    default void addMessage(Object memoryId, List<ChatMessage> messages){
        List<ChatMessage> allMessages = getMessages(memoryId);
        allMessages.addAll(messages);
        updateMessages(memoryId, allMessages);
    }
}
