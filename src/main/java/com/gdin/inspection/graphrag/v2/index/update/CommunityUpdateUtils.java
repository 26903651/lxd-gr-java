package com.gdin.inspection.graphrag.v2.index.update;

import com.gdin.inspection.graphrag.v2.models.Community;
import com.gdin.inspection.graphrag.v2.models.CommunityReport;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 对齐 Python graphrag.index.update.communities:
 *
 * - _update_and_merge_communities
 * - _update_and_merge_community_reports
 */
public class CommunityUpdateUtils {

    /**
     * 对应 Python: _update_and_merge_communities(...)
     *
     * @param oldCommunities   旧社区列表（全量）
     * @param deltaCommunities 增量跑出来的新社区列表
     * @return 合并后的社区列表 + communityId 映射表
     */
    public static CommunitiesMergeResult updateAndMergeCommunities(
            List<Community> oldCommunities,
            List<Community> deltaCommunities
    ) {
        if (oldCommunities == null) oldCommunities = List.of();
        if (deltaCommunities == null) deltaCommunities = List.of();

        // ---- 1. old_max_community_id = old_communities["community"].fillna(0).astype(int).max()
        int oldMaxCommunityId = oldCommunities.stream()
                .map(Community::getCommunity)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);

        // ---- 2. 构建 community_id_mapping
        // Python 做法：把增量里的 community 当 value，映射到 (value + oldMax + 1)
        Map<Integer, Integer> communityIdMapping = new LinkedHashMap<>();
        for (Community c : deltaCommunities) {
            Integer cid = c.getCommunity();
            if (cid == null) continue;
            // 保持第一次映射结果即可
            communityIdMapping.putIfAbsent(cid, cid + oldMaxCommunityId + 1);
        }
        // 对齐 community_id_mapping.update({-1: -1})
        communityIdMapping.put(-1, -1);

        // ---- 3. 先对 delta 里的 community / parent 做替换
        List<Community> mappedDelta = deltaCommunities.stream()
                .map(c -> {
                    Integer oldCommunity = c.getCommunity();
                    Integer oldParent = c.getParent();

                    Integer newCommunity = oldCommunity == null
                            ? null
                            : communityIdMapping.getOrDefault(oldCommunity, oldCommunity);

                    Integer newParent = oldParent == null
                            ? null
                            : communityIdMapping.getOrDefault(oldParent, oldParent);

                    // 其它字段保持不变，children 不做映射（跟 Python 一样，它也没处理 children）
                    return Community.builder()
                            .id(c.getId())
                            .humanReadableId(c.getHumanReadableId()) // 后面统一重算
                            .community(newCommunity)
                            .level(c.getLevel())
                            .parent(newParent)
                            .children(c.getChildren())
                            .title(c.getTitle())
                            .entityIds(c.getEntityIds())
                            .relationshipIds(c.getRelationshipIds())
                            .textUnitIds(c.getTextUnitIds())
                            .period(c.getPeriod())
                            .size(c.getSize())
                            .build();
                })
                .collect(Collectors.toList());

        // ---- 4. old + mappedDelta 合并，再统一重算 title / human_readable_id
        List<Community> merged = new ArrayList<>(oldCommunities.size() + mappedDelta.size());

        // old_communities["community"] = old_communities["community"].astype(int)
        for (Community c : oldCommunities) {
            Integer community = c.getCommunity();
            // Python 会 astype(int)，这里假定不为 null，如果为 null 就当 0
            int communityInt = community != null ? community : 0;
            merged.add(
                    Community.builder()
                            .id(c.getId())
                            .humanReadableId(communityInt)                // human_readable_id = community
                            .community(communityInt)
                            .level(c.getLevel())
                            .parent(c.getParent())
                            .children(c.getChildren())
                            .title("Community " + communityInt)          // title = "Community {community}"
                            .entityIds(c.getEntityIds())
                            .relationshipIds(c.getRelationshipIds())
                            .textUnitIds(c.getTextUnitIds())
                            .period(c.getPeriod())
                            .size(c.getSize())
                            .build()
            );
        }

        for (Community c : mappedDelta) {
            Integer community = c.getCommunity();
            int communityInt = community != null ? community : 0;
            merged.add(
                    Community.builder()
                            .id(c.getId())
                            .humanReadableId(communityInt)
                            .community(communityInt)
                            .level(c.getLevel())
                            .parent(c.getParent())
                            .children(c.getChildren())   // 跟 Python 一样不做 children 映射
                            .title("Community " + communityInt)
                            .entityIds(c.getEntityIds())
                            .relationshipIds(c.getRelationshipIds())
                            .textUnitIds(c.getTextUnitIds())
                            .period(c.getPeriod())
                            .size(c.getSize())
                            .build()
            );
        }

        return new CommunitiesMergeResult(merged, communityIdMapping);
    }

    /**
     * 对应 Python: _update_and_merge_community_reports(...)
     *
     * @param oldReports        旧社区报告
     * @param deltaReports      增量跑出来的新社区报告
     * @param communityIdMapping 来自 updateAndMergeCommunities 的映射
     * @return 合并后的社区报告列表
     */
    public static List<CommunityReport> updateAndMergeCommunityReports(
            List<CommunityReport> oldReports,
            List<CommunityReport> deltaReports,
            Map<Integer, Integer> communityIdMapping
    ) {
        if (oldReports == null) oldReports = List.of();
        if (deltaReports == null) deltaReports = List.of();
        if (communityIdMapping == null) communityIdMapping = Map.of();

        // ---- 1. 对 delta_community_reports 做 community / parent 映射
        Map<Integer, Integer> finalCommunityIdMapping = communityIdMapping;
        List<CommunityReport> mappedDelta = deltaReports.stream()
                .map(r -> {
                    Integer oldCommunity = r.getCommunity();
                    Integer oldParent = r.getParent();

                    Integer newCommunity = oldCommunity == null
                            ? null
                            : finalCommunityIdMapping.getOrDefault(oldCommunity, oldCommunity);

                    Integer newParent = oldParent == null
                            ? null
                            : finalCommunityIdMapping.getOrDefault(oldParent, oldParent);

                    return CommunityReport.builder()
                            .id(r.getId())
                            .humanReadableId(r.getHumanReadableId()) // 后面统一重算
                            .community(newCommunity)
                            .level(r.getLevel())
                            .parent(newParent)
                            .children(r.getChildren())
                            .title(r.getTitle())
                            .summary(r.getSummary())
                            .fullContent(r.getFullContent())
                            .rank(r.getRank())
                            .ratingExplanation(r.getRatingExplanation())
                            .findings(r.getFindings())
                            .fullContentJson(r.getFullContentJson())
                            .period(r.getPeriod())
                            .size(r.getSize())
                            .build();
                })
                .collect(Collectors.toList());

        // ---- 2. old + mappedDelta 合并，并重算 community(int) / human_readable_id
        List<CommunityReport> merged = new ArrayList<>(oldReports.size() + mappedDelta.size());

        for (CommunityReport r : oldReports) {
            Integer community = r.getCommunity();
            int communityInt = community != null ? community : 0;

            merged.add(
                    CommunityReport.builder()
                            .id(r.getId())
                            .humanReadableId(communityInt)    // human_readable_id = community
                            .community(communityInt)
                            .level(r.getLevel())
                            .parent(r.getParent())
                            .children(r.getChildren())
                            .title(r.getTitle())
                            .summary(r.getSummary())
                            .fullContent(r.getFullContent())
                            .rank(r.getRank())
                            .ratingExplanation(r.getRatingExplanation())
                            .findings(r.getFindings())
                            .fullContentJson(r.getFullContentJson())
                            .period(r.getPeriod())
                            .size(r.getSize())
                            .build()
            );
        }

        for (CommunityReport r : mappedDelta) {
            Integer community = r.getCommunity();
            int communityInt = community != null ? community : 0;

            merged.add(
                    CommunityReport.builder()
                            .id(r.getId())
                            .humanReadableId(communityInt)
                            .community(communityInt)
                            .level(r.getLevel())
                            .parent(r.getParent())
                            .children(r.getChildren())
                            .title(r.getTitle())
                            .summary(r.getSummary())
                            .fullContent(r.getFullContent())
                            .rank(r.getRank())
                            .ratingExplanation(r.getRatingExplanation())
                            .findings(r.getFindings())
                            .fullContentJson(r.getFullContentJson())
                            .period(r.getPeriod())
                            .size(r.getSize())
                            .build()
            );
        }

        return merged;
    }

    /**
     * 对应 Python _update_and_merge_communities 的返回值：
     * (merged_communities, community_id_mapping)
     */
    public static class CommunitiesMergeResult {
        private final List<Community> mergedCommunities;
        private final Map<Integer, Integer> communityIdMapping;

        public CommunitiesMergeResult(
                List<Community> mergedCommunities,
                Map<Integer, Integer> communityIdMapping
        ) {
            this.mergedCommunities = mergedCommunities;
            this.communityIdMapping = communityIdMapping;
        }

        public List<Community> getMergedCommunities() {
            return mergedCommunities;
        }

        public Map<Integer, Integer> getCommunityIdMapping() {
            return communityIdMapping;
        }
    }
}
