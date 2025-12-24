package com.gdin.inspection.graphrag.v2.index.pipeline;

import cn.hutool.core.collection.CollectionUtil;
import com.gdin.inspection.graphrag.v2.index.workflows.*;
import com.gdin.inspection.graphrag.v2.models.*;
import com.gdin.inspection.graphrag.v2.storage.GraphRagIndexStorage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StandardUpdatePipelineRegistrar {

    @Resource
    private LoadInputDocumentsWorkflow loadInputDocumentsWorkflow;

    @Resource
    private ExtractGraphWorkflow extractGraphWorkflow;

    @Resource
    private PersistIndexWorkflow persistIndexWorkflow;

    @Resource
    private ExtractCovariatesWorkflow extractCovariatesWorkflow;

    @Resource
    private CreateCommunitiesWorkflow createCommunitiesWorkflow;

    @Resource
    private CreateFinalTextUnitsWorkflow createFinalTextUnitsWorkflow;

    @Resource
    private CreateCommunityReportsWorkflow createCommunityReportsWorkflow;

    @Resource
    private LoadPreviousIndexWorkflow loadPreviousIndexWorkflow;

    @Resource
    private UpdateGraphWorkflow updateGraphWorkflow;

    @Resource
    private UpdateTextUnitsWorkflow updateTextUnitsWorkflow;

    @Resource
    private UpdateCovariatesWorkflow updateCovariatesWorkflow;

    @Resource
    private UpdateCommunitiesWorkflow updateCommunitiesWorkflow;

    @Resource
    private UpdateCommunityReportsWorkflow updateCommunityReportsWorkflow;

    @Resource
    private UpdateCleanStateWorkflow updateCleanStateWorkflow;

    @Resource
    public PipelineFactory<Object> factory;

    @PostConstruct
    public void init() {

        // 1) update_load_delta_documents
        factory.register("update_load_delta_documents", (cfg, ctx) -> {
            List<TextUnit> textUnits = loadInputDocumentsWorkflow.run(
                    ctx.get("update_document_ids")
            );
            boolean stop = false;
            if(CollectionUtil.isEmpty(textUnits)) stop = true;
            else ctx.put("delta_text_units", textUnits);
            return WorkflowFunctionOutput.builder().result("update_load_delta_documents_done").stop(stop).build();
        });

        // 2) update_extract_delta_graph
        factory.register("update_extract_delta_graph", (cfg, ctx) -> {
            ExtractGraphWorkflow.Result out = extractGraphWorkflow.run(
                    ctx.get("delta_text_units"),
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
            ctx.put("delta_entities", out.getEntities());
            ctx.put("delta_relationships", out.getRelationships());
            ctx.put("delta_raw_entities", out.getRawEntities());
            ctx.put("delta_raw_relationships", out.getRawRelationships());
            return WorkflowFunctionOutput.builder().result("update_extract_delta_graph_done").build();
        });

        // 2.5) update_persist_temp_delta_graph
        factory.register("update_persist_temp_delta_graph", (cfg, ctx) -> {
            persistIndexWorkflow.run(
                    GraphRagIndexStorage.SCOPE_DELTA,
                    null,
                    ctx.get("delta_entities"),
                    ctx.get("delta_relationships"),
                    null,
                    null,
                    null
            );
            return WorkflowFunctionOutput.builder().result("update_persist_temp_delta_graph_done").build();
        });

        // 3) update_extract_delta_covariates
        factory.register("update_extract_delta_covariates", (cfg, ctx) -> {
            List<Covariate> covariates = extractCovariatesWorkflow.run(
                    ctx.get("claims_enabled"),
                    ctx.get("delta_text_units"),
                    ctx.get("claims_description"),
                    ctx.get("claims_max_gleanings"),
                    ctx.get("claims_tuple_delimiter"),
                    ctx.get("claims_record_delimiter"),
                    ctx.get("claims_completion_delimiter"),
                    ctx.get("claims_entity_types"),
                    ctx.get("claims_extraction_prompt"),
                    ctx.get("concurrent_requests")
            );
            ctx.put("delta_covariates", covariates);
            return WorkflowFunctionOutput.builder().result("update_extract_delta_covariates_done").build();
        });

        // 3.5) update_persist_temp_delta_covariates
        factory.register("update_persist_temp_delta_covariates", (cfg, ctx) -> {
            persistIndexWorkflow.run(
                    GraphRagIndexStorage.SCOPE_DELTA,
                    null,
                    null,
                    null,
                    null,
                    null,
                    ctx.get("delta_covariates")
            );
            return WorkflowFunctionOutput.builder().result("update_persist_temp_delta_covariates_done").build();
        });

        // 4) update_create_delta_communities
        factory.register("update_create_delta_communities", (cfg, ctx) -> {
            List<Community> communities = createCommunitiesWorkflow.run(
                    ctx.get("delta_entities"),
                    ctx.get("delta_relationships"),
                    ctx.get("max_cluster_size"),
                    ctx.get("use_lcc"),
                    ctx.get("cluster_seed")
            );
            ctx.put("delta_communities", communities);
            return WorkflowFunctionOutput.builder().result("update_create_delta_communities_done").build();
        });

        // 4.5) update_persist_temp_delta_communities
        factory.register("update_persist_temp_delta_communities", (cfg, ctx) -> {
            persistIndexWorkflow.run(
                    GraphRagIndexStorage.SCOPE_DELTA,
                    null,
                    null,
                    null,
                    ctx.get("delta_communities"),
                    null,
                    null
            );
            return WorkflowFunctionOutput.builder().result("update_persist_temp_delta_communities_done").build();
        });

        // 5) update_create_delta_final_text_units
        factory.register("update_create_delta_final_text_units", (cfg, ctx) -> {
            List<TextUnit> textUnit = createFinalTextUnitsWorkflow.run(
                    GraphRagIndexStorage.SCOPE_DELTA,
                    ctx.get("delta_text_units"),
                    ctx.get("delta_entities"),
                    ctx.get("delta_relationships"),
                    ctx.get("delta_covariates")
            );
            ctx.put("delta_text_units", textUnit);
            return WorkflowFunctionOutput.builder().result("update_create_delta_final_text_units_done").build();
        });

        // 5.5) update_persist_temp_delta_text_units
        factory.register("update_persist_temp_delta_text_units", (cfg, ctx) -> {
            persistIndexWorkflow.run(
                    GraphRagIndexStorage.SCOPE_DELTA,
                    ctx.get("delta_text_units"),
                    null,
                    null,
                    null,
                    null,
                    null
            );
            return WorkflowFunctionOutput.builder().result("update_persist_temp_delta_text_units_done").build();
        });

        // 6) update_create_delta_community_reports
        factory.register("update_create_delta_community_reports", (cfg, ctx) -> {
            List<CommunityReport> communityReports = createCommunityReportsWorkflow.run(
                    ctx.get("delta_entities"),
                    ctx.get("delta_relationships"),
                    ctx.get("delta_communities"),
                    ctx.get("delta_covariates"),
                    ctx.get("max_context_tokens"),
                    ctx.get("max_report_length"),
                    ctx.get("concurrent_requests")
            );
            ctx.put("delta_community_reports", communityReports);
            return WorkflowFunctionOutput.builder().result("update_create_delta_community_reports_done").build();
        });

        // 7) update_persist_delta_index
        factory.register("update_persist_delta_index", (cfg, ctx) -> {
            persistIndexWorkflow.run(
                    GraphRagIndexStorage.SCOPE_DELTA,
                    ctx.get("delta_text_units"),
                    ctx.get("delta_entities"),
                    ctx.get("delta_relationships"),
                    ctx.get("delta_communities"),
                    ctx.get("delta_community_reports"),
                    ctx.get("delta_covariates")
            );
            return WorkflowFunctionOutput.builder().result("update_persist_delta_index_done").build();
        });

        // 8) update_load_previous_index
        factory.register("update_load_previous_index", (cfg, ctx) -> {
            LoadPreviousIndexWorkflow.Result result = loadPreviousIndexWorkflow.run(GraphRagIndexStorage.SCOPE_MAIN, true, true, true, true, true, true);
            ctx.put("old_text_units", result.getTextUnits());
            ctx.put("old_entities", result.getEntities());
            ctx.put("old_relationships", result.getRelationships());
            ctx.put("old_communities", result.getCommunities());
            ctx.put("old_community_reports", result.getCommunityReports());
            ctx.put("old_covariates", result.getCovariates());
            return WorkflowFunctionOutput.builder().result("update_load_previous_index_done").build();
        });

        // 9) update_merge_graph
        factory.register("update_merge_graph", (cfg, ctx) -> {
            UpdateGraphWorkflow.Result result = updateGraphWorkflow.run(
                    ctx.get("old_entities"),
                    ctx.get("delta_entities"),
                    ctx.get("old_relationships"),
                    ctx.get("delta_relationships"),
                    ctx.get("entity_summary_max_words"),
                    ctx.get("relationship_summary_max_words")
            );
            ctx.put("merged_entities", result.getMergedEntities());
            ctx.put("merged_relationships", result.getMergedRelationships());
            ctx.put("entity_id_mapping", result.getEntityIdMapping());
            return WorkflowFunctionOutput.builder().result("update_merge_graph_done").build();
        });

        // 9.5) update_persist_temp_merge_graph
        factory.register("update_persist_temp_merge_graph", (cfg, ctx) -> {
            persistIndexWorkflow.run(
                    GraphRagIndexStorage.SCOPE_MAIN,
                    null,
                    ctx.get("merged_entities"),
                    ctx.get("merged_relationships"),
                    null,
                    null,
                    null
            );
            return WorkflowFunctionOutput.builder().result("update_persist_temp_merge_graph_done").build();
        });

        // 10) update_merge_text_units
        factory.register("update_merge_text_units", (cfg, ctx) -> {
            List<TextUnit> mergedTextUnits = updateTextUnitsWorkflow.run(
                    ctx.get("old_text_units"),
                    ctx.get("delta_text_units"),
                    ctx.get("entity_id_mapping")
            );
            ctx.put("merged_text_units", mergedTextUnits);
            return WorkflowFunctionOutput.builder().result("update_merge_text_units_done").build();
        });

        // 10.5) update_persist_temp_merge_text_units
        factory.register("update_persist_temp_merge_text_units", (cfg, ctx) -> {
            persistIndexWorkflow.run(
                    GraphRagIndexStorage.SCOPE_MAIN,
                    ctx.get("merged_text_units"),
                    null,
                    null,
                    null,
                    null,
                    null
            );
            return WorkflowFunctionOutput.builder().result("update_persist_temp_merge_text_units_done").build();
        });

        // 11) update_merge_covariates
        factory.register("update_merge_covariates", (cfg, ctx) -> {
            List<Covariate> mergedCovariates = updateCovariatesWorkflow.run(
                    ctx.get("old_covariates"),
                    ctx.get("delta_covariates")
            );
            ctx.put("merged_covariates", mergedCovariates);
            return WorkflowFunctionOutput.builder().result("update_merge_covariates_done").build();
        });

        // 11.5) update_persist_temp_merge_covariates
        factory.register("update_persist_temp_merge_covariates", (cfg, ctx) -> {
            // 你 PersistIndexWorkflow 是覆盖逻辑，所以这里给 main 写 merged 即可
            persistIndexWorkflow.run(
                    GraphRagIndexStorage.SCOPE_MAIN,
                    null,
                    null,
                    null,
                    null,
                    null,
                    ctx.get("merged_covariates")
            );
            return WorkflowFunctionOutput.builder().result("update_persist_temp_merge_covariates_done").build();
        });

        // 12) update_merge_communities
        factory.register("update_merge_communities", (cfg, ctx) -> {
            UpdateCommunitiesWorkflow.Result out = updateCommunitiesWorkflow.run(
                    ctx.get("old_communities"),
                    ctx.get("delta_communities")
            );
            ctx.put("merged_communities", out.getMergedCommunities());
            ctx.put("community_id_mapping", out.getCommunityIdMapping());
            return WorkflowFunctionOutput.builder().result("update_merge_communities_done").build();
        });

        // 12.5) update_persist_temp_merge_communities
        factory.register("update_persist_temp_merge_communities", (cfg, ctx) -> {
            // 你 PersistIndexWorkflow 是覆盖逻辑，所以这里给 main 写 merged 即可
            persistIndexWorkflow.run(
                    GraphRagIndexStorage.SCOPE_MAIN,
                    null,
                    null,
                    null,
                    ctx.get("merged_communities"),
                    null,
                    null
            );
            return WorkflowFunctionOutput.builder().result("update_persist_temp_merge_communities_done").build();
        });

        // 13) update_community_merge_reports
        factory.register("update_community_merge_reports", (cfg, ctx) -> {
            List<CommunityReport> merged = updateCommunityReportsWorkflow.run(
                    ctx.get("old_community_reports"),
                    ctx.get("delta_community_reports"),
                    ctx.get("community_id_mapping")
            );
            ctx.put("merged_community_reports", merged);
            return WorkflowFunctionOutput.builder().result("update_community_merge_reports_done").build();
        });

        // 14) update_persist_index
        factory.register("update_persist_index", (cfg, ctx) -> {
            persistIndexWorkflow.run(
                    GraphRagIndexStorage.SCOPE_MAIN,
                    ctx.get("merged_text_units"),
                    ctx.get("merged_entities"),
                    ctx.get("merged_relationships"),
                    ctx.get("merged_communities"),
                    ctx.get("merged_community_reports"),
                    ctx.get("merged_covariates")
            );
            return WorkflowFunctionOutput.builder().result("update_persist_index_done").build();
        });

        // 15) update_clean_state
        factory.register("update_clean_state", (cfg, ctx) -> {
            updateCleanStateWorkflow.run(ctx);
            return WorkflowFunctionOutput.builder().result("update_clean_state_done").build();
        });


        // update_load_temp_graph
        factory.register("update_load_temp_delta_graph", (cfg, ctx) -> {
            LoadPreviousIndexWorkflow.Result result = loadPreviousIndexWorkflow.run(GraphRagIndexStorage.SCOPE_DELTA, false, true, true, false, false, false);
            ctx.put("delta_entities", result.getEntities());
            ctx.put("delta_relationships", result.getRelationships());
            return WorkflowFunctionOutput.builder().result("update_load_temp_delta_graph_done").build();
        });

        // update_load_temp_covariates
        factory.register("update_load_temp_delta_covariates", (cfg, ctx) -> {
            LoadPreviousIndexWorkflow.Result result = loadPreviousIndexWorkflow.run(GraphRagIndexStorage.SCOPE_DELTA, false, false, false, false, false, true);
            ctx.put("delta_covariates", result.getCovariates());
            return WorkflowFunctionOutput.builder().result("update_load_temp_delta_covariates_done").build();
        });

        // update_load_temp_communities
        factory.register("update_load_temp_delta_communities", (cfg, ctx) -> {
            LoadPreviousIndexWorkflow.Result result = loadPreviousIndexWorkflow.run(GraphRagIndexStorage.SCOPE_DELTA, false, false, false, true, false, false);
            ctx.put("delta_communities", result.getCommunities());
            return WorkflowFunctionOutput.builder().result("update_load_temp_delta_communities_done").build();
        });

        // update_load_temp_delta_text_units
        factory.register("update_load_temp_delta_text_units", (cfg, ctx) -> {
            LoadPreviousIndexWorkflow.Result result = loadPreviousIndexWorkflow.run(GraphRagIndexStorage.SCOPE_DELTA, true, false, false, false, false, false);
            ctx.put("delta_text_units", result.getTextUnits());
            return WorkflowFunctionOutput.builder().result("update_load_temp_delta_text_units_done").build();
        });

        // update_load_temp_delta_index
        factory.register("update_load_temp_delta_index", (cfg, ctx) -> {
            LoadPreviousIndexWorkflow.Result result = loadPreviousIndexWorkflow.run(GraphRagIndexStorage.SCOPE_DELTA, true, true, true, true, true, true);
            ctx.put("delta_text_units", result.getTextUnits());
            ctx.put("delta_entities", result.getEntities());
            ctx.put("delta_relationships", result.getRelationships());
            ctx.put("delta_communities", result.getCommunities());
            ctx.put("delta_community_reports", result.getCommunityReports());
            ctx.put("delta_covariates", result.getCovariates());
            return WorkflowFunctionOutput.builder().result("update_load_temp_delta_index_done").build();
        });



        factory.registerPipeline("standard_update", List.of(
                "update_load_delta_documents",
                "update_extract_delta_graph",
                "update_extract_delta_covariates",
                "update_create_delta_communities",
                "update_create_delta_final_text_units",
                "update_create_delta_community_reports",
                "update_load_previous_index",
                "update_merge_graph",
                "update_merge_text_units",
                "update_merge_covariates",
                "update_merge_communities",
                "update_community_merge_reports",
                "update_persist_index",
                "update_clean_state"
        ));

        factory.registerPipeline("standard_update-test", List.of(
                "update_load_delta_documents",
                // "update_extract_delta_graph",
                // "update_persist_temp_delta_graph",
                "update_load_temp_delta_graph",
                "update_extract_delta_covariates",
                "update_persist_temp_delta_covariates",
                // "update_load_temp_delta_covariates",
                // "update_create_delta_communities",
                // "update_persist_temp_delta_communities",
                "update_load_temp_delta_communities",
                "update_create_delta_final_text_units",
                "update_persist_temp_delta_text_units",
                // "update_load_temp_delta_text_units",
                "update_create_delta_community_reports",
                "update_persist_delta_index",
                // "update_load_temp_delta_index",
                "update_load_previous_index",
                "update_merge_graph",
                "update_persist_temp_merge_graph",
                "update_merge_text_units",
                "update_persist_temp_merge_text_units",
                "update_merge_covariates",
                "update_persist_temp_merge_covariates",
                "update_merge_communities",
                "update_persist_temp_merge_communities",
                "update_community_merge_reports",
                "update_persist_index",
                "update_clean_state"
        ));
    }
}
