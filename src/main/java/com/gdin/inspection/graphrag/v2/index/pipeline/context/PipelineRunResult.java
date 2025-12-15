package com.gdin.inspection.graphrag.v2.index.pipeline.context;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class PipelineRunResult {
    String workflow;
    Object result;
    PipelineRunContext context;
    List<Exception> errors;
}
