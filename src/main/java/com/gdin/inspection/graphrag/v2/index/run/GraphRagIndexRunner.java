package com.gdin.inspection.graphrag.v2.index.run;

import com.gdin.inspection.graphrag.v2.index.pipeline.Pipeline;
import com.gdin.inspection.graphrag.v2.index.pipeline.PipelineFactory;
import com.gdin.inspection.graphrag.v2.index.pipeline.context.PipelineRunContext;
import com.gdin.inspection.graphrag.v2.index.pipeline.context.RunPipeline;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GraphRagIndexRunner {

    @Resource
    private PipelineFactory<Object> factory;

    public List<?> runStandard(GraphRagIndexRunRequest req) {
        PipelineRunContext ctx = new PipelineRunContext();

        // === 按你 DefaultPipelineRegistrar 里 ctx.get(...) 读取的 key 填 ===
        ctx.put("document_ids", req.documentIds());
        ctx.put("entity_types", req.entityTypes());
        ctx.put("entity_summary_max_words", req.entitySummaryMaxWords());
        ctx.put("relationship_summary_max_words", req.relationshipSummaryMaxWords());

        ctx.put("max_cluster_size", req.maxClusterSize());
        ctx.put("use_lcc", req.useLcc());
        ctx.put("cluster_seed", req.clusterSeed());

        ctx.put("max_report_length", req.maxReportLength());

        // covariates 目前你还没抽取，就让它缺省为 null（create_final_text_units 会处理）
        ctx.put("covariates", null);

        Pipeline<Object> pipeline = factory.createPipeline("standard");
        return new RunPipeline<>().run(pipeline, null, ctx);
    }

    public record GraphRagIndexRunRequest(
            List<String> documentIds,
            String entityTypes,
            Integer entitySummaryMaxWords,
            Integer relationshipSummaryMaxWords,
            Integer maxClusterSize,
            Boolean useLcc,
            Integer clusterSeed,
            Integer maxReportLength
    ) {}
}
