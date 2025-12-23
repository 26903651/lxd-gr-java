package com.gdin.inspection.graphrag.v2.index.workflows;

import com.gdin.inspection.graphrag.config.properties.GraphProperties;
import com.gdin.inspection.graphrag.service.MilvusDeleteService;
import com.gdin.inspection.graphrag.v2.index.pipeline.context.PipelineRunContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 对齐 Python update_clean_state 的“清理状态”意图，
 * 但按你的 Java pipeline 约定：仅保留 merged_*，其余一律清除。
 */
@Slf4j
@Service
public class UpdateCleanStateWorkflow {

    @Resource
    private MilvusDeleteService milvusDeleteService;

    @Resource
    private GraphProperties graphProperties;

    /**
     * @param ctx 你的 PipelineContext（这里按 Map 语义使用）
     * @return 被删除的 key 列表（便于日志/排查）
     */
    public List<String> run(PipelineRunContext ctx) {
        log.info("清理所有中间数据");

        List<String> toDelete = new ArrayList<>();
        for (String key : ctx.keySet()) {
            if (key == null) continue;
            // 只保留 merged_*
            if (!key.startsWith("merged_")) {
                toDelete.add(key);
            }
        }

        for (String key : toDelete) ctx.remove(key);

        // 清空所有的delta数据
        GraphProperties.CollectionNames.Delta delta = graphProperties.getCollectionNames().getDelta();
        milvusDeleteService.deleteAll(delta.getCommunityReportCollectionName());
        milvusDeleteService.deleteAll(delta.getCommunityCollectionName());
        milvusDeleteService.deleteAll(delta.getCovariateCollectionName());
        milvusDeleteService.deleteAll(delta.getRelationshipCollectionName());
        milvusDeleteService.deleteAll(delta.getEntityCollectionName());
        return toDelete;
    }
}
