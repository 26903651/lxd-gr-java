package com.gdin.inspection.graphrag.v2.index.pipeline.context;

import lombok.Getter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class PipelineRunContext {

    private final PipelineRunStats stats = new PipelineRunStats();
    private final Map<String, Object> state = new ConcurrentHashMap<>();

    public void put(String key, Object value) { state.put(key, value); }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) { return (T) state.get(key); }
}
