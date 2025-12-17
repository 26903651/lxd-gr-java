package com.gdin.inspection.graphrag.v2.index.workflows;

import com.gdin.inspection.graphrag.v2.index.pipeline.context.PipelineRunContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class UpdateCleanStateWorkflow {

    public void run(PipelineRunContext context) {
        List<String> keysToDelete = new ArrayList<>();
        for (String k : context.getState().keySet()) {
            if (k != null && k.startsWith("incremental_update_")) {
                keysToDelete.add(k);
            }
        }
        for (String k : keysToDelete) {
            context.getState().remove(k);
        }
    }
}