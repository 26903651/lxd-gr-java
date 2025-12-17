package com.gdin.inspection.graphrag.v2.index.workflows;

import cn.hutool.core.collection.CollectionUtil;
import com.gdin.inspection.graphrag.config.properties.GraphProperties;
import com.gdin.inspection.graphrag.service.MilvusDeleteService;
import com.gdin.inspection.graphrag.v2.models.*;
import com.gdin.inspection.graphrag.v2.storage.GraphRagIndexStorage;
import com.gdin.inspection.graphrag.v2.storage.KnowledgeSliceWriteBackService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class PersistIndexWorkflow {

    @Resource
    private GraphRagIndexStorage milvusStorage;

    @Resource
    private MilvusDeleteService milvusDeleteService;

    @Resource
    private GraphProperties graphProperties;

    @Resource
    private KnowledgeSliceWriteBackService knowledgeSliceWriteBackService;

    public void run(
            List<TextUnit> textUnits,
            List<Entity> entities,
            List<Relationship> relationships,
            List<Community> communities,
            List<CommunityReport> communityReports,
            List<Covariate> covariates
    ) throws Exception {
        if (CollectionUtil.isEmpty(textUnits)) throw new IllegalStateException("textUnits empty, refuse to deleteAll");
        if (CollectionUtil.isEmpty(entities)) throw new IllegalStateException("entities empty, refuse to deleteAll");
        if (CollectionUtil.isEmpty(relationships)) throw new IllegalStateException("relationships empty, refuse to deleteAll");
        if (CollectionUtil.isEmpty(communities)) throw new IllegalStateException("communities empty, refuse to deleteAll");
        if (CollectionUtil.isEmpty(communityReports)) throw new IllegalStateException("communityReports empty, refuse to deleteAll");

        log.info(
                "保存所有实体：textUnits={}, entities={}, relationships={}, communities={}, communityReports={}, covariates={}",
                textUnits.size(),
                entities.size(),
                relationships.size(),
                communities.size(),
                communityReports.size(),
                CollectionUtil.isEmpty(covariates)? 0: covariates.size()
        );

        // 先清空, 注意textUnits不能清空
        milvusDeleteService.deleteAll(graphProperties.getCovariateCollectionName());
        milvusDeleteService.deleteAll(graphProperties.getCommunityReportCollectionName());
        milvusDeleteService.deleteAll(graphProperties.getCommunityCollectionName());
        milvusDeleteService.deleteAll(graphProperties.getRelationshipCollectionName());
        milvusDeleteService.deleteAll(graphProperties.getEntityCollectionName());

        // 保存
        milvusStorage.saveEntities(entities);
        milvusStorage.saveRelationships(relationships);
        milvusStorage.saveCommunities(communities);
        milvusStorage.saveCommunityReports(communityReports);
        milvusStorage.saveCovariates(covariates);

        // 回写TextUnit到知识库
        knowledgeSliceWriteBackService.writeBackToKnowledgeBase(textUnits);

    }
}
