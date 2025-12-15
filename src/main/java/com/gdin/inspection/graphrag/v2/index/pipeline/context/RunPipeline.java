package com.gdin.inspection.graphrag.v2.index.pipeline.context;

import com.gdin.inspection.graphrag.v2.index.pipeline.Pipeline;
import com.gdin.inspection.graphrag.v2.index.pipeline.WorkflowFunctionOutput;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class RunPipeline<C> {

    public List<PipelineRunResult> run(Pipeline<C> pipeline, C config, PipelineRunContext context) {
        long start = System.nanoTime();
        List<PipelineRunResult> results = new ArrayList<>();
        String last = "<startup>";

        try {
            for (Pipeline.Step<C> step : pipeline) {
                last = step.getName();
                long t0 = System.nanoTime();

                WorkflowFunctionOutput out = step.getFn().run(config, context);

                double sec = (System.nanoTime() - t0) / 1_000_000_000.0;
                context.getStats().getWorkflowSeconds().put(last, sec);

                results.add(PipelineRunResult.builder()
                        .workflow(last)
                        .result(out == null ? null : out.getResult())
                        .context(context)
                        .errors(null)
                        .build());

                if (out != null && out.isStop()) {
                    log.info("Pipeline halted by workflow request: {}", last);
                    break;
                }
            }

            context.getStats().setTotalSeconds((System.nanoTime() - start) / 1_000_000_000.0);
            return results;

        } catch (Exception e) {
            log.error("error running workflow {}", last, e);
            results.add(PipelineRunResult.builder()
                    .workflow(last)
                    .result(null)
                    .context(context)
                    .errors(List.of(e))
                    .build());
            context.getStats().setTotalSeconds((System.nanoTime() - start) / 1_000_000_000.0);
            return results;
        }
    }
}
