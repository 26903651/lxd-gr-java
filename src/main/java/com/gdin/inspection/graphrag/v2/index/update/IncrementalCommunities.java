package com.gdin.inspection.graphrag.v2.index.update;

import com.gdin.inspection.graphrag.v2.models.Community;
import lombok.Value;

import java.util.*;

public class IncrementalCommunities {

    private IncrementalCommunities() {}

    @Value
    public static class Result {
        List<Community> mergedCommunities;
        Map<Integer, Integer> communityIdMapping; // deltaCommunityId -> newCommunityId
    }

    /**
     * 严格对齐 Python: graphrag/index/update/communities.py::_update_and_merge_communities
     *
     * 关键点：
     * - 若旧数据缺 size/period 列，Python 会补 None；Java 这里自然用 null 表示
     * - old_max_community_id = old.community.max()
     * - community_id_mapping = {v: v + old_max + 1 for v in delta.community}; 并加 {-1:-1}
     * - delta.community/delta.parent 应用 mapping
     * - merge old + delta
     * - title = "Community " + community
     * - human_readable_id = community
     */
    public static Result updateAndMergeCommunities(List<Community> oldCommunities, List<Community> deltaCommunities) {
        List<Community> oldList = oldCommunities == null ? List.of() : oldCommunities;
        List<Community> deltaList = deltaCommunities == null ? List.of() : deltaCommunities;

        int oldMax = oldList.stream()
                .map(Community::getCommunity)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);

        // community_id_mapping
        Map<Integer, Integer> mapping = new HashMap<>();
        for (Community c : deltaList) {
            if (c == null) continue;
            Integer v = c.getCommunity();
            if (v != null) {
                mapping.put(v, v + oldMax + 1);
            }
        }
        mapping.put(-1, -1);

        // 应用 mapping 到 delta 的 community / parent
        List<Community> deltaUpdated = new ArrayList<>(deltaList.size());
        for (Community c : deltaList) {
            if (c == null) continue;

            Integer comm = castIntOrThrow(c.getCommunity(), "delta.community");
            Integer parent = castIntOrThrow(c.getParent(), "delta.parent");

            Integer newComm = mapping.getOrDefault(comm, comm);
            Integer newParent = mapping.getOrDefault(parent, parent);

            deltaUpdated.add(Community.builder()
                    .id(c.getId())
                    .community(newComm)
                    .level(c.getLevel())
                    .parent(newParent)
                    .children(c.getChildren())
                    .title("Community " + newComm)   // Python 重命名 title
                    .humanReadableId(newComm)        // hrid = community
                    .entityIds(c.getEntityIds())
                    .relationshipIds(c.getRelationshipIds())
                    .textUnitIds(c.getTextUnitIds())
                    .period(c.getPeriod())
                    .size(c.getSize())
                    .build());
        }

        // old.community cast int（Python astype(int)）
        for (Community c : oldList) {
            if (c == null) continue;
            castIntOrThrow(c.getCommunity(), "old.community");
            castIntOrThrow(c.getParent(), "old.parent");
        }

        // merge
        List<Community> merged = new ArrayList<>(oldList.size() + deltaUpdated.size());
        merged.addAll(oldList);
        merged.addAll(deltaUpdated);

        // 最终再统一 title/hrid（对齐 Python）
        List<Community> finalMerged = new ArrayList<>(merged.size());
        for (Community c : merged) {
            if (c == null) continue;
            Integer comm = castIntOrThrow(c.getCommunity(), "merged.community");

            finalMerged.add(Community.builder()
                    .id(c.getId())
                    .community(comm)
                    .level(c.getLevel())
                    .parent(c.getParent())
                    .children(c.getChildren())
                    .title("Community " + comm)
                    .humanReadableId(comm)
                    .entityIds(c.getEntityIds())
                    .relationshipIds(c.getRelationshipIds())
                    .textUnitIds(c.getTextUnitIds())
                    .period(c.getPeriod())
                    .size(c.getSize())
                    .build());
        }

        return new Result(finalMerged, mapping);
    }

    private static Integer castIntOrThrow(Integer v, String field) {
        if (v == null) {
            // Python 这里 astype(int) 直接炸；Java 也要炸，避免悄悄“不对齐”
            throw new IllegalStateException(field + " 为 null，无法对齐 Python astype(int) 语义");
        }
        return v;
    }
}
