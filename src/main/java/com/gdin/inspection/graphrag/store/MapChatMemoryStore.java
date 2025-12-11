package com.gdin.inspection.graphrag.store;

import dev.langchain4j.data.message.ChatMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class MapChatMemoryStore implements EditableChatMemoryStore {
    private Map<Object, List<ChatMessage>> map = new HashMap<>();
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        return map.computeIfAbsent(memoryId, k -> new ArrayList<>());
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> list) {
        map.put(memoryId, list);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        map.remove(memoryId);
    }
}
