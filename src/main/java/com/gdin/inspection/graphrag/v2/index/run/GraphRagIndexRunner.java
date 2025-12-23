package com.gdin.inspection.graphrag.v2.index.run;

import com.gdin.inspection.graphrag.config.properties.GraphProperties;
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
    private GraphProperties graphProperties;

    @Resource
    private PipelineFactory<Object> factory;

    public List<?> runStandard(List<String> documentIds) {
        return runStandard(documentIds, "standard");
    }

    public List<?> runStandard(List<String> documentIds, String piplineName) {
        PipelineRunContext ctx = new PipelineRunContext();

        GraphProperties.Index.Standard standard = graphProperties.getIndex().getStandard();
        ctx.put("concurrent_requests", standard.getConcurrentRequests());
        // ==============load_input_documents==============
        ctx.put("document_ids", documentIds);
        // ==============extract_graph==============
        ctx.put("max_gleanings", standard.getMaxGleanings());
        ctx.put("tuple_delimiter", standard.getTupleDelimiter());
        ctx.put("record_delimiter", standard.getRecordDelimiter());
        ctx.put("completion_delimiter", standard.getCompletionDelimiter());
        if(standard.getExtractionPrompt()!=null) ctx.put("extraction_prompt", standard.getExtractionPrompt());
        ctx.put("entity_types", standard.getEntityTypes());
        ctx.put("entity_summary_max_words", standard.getEntitySummaryMaxWords());
        ctx.put("relationship_summary_max_words", standard.getRelationshipSummaryMaxWords());
        // ==============extract_covariates==============
        ctx.put("claims_enabled", standard.getClaimsEnabled());
        ctx.put("claims_description", standard.getClaimsDescription());
        ctx.put("claims_max_gleanings", standard.getClaimsMaxGleanings());
        ctx.put("claims_tuple_delimiter", standard.getClaimsTupleDelimiter());
        ctx.put("claims_record_delimiter", standard.getClaimsRecordDelimiter());
        ctx.put("claims_completion_delimiter", standard.getClaimsCompletionDelimiter());
        ctx.put("claims_entity_types", standard.getClaimsEntityTypes());
        if(standard.getClaimsExtractionPrompt()!=null) ctx.put("claims_extraction_prompt", standard.getClaimsExtractionPrompt());
        // ==============extract_covariates==============
        ctx.put("max_cluster_size", standard.getMaxClusterSize());
        ctx.put("use_lcc", standard.getUseLcc());
        ctx.put("cluster_seed", standard.getClusterSeed());
        // ==============create_community_reports==============
        ctx.put("max_context_tokens", standard.getMaxContextTokens());
        ctx.put("max_report_length", standard.getMaxReportLength());

        Pipeline<Object> pipeline = factory.createPipeline(piplineName);
        return new RunPipeline<>().run(pipeline, null, ctx);
    }

    public List<?> runStandardUpdate(List<String> documentIds) {
        return runStandardUpdate(documentIds, "standard_update");
    }

    public List<?> runStandardUpdate(List<String> documentIds, String piplineName) {
        PipelineRunContext ctx = new PipelineRunContext();

        GraphProperties.Index.StandardUpdate standardUpdate = graphProperties.getIndex().getStandardUpdate();
        ctx.put("concurrent_requests", standardUpdate.getConcurrentRequests());
        // ==============update_load_delta_documents==============
        ctx.put("update_document_ids", documentIds);
        // ==============extract_graph==============
        ctx.put("max_gleanings", standardUpdate.getMaxGleanings());
        ctx.put("tuple_delimiter", standardUpdate.getTupleDelimiter());
        ctx.put("record_delimiter", standardUpdate.getRecordDelimiter());
        ctx.put("completion_delimiter", standardUpdate.getCompletionDelimiter());
        if(standardUpdate.getExtractionPrompt()!=null) ctx.put("extraction_prompt", standardUpdate.getExtractionPrompt());
        ctx.put("entity_types", standardUpdate.getEntityTypes());
        ctx.put("entity_summary_max_words", standardUpdate.getEntitySummaryMaxWords());
        ctx.put("relationship_summary_max_words", standardUpdate.getRelationshipSummaryMaxWords());
        // ==============extract_covariates==============
        ctx.put("claims_enabled", standardUpdate.getClaimsEnabled());
        ctx.put("claims_description", standardUpdate.getClaimsDescription());
        ctx.put("claims_max_gleanings", standardUpdate.getClaimsMaxGleanings());
        ctx.put("claims_tuple_delimiter", standardUpdate.getClaimsTupleDelimiter());
        ctx.put("claims_record_delimiter", standardUpdate.getClaimsRecordDelimiter());
        ctx.put("claims_completion_delimiter", standardUpdate.getClaimsCompletionDelimiter());
        ctx.put("claims_entity_types", standardUpdate.getClaimsEntityTypes());
        if(standardUpdate.getClaimsExtractionPrompt()!=null) ctx.put("claims_extraction_prompt", standardUpdate.getClaimsExtractionPrompt());
        // ==============extract_covariates==============
        ctx.put("max_cluster_size", standardUpdate.getMaxClusterSize());
        ctx.put("use_lcc", standardUpdate.getUseLcc());
        ctx.put("cluster_seed", standardUpdate.getClusterSeed());
        // ==============create_community_reports==============
        ctx.put("max_context_tokens", standardUpdate.getMaxContextTokens());
        ctx.put("max_report_length", standardUpdate.getMaxReportLength());

        Pipeline<Object> pipeline = factory.createPipeline(piplineName);
        return new RunPipeline<>().run(pipeline, null, ctx);
    }
}
