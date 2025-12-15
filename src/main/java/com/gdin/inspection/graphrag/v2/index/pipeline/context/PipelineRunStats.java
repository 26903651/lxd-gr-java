package com.gdin.inspection.graphrag.v2.index.pipeline.context;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class PipelineRunStats {

    private final Map<String, Double> workflowSeconds = new ConcurrentHashMap<>();

    @Setter
    private double totalSeconds;
}
