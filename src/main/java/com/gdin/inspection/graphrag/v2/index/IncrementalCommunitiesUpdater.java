package com.gdin.inspection.graphrag.v2.index;

import com.gdin.inspection.graphrag.v2.index.update.CommunityUpdateUtils;
import com.gdin.inspection.graphrag.v2.models.Community;
import com.gdin.inspection.graphrag.v2.models.CommunityReport;
import jakarta.annotation.Resource;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 增量更新社区 + 社区报告 的统一封装。
 *
 * 对应 Python 里的：
 * - _update_and_merge_communities(...)
 * - _update_and_merge_community_reports(...)
 *
 * 只是这里拆成了三个类：
 * - CommunityUpdateUtils      —— 纯工具方法（已经有）
 * - UpdateCommunitiesOperation       —— 合并 communities（已经有）
 * - UpdateCommunityReportsOperation  —— 合并 community_reports（已经有）
 *
 * 这个类只是把上面两个 Operation 串成一个「一步到位」的方法，
 * 方便在增量任务里直接调用。
 */
@Slf4j
@Component
public class IncrementalCommunitiesUpdater {

    @Resource
    private UpdateCommunitiesOperation updateCommunitiesOperation;

    @Resource
    private UpdateCommunityReportsOperation updateCommunityReportsOperation;

    /**
     * 封装一次增量合并的结果：
     * - mergedCommunities: 合并后的社区列表（旧 + 新）
     * - mergedCommunityReports: 合并后的社区报告列表（旧 + 新）
     * - communityIdMapping: 旧 community_id -> 新 community_id 的映射
     */
    @Value
    @Builder
    public static class Result {
        List<Community> mergedCommunities;
        List<CommunityReport> mergedCommunityReports;
        Map<Integer, Integer> communityIdMapping;
    }

    /**
     * 一次性完成「社区」和「社区报告」的增量合并。
     *
     * 对齐 Python 逻辑：
     * 1. 先通过 UpdateCommunitiesOperation 合并 communities，得到新的社区列表 + 映射表；
     * 2. 再用同一份映射表，通过 UpdateCommunityReportsOperation 合并 community_reports。
     *
     * @param oldCommunities   旧 run 的 communities
     * @param deltaCommunities 本次增量 run 产生的 communities
     * @param oldReports       旧 run 的 community_reports
     * @param deltaReports     本次增量 run 产生的 community_reports
     */
    public Result mergeCommunitiesAndReports(
            List<Community> oldCommunities,
            List<Community> deltaCommunities,
            List<CommunityReport> oldReports,
            List<CommunityReport> deltaReports
    ) {
        // 1. 先合并 communities
        CommunityUpdateUtils.CommunitiesMergeResult communitiesMergeResult =
                updateCommunitiesOperation.updateCommunities(oldCommunities, deltaCommunities);

        List<Community> mergedCommunities = communitiesMergeResult.getMergedCommunities();
        Map<Integer, Integer> communityIdMapping = communitiesMergeResult.getCommunityIdMapping();

        // 2. 再用同一份映射表合并社区报告
        List<CommunityReport> mergedReports =
                updateCommunityReportsOperation.updateCommunityReports(
                        oldReports,
                        deltaReports,
                        communityIdMapping
                );

        return Result.builder()
                .mergedCommunities(mergedCommunities)
                .mergedCommunityReports(mergedReports)
                .communityIdMapping(communityIdMapping)
                .build();
    }
}
