package com.gdin.inspection.graphrag.v2.index.pipeline;

import cn.hutool.core.collection.CollectionUtil;
import com.gdin.inspection.graphrag.v2.index.update.CommunityUpdateUtils;
import com.gdin.inspection.graphrag.v2.index.workflows.*;
import com.gdin.inspection.graphrag.v2.models.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class StandardUpdatePipelineRegistrar {

    @Resource
    private LoadPreviousIndexWorkflow loadPreviousIndexWorkflow;
    @Resource
    private LoadUpdateDocumentsWorkflow loadUpdateDocumentsWorkflow;
    @Resource
    private ExtractGraphWorkflow extractGraphWorkflow;
    @Resource
    private UpdateCommunitiesWorkflow updateCommunitiesWorkflow;
    @Resource
    private UpdateCommunityReportsWorkflow updateCommunityReportsWorkflow;
    @Resource
    private UpdateEntitiesRelationshipsWorkflow updateEntitiesRelationshipsWorkflow;
    @Resource
    private UpdateTextUnitsWorkflow updateTextUnitsWorkflow;
    @Resource
    private UpdateCovariatesWorkflow updateCovariatesWorkflow;
    @Resource
    private UpdateTextEmbeddingsWorkflow updateTextEmbeddingsWorkflow;
    @Resource
    private UpdateCleanStateWorkflow updateCleanStateWorkflow;

    @Resource
    public PipelineFactory<Object> factory;

    @PostConstruct
    public void init() {

        // 1) load_previous_index
        factory.register("load_previous_index", (cfg, ctx) -> {
            LoadPreviousIndexWorkflow.Result result = loadPreviousIndexWorkflow.run();
            ctx.put("old_text_units", result.getTextUnits());
            ctx.put("old_entities", result.getEntities());
            ctx.put("old_relationships", result.getRelationships());
            ctx.put("old_communities", result.getCommunities());
            ctx.put("old_community_reports", result.getReports());
            ctx.put("old_covariates", result.getCovariates());
            return WorkflowFunctionOutput.builder().result("load_previous_index_done").build();
        });

        // 2) load_update_documents
        factory.register("load_update_documents", (cfg, ctx) -> {
            List<TextUnit> textUnits = loadUpdateDocumentsWorkflow.run(
                    ctx.get("update_document_ids")
            );
            boolean stop = false;
            if(CollectionUtil.isEmpty(textUnits)) stop = true;
            else ctx.put("delta_text_units", textUnits);
            return WorkflowFunctionOutput.builder().result("load_update_documents_done").stop(stop).build();
        });

        // 3) update_extract_graph
        factory.register("update_extract_graph", (cfg, ctx) -> {
            ExtractGraphWorkflow.Result out = extractGraphWorkflow.run(
                    ctx.get("text_units"),
                    ctx.get("max_gleanings"),
                    ctx.get("tuple_delimiter"),
                    ctx.get("record_delimiter"),
                    ctx.get("completion_delimiter"),
                    ctx.get("extraction_prompt"),
                    ctx.get("entity_types"),
                    ctx.get("entity_summary_max_words"),
                    ctx.get("relationship_summary_max_words")
            );
            ctx.put("entities", out.getEntities());
            ctx.put("relationships", out.getRelationships());
            ctx.put("raw_entities", out.getRawEntities());
            ctx.put("raw_relationships", out.getRawRelationships());
            return WorkflowFunctionOutput.builder().result("update_extract_graph_done").build();
        });

        // 4) update_entities_relationships
        factory.register("update_entities_relationships", (cfg, ctx) -> {
            UpdateEntitiesRelationshipsWorkflow.Result result = updateEntitiesRelationshipsWorkflow.run(
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
            return WorkflowFunctionOutput.builder().result("update_entities_relationships_done").build();
        });

        // 5) update_text_units
        factory.register("update_text_units", (cfg, ctx) -> {
            List<TextUnit> mergedTextUnits = updateTextUnitsWorkflow.run(
                    ctx.get("old_text_units"),
                    ctx.get("delta_text_units"),
                    ctx.get("entity_id_mapping")
            );
            ctx.put("merged_text_units", mergedTextUnits);
            return WorkflowFunctionOutput.builder().result("update_text_units_done").build();
        });

        // 6) update_covariates
        factory.register("update_covariates", (cfg, ctx) -> {
            updateCovariatesWorkflow.run(
                    ctx.get("old_covariates"),
                    ctx.get("delta_covariates")
            );
            return WorkflowFunctionOutput.builder().result("update_covariates_done").build();
        });

        // 6) update_communities
        factory.register("update_communities", (cfg, ctx) -> {
            List<Community> oldCommunities = ctx.get("old_communities");
            List<Community> deltaCommunities = ctx.get("communities");

            UpdateCommunitiesWorkflow.Result result = updateCommunitiesWorkflow.run(oldCommunities, deltaCommunities);

            // 用 merged 覆盖回主键名（后续 persist_index 用）
            ctx.put("communities", result.getMergedCommunities());
            ctx.put("community_id_mapping", result.getCommunityIdMapping());

            return WorkflowFunctionOutput.builder().result("update_communities_done").build();
        });

        // 7) update_community_reports
        factory.register("update_community_reports", (cfg, ctx) -> {
            List<CommunityReport> oldCommunityReports = ctx.get("old_community_reports");
            List<CommunityReport> deltaCommunityReports = ctx.get("community_reports");
            Map<Integer, Integer> mapping = ctx.get("community_id_mapping");

            List<CommunityReport> mergedReports = updateCommunityReportsWorkflow.run(oldCommunityReports, deltaCommunityReports, mapping);
            ctx.put("community_reports", mergedReports);

            return WorkflowFunctionOutput.builder().result("update_community_reports_done").build();
        });

        // 8) update_text_embeddings
        factory.register("update_text_embeddings", (cfg, ctx) -> {
            updateTextEmbeddingsWorkflow.run();
            return WorkflowFunctionOutput.builder().result("update_text_embeddings_done").build();
        });

        // 9) update_clean_state
        factory.register("update_clean_state", (cfg, ctx) -> {
            updateCleanStateWorkflow.run(ctx);
            return WorkflowFunctionOutput.builder().result("update_clean_state_done").build();
        });

        // pipeline 顺序：严格按 Python 的 StandardUpdate 语义拼起来（去掉你不做的 documents 部分也要保留 update_* 的位置）
        factory.registerPipeline("standard_update", List.of(
                "load_previous_index",
                "load_update_documents",
                "update_extract_graph",
                "update_entities_relationships",
                "update_text_units",
                "update_covariates",
                "create_communities",
                "update_communities",
                "create_final_text_units",
                "create_community_reports",
                "update_community_reports",
                "update_text_embeddings",
                "update_clean_state",
                "persist_index"
        ));
    }
}
