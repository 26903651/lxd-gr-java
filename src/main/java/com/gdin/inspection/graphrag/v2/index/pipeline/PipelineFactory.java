package com.gdin.inspection.graphrag.v2.index.pipeline;

import java.util.*;

public class PipelineFactory<C> {

    private final Map<String, WorkflowFunction<C>> workflows = new HashMap<>();
    private final Map<String, List<String>> pipelines = new HashMap<>();

    public void register(String name, WorkflowFunction<C> workflow) {
        workflows.put(name, workflow);
    }

    public void registerPipeline(String name, List<String> workflowNames) {
        pipelines.put(name, workflowNames);
    }

    public Pipeline<C> createPipeline(String pipelineName) {
        List<String> names = pipelines.getOrDefault(pipelineName, List.of());
        Pipeline<C> pipeline = new Pipeline<>();
        for (String n : names) {
            WorkflowFunction<C> wf = workflows.get(n);
            if (wf == null) {
                throw new IllegalStateException("Workflow not registered: " + n);
            }
            pipeline.add(n, wf);
        }
        return pipeline;
    }
}
