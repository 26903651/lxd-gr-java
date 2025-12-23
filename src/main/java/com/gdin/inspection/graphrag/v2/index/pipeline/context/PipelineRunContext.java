package com.gdin.inspection.graphrag.v2.index.pipeline.context;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

@Getter
public class PipelineRunContext {

    private final PipelineRunStats stats = new PipelineRunStats();
    private final Map<String, Object> state = new ConcurrentHashMap<>();

    public void put(String key, Object value) { state.put(key, value); }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) { return (T) state.get(key); }

    public Set<String> keySet() {
        return state.keySet();
    }

    public int size() {
        return state.size();
    }

    public Object remove(Object key) {
        return state.remove(key);
    }
}
