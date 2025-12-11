package com.gdin.inspection.graphrag.v2.index.cluster;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * 对应 Python cluster_graph 返回的单个聚类结果:
 * (level, community_id, parent_community_id, [node_titles])
 */
@Value
@Builder
public class LeidenCluster {

    /**
     * 层级，从 0 开始，越大越高层。
     */
    int level;

    /**
     * 社区编号，同一层内唯一，整体上也可以视为全局 ID。
     */
    int communityId;

    /**
     * 父社区编号。根社区通常为 -1。
     */
    Integer parentCommunityId;

    /**
     * 该社区包含的节点名称列表，Python 版本里就是实体标题 title。
     */
    List<String> nodeTitles;
}
