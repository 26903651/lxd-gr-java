package com.gdin.inspection.graphrag.v2.index.pipeline;

import com.gdin.inspection.graphrag.v2.index.workflows.*;
import com.gdin.inspection.graphrag.v2.models.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StandardPipelineRegistrar {

    @Resource
    private LoadInputDocumentsWorkflow loadInputDocumentsWorkflow;
    @Resource
    private ExtractGraphWorkflow extractGraphWorkflow;
    @Resource
    private ExtractCovariatesWorkflow extractCovariatesWorkflow;
    @Resource
    private CreateCommunitiesWorkflow createCommunitiesWorkflow;
    @Resource
    private CreateFinalTextUnitsWorkflow createFinalTextUnitsWorkflow;
    @Resource
    private CreateCommunityReportsWorkflow createCommunityReportsWorkflow;
    @Resource
    private PersistIndexWorkflow persistIndexWorkflow;

    @Resource
    public PipelineFactory<Object> factory;

    @PostConstruct
    public void init() {

        // 1) load_input_documents
        factory.register("load_input_documents", (cfg, ctx) -> {
            List<TextUnit> textUnits = loadInputDocumentsWorkflow.run(
                    ctx.get("document_ids")
            );
            ctx.put("text_units", textUnits);
            return WorkflowFunctionOutput.builder().result("load_input_documents_done").build();
        });

        // 2) extract_graph
        factory.register("extract_graph", (cfg, ctx) -> {
            ExtractGraphWorkflow.Result out = extractGraphWorkflow.run(
                    ctx.get("text_units"),
                    ctx.get("entity_types"),
                    ctx.get("entity_summary_max_words"),
                    ctx.get("relationship_summary_max_words")
            );
            ctx.put("entities", out.getEntities());
            ctx.put("relationships", out.getRelationships());
            ctx.put("raw_entities", out.getRawEntities());
            ctx.put("raw_relationships", out.getRawRelationships());
            return WorkflowFunctionOutput.builder().result("extract_graph_done").build();
        });

        // 3) extract_covariates —— 对齐 Python: workflows/extract_covariates.py
        factory.register("extract_covariates", (cfg, ctx) -> {
            // enabled：对齐 Python config.extract_claims.enabled
            Boolean extractClaimsEnabled = ctx.get("extract_claims_enabled");
            if (extractClaimsEnabled == null) extractClaimsEnabled = Boolean.FALSE;
            // claim_description：对齐 strategy_config["claim_description"]
            String claimDescription = ctx.get("extract_claims_claim_description");
            if (claimDescription == null) claimDescription = ctx.get("claim_description");
            // max_gleanings / delimiters：对齐 strategy_config
            Integer maxGleanings = ctx.get("extract_claims_max_gleanings");
            if (maxGleanings == null) maxGleanings = ctx.get("max_gleanings");
            String tupleDelimiter = ctx.get("extract_claims_tuple_delimiter");
            if (tupleDelimiter == null) tupleDelimiter = ctx.get("tuple_delimiter");
            String recordDelimiter = ctx.get("extract_claims_record_delimiter");
            if (recordDelimiter == null) recordDelimiter = ctx.get("record_delimiter");
            String completionDelimiter = ctx.get("extract_claims_completion_delimiter");
            if (completionDelimiter == null) completionDelimiter = ctx.get("completion_delimiter");
            // entity_types：Python 允许 None -> DEFAULT_ENTITY_TYPES，这里也允许不传
            List<String> entityTypesOrNames = ctx.get("extract_claims_entity_types");
            if (entityTypesOrNames == null) entityTypesOrNames = ctx.get("entity_types"); // 如果你没单独放，就回退复用
            // extraction_prompt：Python strategy 可覆盖（你 Java 侧目前还没做 resolved_strategy，这里先透传）
            String extractionPrompt = ctx.get("extract_claims_extraction_prompt");
            if (extractionPrompt == null) extractionPrompt = ctx.get("extraction_prompt");

            List<Covariate> covariates = extractCovariatesWorkflow.run(
                    extractClaimsEnabled,
                    ctx.get("text_units"),
                    claimDescription,
                    maxGleanings,
                    tupleDelimiter,
                    recordDelimiter,
                    completionDelimiter,
                    entityTypesOrNames,
                    extractionPrompt
            );

            ctx.put("covariates", covariates);
            return WorkflowFunctionOutput.builder().result("extract_covariates_done").build();
        });

        // 4) create_communities
        factory.register("create_communities", (cfg, ctx) -> {
            List<Community> communities = createCommunitiesWorkflow.run(
                    ctx.get("entities"),
                    ctx.get("relationships"),
                    ctx.get("max_cluster_size"),
                    ctx.get("use_lcc"),
                    ctx.get("cluster_seed")
            );

            ctx.put("communities", communities);
            return WorkflowFunctionOutput.builder().result("create_communities_done").build();
        });

        // 5) create_final_text_units
        factory.register("create_final_text_units", (cfg, ctx) -> {
            List<TextUnit> textUnit = createFinalTextUnitsWorkflow.run(
                    ctx.get("text_units"),
                    ctx.get("entities"),
                    ctx.get("relationships"),
                    ctx.get("covariates")
            );

            ctx.put("text_units", textUnit);
            return WorkflowFunctionOutput.builder().result("create_final_text_units_done").build();
        });

        // 6) create_community_reports
        factory.register("create_community_reports", (cfg, ctx) -> {
            List<CommunityReport> communityReports = createCommunityReportsWorkflow.run(
                    ctx.get("communities"),
                    ctx.get("entities"),
                    ctx.get("relationships"),
                    ctx.get("text_units"),
                    ctx.get("max_report_length")
            );

            ctx.put("community_reports", communityReports);
            return WorkflowFunctionOutput.builder().result("create_community_reports_done").build();
        });

        // 7) persist_index
        factory.register("persist_index", (cfg, ctx) -> {
            persistIndexWorkflow.run(
                    ctx.get("text_units"),
                    ctx.get("entities"),
                    ctx.get("relationships"),
                    ctx.get("communities"),
                    ctx.get("community_reports"),
                    ctx.get("covariates")
            );

            return WorkflowFunctionOutput.builder().result("persist_index_done").build();
        });

        // pipeline：对齐 Python Standard 的相对顺序（去掉 documents 的部分）
        factory.registerPipeline("standard", List.of(
                "load_input_documents",
                "extract_graph",
                "extract_covariates",
                "create_communities",
                "create_final_text_units",
                "create_community_reports",
                "persist_index"
        ));
    }
}
