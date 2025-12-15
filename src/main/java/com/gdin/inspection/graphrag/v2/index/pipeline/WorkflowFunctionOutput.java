package com.gdin.inspection.graphrag.v2.index.pipeline;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WorkflowFunctionOutput {
    Object result;
    @Builder.Default
    boolean stop = false;
}
