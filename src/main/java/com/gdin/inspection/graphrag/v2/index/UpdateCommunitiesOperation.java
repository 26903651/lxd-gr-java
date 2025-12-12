package com.gdin.inspection.graphrag.v2.index;

import com.gdin.inspection.graphrag.v2.index.update.CommunityUpdateUtils;
import com.gdin.inspection.graphrag.v2.index.update.CommunityUpdateUtils.CommunitiesMergeResult;
import com.gdin.inspection.graphrag.v2.models.Community;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 对齐 Python graphrag.index.update.communities._update_and_merge_communities
 *
 * 注意：这里不负责读写存储，只负责合并逻辑，
 * 上层把 “旧社区列表 + 增量社区列表” 传进来即可。
 */
@Component
public class UpdateCommunitiesOperation {

    /**
     * 合并旧社区和增量社区，并返回：
     * - mergedCommunities：合并后的社区列表
     * - communityIdMapping：旧增量社区 ID -> 新社区 ID 的映射
     *
     * 这个映射要被后面的社区报告增量更新复用。
     */
    public CommunitiesMergeResult updateCommunities(
            List<Community> oldCommunities,
            List<Community> deltaCommunities
    ) {
        return CommunityUpdateUtils.updateAndMergeCommunities(oldCommunities, deltaCommunities);
    }
}
