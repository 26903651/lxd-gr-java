package com.gdin.inspection.graphrag.v2.index.update;

import com.gdin.inspection.graphrag.v2.models.CommunityReport;

import java.util.*;

public class IncrementalCommunityReports {

    private IncrementalCommunityReports() {}

    /**
     * 对齐 Python: _update_and_merge_community_reports(old, delta, community_id_mapping)
     *
     * - delta.community / delta.parent 按 mapping 替换
     * - concat old + delta
     * - merged.community cast int（Java 已是 Integer）
     * - merged.human_readable_id = merged.community
     */
    public static List<CommunityReport> updateAndMergeCommunityReports(
            List<CommunityReport> oldReports,
            List<CommunityReport> deltaReports,
            Map<Integer, Integer> communityIdMapping
    ) {
        List<CommunityReport> oldList = oldReports == null ? List.of() : oldReports;
        List<CommunityReport> deltaList = deltaReports == null ? List.of() : deltaReports;

        Map<Integer, Integer> mapping = communityIdMapping == null ? Map.of() : communityIdMapping;

        List<CommunityReport> deltaUpdated = new ArrayList<>(deltaList.size());
        for (CommunityReport r : deltaList) {
            if (r == null) continue;

            Integer comm = requireInt(r.getCommunity(), "delta.community");
            Integer parent = requireInt(r.getParent(), "delta.parent");

            Integer newComm = mapping.getOrDefault(comm, comm);
            Integer newParent = mapping.getOrDefault(parent, parent);

            deltaUpdated.add(CommunityReport.builder()
                    .id(r.getId())
                    .community(newComm)
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
                    // Python 最后统一重置 hrid=community
                    .humanReadableId(newComm)
                    .build());
        }

        List<CommunityReport> merged = new ArrayList<>(oldList.size() + deltaUpdated.size());
        merged.addAll(oldList);
        merged.addAll(deltaUpdated);

        List<CommunityReport> finalMerged = new ArrayList<>(merged.size());
        for (CommunityReport r : merged) {
            if (r == null) continue;
            Integer comm = requireInt(r.getCommunity(), "merged.community");
            finalMerged.add(CommunityReport.builder()
                    .id(r.getId())
                    .community(comm)
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
                    .humanReadableId(comm)
                    .build());
        }

        return finalMerged;
    }

    private static Integer requireInt(Integer v, String field) {
        if (v == null) {
            // Python astype(int) 会直接炸；Java 也必须炸，保证对齐
            throw new IllegalStateException(field + " 为 null，无法对齐 Python astype(int)");
        }
        return v;
    }
}
