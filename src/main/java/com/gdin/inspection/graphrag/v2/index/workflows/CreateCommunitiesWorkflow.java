package com.gdin.inspection.graphrag.v2.index.workflows;

import cn.hutool.core.collection.CollectionUtil;
import com.gdin.inspection.graphrag.v2.index.opertation.CreateCommunitiesOperation;
import com.gdin.inspection.graphrag.v2.models.Community;
import com.gdin.inspection.graphrag.v2.models.Entity;
import com.gdin.inspection.graphrag.v2.models.Relationship;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 对齐 Python workflow: create_communities
 *
 * 输入（来自 context.state）：
 * - entities
 * - relationships
 *
 * 输出（写回 context.state）：
 * - communities
 */
@Slf4j
@Service
public class CreateCommunitiesWorkflow {

    @Resource
    private CreateCommunitiesOperation createCommunitiesOperation;

    public List<Community> run(
            List<Entity> entities,
            List<Relationship> relationships,
            Integer maxClusterSize,
            Boolean useLcc,
            Integer clusterSeed
    ) throws Exception {
        if (CollectionUtil.isEmpty(entities)) throw new IllegalStateException("entities 不能为空");
        if (CollectionUtil.isEmpty(relationships)) throw new IllegalStateException("relationships 不能为空");

        log.info(
                "开始生成社区：entities={}, relationships={}",
                entities.size(),
                relationships.size()
        );

        int mcs = maxClusterSize == null ? 10_000 : maxClusterSize;
        boolean lcc = useLcc == null ? true : useLcc;

        return createCommunitiesOperation.createCommunities(
                entities,
                relationships,
                mcs,
                lcc,
                clusterSeed
        );
    }
}
