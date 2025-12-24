package com.gdin.inspection.graphrag.v2.index.pipeline;

import com.gdin.inspection.graphrag.v2.index.workflows.*;
import com.gdin.inspection.graphrag.v2.models.*;
import com.gdin.inspection.graphrag.v2.storage.GraphRagIndexStorage;
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
    private LoadPreviousIndexWorkflow loadPreviousIndexWorkflow;

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
                    ctx.get("max_gleanings"),
                    ctx.get("tuple_delimiter"),
                    ctx.get("record_delimiter"),
                    ctx.get("completion_delimiter"),
                    ctx.get("extraction_prompt"),
                    ctx.get("entity_types"),
                    ctx.get("entity_summary_max_words"),
                    ctx.get("relationship_summary_max_words"),
                    ctx.get("concurrent_requests")
            );
            ctx.put("entities", out.getEntities());
            ctx.put("relationships", out.getRelationships());
            ctx.put("raw_entities", out.getRawEntities());
            ctx.put("raw_relationships", out.getRawRelationships());
            return WorkflowFunctionOutput.builder().result("extract_graph_done").build();
        });

        // 2.5) persist_temp_graph
        factory.register("persist_temp_graph", (cfg, ctx) -> {
            persistIndexWorkflow.run(
                    GraphRagIndexStorage.SCOPE_MAIN,
                    null,
                    ctx.get("entities"),
                    ctx.get("relationships"),
                    null,
                    null,
                    null
            );

            return WorkflowFunctionOutput.builder().result("persist_temp_graph_done").build();
        });

        // 3) extract_covariates —— 对齐 Python: workflows/extract_covariates.py
        factory.register("extract_covariates", (cfg, ctx) -> {
            List<Covariate> covariates = extractCovariatesWorkflow.run(
                    ctx.get("claims_enabled"),
                    ctx.get("text_units"),
                    ctx.get("claims_description"),
                    ctx.get("claims_max_gleanings"),
                    ctx.get("claims_tuple_delimiter"),
                    ctx.get("claims_record_delimiter"),
                    ctx.get("claims_completion_delimiter"),
                    ctx.get("claims_entity_types"),
                    ctx.get("claims_extraction_prompt"),
                    ctx.get("concurrent_requests")
            );

            ctx.put("covariates", covariates);
            return WorkflowFunctionOutput.builder().result("extract_covariates_done").build();
        });

        // 3.5) persist_temp_covariates
        factory.register("persist_temp_covariates", (cfg, ctx) -> {
            persistIndexWorkflow.run(
                    GraphRagIndexStorage.SCOPE_MAIN,
                    null,
                    null,
                    null,
                    null,
                    null,
                    ctx.get("covariates")
            );

            return WorkflowFunctionOutput.builder().result("persist_temp_covariates_done").build();
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

        // 4.5) persist_temp_communities
        factory.register("persist_temp_communities", (cfg, ctx) -> {
            persistIndexWorkflow.run(
                    GraphRagIndexStorage.SCOPE_MAIN,
                    null,
                    null,
                    null,
                    ctx.get("communities"),
                    null,
                    null
            );

            return WorkflowFunctionOutput.builder().result("persist_temp_communities_done").build();
        });

        // 5) create_final_text_units
        factory.register("create_final_text_units", (cfg, ctx) -> {
            List<TextUnit> textUnit = createFinalTextUnitsWorkflow.run(
                    GraphRagIndexStorage.SCOPE_MAIN,
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
                    ctx.get("entities"),
                    ctx.get("relationships"),
                    ctx.get("communities"),
                    ctx.get("covariates"),
                    ctx.get("max_context_tokens"),
                    ctx.get("max_report_length"),
                    ctx.get("concurrent_requests")
            );

            ctx.put("community_reports", communityReports);
            return WorkflowFunctionOutput.builder().result("create_community_reports_done").build();
        });

        // 7) persist_index
        factory.register("persist_index", (cfg, ctx) -> {
            persistIndexWorkflow.run(
                    GraphRagIndexStorage.SCOPE_MAIN,
                    ctx.get("text_units"),
                    ctx.get("entities"),
                    ctx.get("relationships"),
                    ctx.get("communities"),
                    ctx.get("community_reports"),
                    ctx.get("covariates")
            );

            return WorkflowFunctionOutput.builder().result("persist_index_done").build();
        });

        // load_temp_graph
        factory.register("load_temp_graph", (cfg, ctx) -> {
            LoadPreviousIndexWorkflow.Result result = loadPreviousIndexWorkflow.run(GraphRagIndexStorage.SCOPE_MAIN, false, true, true, false, false, false);
            ctx.put("entities", result.getEntities());
            ctx.put("relationships", result.getRelationships());
            return WorkflowFunctionOutput.builder().result("load_temp_graph_done").build();
        });

        // load_temp_covariates
        factory.register("load_temp_covariates", (cfg, ctx) -> {
            LoadPreviousIndexWorkflow.Result result = loadPreviousIndexWorkflow.run(GraphRagIndexStorage.SCOPE_MAIN, false, false, false, false, false, true);
            ctx.put("covariates", result.getCovariates());
            return WorkflowFunctionOutput.builder().result("load_temp_covariates_done").build();
        });

        // load_temp_communities
        factory.register("load_temp_communities", (cfg, ctx) -> {
            LoadPreviousIndexWorkflow.Result result = loadPreviousIndexWorkflow.run(GraphRagIndexStorage.SCOPE_MAIN, false, false, false, true, false, false);
            ctx.put("communities", result.getCommunities());
            return WorkflowFunctionOutput.builder().result("load_temp_communities_done").build();
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

        // pipeline：对齐 Python Standard 的相对顺序（去掉 documents 的部分）
        factory.registerPipeline("standard-test", List.of(
                "load_input_documents",
                // "extract_graph",
                // "persist_temp_graph",
                "load_temp_graph",
                // "extract_covariates",
                // "persist_temp_covariates",
                "load_temp_covariates",
                // "create_communities",
                // "persist_temp_communities",
                "load_temp_communities",
                "create_final_text_units",
                "create_community_reports",
                "persist_index"
        ));
    }
}
