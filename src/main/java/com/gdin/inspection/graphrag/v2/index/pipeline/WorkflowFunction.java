package com.gdin.inspection.graphrag.v2.index.pipeline;

import com.gdin.inspection.graphrag.v2.index.pipeline.context.PipelineRunContext;

public interface WorkflowFunction<C> {
    WorkflowFunctionOutput run(C config, PipelineRunContext context) throws Exception;
}
