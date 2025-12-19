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
        // if (CollectionUtil.isEmpty(textUnits)) throw new IllegalStateException("textUnits empty, refuse to deleteAll");
        // if (CollectionUtil.isEmpty(entities)) throw new IllegalStateException("entities empty, refuse to deleteAll");
        // if (CollectionUtil.isEmpty(relationships)) throw new IllegalStateException("relationships empty, refuse to deleteAll");
        // if (CollectionUtil.isEmpty(communities)) throw new IllegalStateException("communities empty, refuse to deleteAll");
        // if (CollectionUtil.isEmpty(communityReports)) throw new IllegalStateException("communityReports empty, refuse to deleteAll");

        log.info(
                "保存所有实体：textUnits={}, entities={}, relationships={}, communities={}, communityReports={}, covariates={}",
                CollectionUtil.isEmpty(textUnits) ? 0 :textUnits.size(),
                CollectionUtil.isEmpty(entities) ? 0 :entities.size(),
                CollectionUtil.isEmpty(relationships) ? 0 :relationships.size(),
                CollectionUtil.isEmpty(communities) ? 0 :communities.size(),
                CollectionUtil.isEmpty(communityReports) ? 0 :communityReports.size(),
                CollectionUtil.isEmpty(covariates) ? 0 :covariates.size()
        );

        // 先清空, 注意textUnits不能清空
        if(CollectionUtil.isNotEmpty(communityReports)) milvusDeleteService.deleteAll(graphProperties.getCollectionNames().getCommunityReportCollectionName());
        if(CollectionUtil.isNotEmpty(communities)) milvusDeleteService.deleteAll(graphProperties.getCollectionNames().getCommunityCollectionName());
        if(CollectionUtil.isNotEmpty(covariates)) milvusDeleteService.deleteAll(graphProperties.getCollectionNames().getCovariateCollectionName());
        if(CollectionUtil.isNotEmpty(relationships)) milvusDeleteService.deleteAll(graphProperties.getCollectionNames().getRelationshipCollectionName());
        if(CollectionUtil.isNotEmpty(entities)) milvusDeleteService.deleteAll(graphProperties.getCollectionNames().getEntityCollectionName());

        // 保存
        if(CollectionUtil.isNotEmpty(entities)) milvusStorage.saveEntities(entities);
        if(CollectionUtil.isNotEmpty(relationships)) milvusStorage.saveRelationships(relationships);
        if(CollectionUtil.isNotEmpty(covariates)) milvusStorage.saveCovariates(covariates);
        if(CollectionUtil.isNotEmpty(communities)) milvusStorage.saveCommunities(communities);
        if(CollectionUtil.isNotEmpty(communityReports)) milvusStorage.saveCommunityReports(communityReports);

        // 回写TextUnit到知识库
        if(CollectionUtil.isNotEmpty(textUnits)) knowledgeSliceWriteBackService.writeBackToKnowledgeBase(textUnits);
    }
}
