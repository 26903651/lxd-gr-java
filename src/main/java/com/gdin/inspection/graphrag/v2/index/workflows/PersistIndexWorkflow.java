package com.gdin.inspection.graphrag.v2.index.workflows;

import cn.hutool.core.collection.CollectionUtil;
import com.gdin.inspection.graphrag.config.properties.GraphProperties;
import com.gdin.inspection.graphrag.service.MilvusDeleteService;
import com.gdin.inspection.graphrag.v2.models.Community;
import com.gdin.inspection.graphrag.v2.models.CommunityReport;
import com.gdin.inspection.graphrag.v2.models.Entity;
import com.gdin.inspection.graphrag.v2.models.Relationship;
import com.gdin.inspection.graphrag.v2.storage.GraphRagIndexStorage;
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

    public void run(
            List<Entity> entities,
            List<Relationship> relationships,
            List<Community> communities,
            List<CommunityReport> communityReports
    ) throws Exception {
        if (CollectionUtil.isEmpty(entities)) throw new IllegalStateException("entities empty, refuse to deleteAll");
        if (CollectionUtil.isEmpty(relationships)) throw new IllegalStateException("relationships empty, refuse to deleteAll");
        if (CollectionUtil.isEmpty(communities)) throw new IllegalStateException("communities empty, refuse to deleteAll");
        if (CollectionUtil.isEmpty(communityReports)) throw new IllegalStateException("communityReports empty, refuse to deleteAll");

        log.info(
                "保存所有实体：entities={}, relationships={}, communities={}, communityReports={}",
                entities.size(),
                relationships.size(),
                communities.size(),
                communityReports.size()
        );

        // 先清空
        milvusDeleteService.deleteAll(graphProperties.getCommunityReportCollectionName());
        milvusDeleteService.deleteAll(graphProperties.getCommunityCollectionName());
        milvusDeleteService.deleteAll(graphProperties.getRelationshipCollectionName());
        milvusDeleteService.deleteAll(graphProperties.getEntityCollectionName());

        // 只负责落库：数据由前面 workflow 产出并放进 context.state
        milvusStorage.saveEntities(entities);
        milvusStorage.saveRelationships(relationships);
        milvusStorage.saveCommunities(communities);
        milvusStorage.saveCommunityReports(communityReports);
    }
}
