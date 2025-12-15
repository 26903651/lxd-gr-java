package com.gdin.inspection.graphrag.v2.index.workflows;

import com.gdin.inspection.graphrag.v2.index.pipeline.PipelineFactory;
import com.gdin.inspection.graphrag.v2.index.pipeline.WorkflowFunctionOutput;
import com.gdin.inspection.graphrag.v2.models.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DefaultPipelineRegistrar {

    @Resource
    private LoadInputDocumentsWorkflow loadInputDocumentsWorkflow;
    @Resource
    private ExtractGraphWorkflow extractGraphWorkflow;
    @Resource
    private CreateCommunitiesWorkflow createCommunitiesWorkflow;
    @Resource
    private CreateFinalTextUnitsWorkflow createFinalTextUnitsWorkflow;
    @Resource
    private CreateCommunityReportsWorkflow createCommunityReportsWorkflow;
    @Resource
    private PersistIndexWorkflow persistIndexWorkflow;

    // 用 PipelineFactory 组装 pipeline
    public final PipelineFactory<Object> factory = new PipelineFactory<>();

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

        // 3) create_communities
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

        // 4) create_final_text_units
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

        // 5) create_community_reports
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

        // 5) persist_index
        factory.register("persist_index", (cfg, ctx) -> {
            persistIndexWorkflow.run(
                    ctx.get("entities"),
                    ctx.get("relationships"),
                    ctx.get("communities"),
                    ctx.get("community_reports")
            );

            return WorkflowFunctionOutput.builder().result("persist_index_done").build();
        });

        // pipeline：对齐 Python Standard 的相对顺序（去掉 documents 的部分）
        factory.registerPipeline("standard", List.of(
                "load_input_documents",
                "extract_graph",
                "create_communities",
                "create_final_text_units",
                "create_community_reports",
                "persist_index"
        ));
    }
}
