package com.gdin.inspection.graphrag.v2.index.workflows;

import cn.hutool.core.collection.CollectionUtil;
import com.gdin.inspection.graphrag.v2.models.CommunityReport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 对齐 Python：
 * graphrag/index/workflows/update_community_reports.py
 * graphrag/index/update/communities.py::_update_and_merge_community_reports
 * 输入：old(main) + delta(delta) + community_id_mapping
 * 输出：merged community_reports（delta 的 community/parent 做映射；hrid=community）
 */
@Slf4j
@Service
public class UpdateCommunityReportsWorkflow {

    public List<CommunityReport> run(
            List<CommunityReport> oldCommunityReports,
            List<CommunityReport> deltaCommunityReports,
            Map<Integer, Integer> communityIdMapping
    ) {
        if (CollectionUtil.isEmpty(oldCommunityReports) && CollectionUtil.isEmpty(deltaCommunityReports)) throw new IllegalStateException("community_reports(main+delta) 都为空，拒绝继续");

        oldCommunityReports = oldCommunityReports == null ? Collections.emptyList() : oldCommunityReports;
        deltaCommunityReports = deltaCommunityReports == null ? Collections.emptyList() : deltaCommunityReports;
        communityIdMapping = communityIdMapping == null ? Collections.emptyMap() : communityIdMapping;

        log.info("开始更新社区报告：oldCommunityReports={}, deltaCommunityReports={}, communityIdMapping={}",
                oldCommunityReports.size(), deltaCommunityReports.size(), communityIdMapping.size());

        // 1) apply mapping to delta community / parent
        List<CommunityReport> deltaMapped = new ArrayList<>(deltaCommunityReports.size());
        for (CommunityReport communityReport : deltaCommunityReports) {
            if (communityReport == null) continue;

            Integer community = communityReport.getCommunity();
            Integer parent = communityReport.getParent();

            Integer newCommunity = community == null ? null : communityIdMapping.getOrDefault(community, community);
            Integer newParent = parent == null ? null : communityIdMapping.getOrDefault(parent, parent);

            deltaMapped.add(CommunityReport.builder()
                    .id(communityReport.getId())
                    // Python 最终会重设 hrid=community，这里先保留/占位
                    .humanReadableId(communityReport.getHumanReadableId())
                    .community(newCommunity)
                    .level(communityReport.getLevel())
                    .parent(newParent)
                    .children(communityReport.getChildren())
                    .title(communityReport.getTitle())
                    .summary(communityReport.getSummary())
                    .fullContent(communityReport.getFullContent())
                    .rank(communityReport.getRank())
                    .ratingExplanation(communityReport.getRatingExplanation())
                    .findings(communityReport.getFindings())
                    .fullContentJson(communityReport.getFullContentJson())
                    .period(communityReport.getPeriod())
                    .size(communityReport.getSize())
                    .build());
        }

        // 2) merged = concat(old, deltaMapped)
        List<CommunityReport> merged = new ArrayList<>(oldCommunityReports.size() + deltaMapped.size());
        merged.addAll(oldCommunityReports);
        merged.addAll(deltaMapped);

        // 3) hrid = community (and keep community as int)
        List<CommunityReport> finalMerged = new ArrayList<>(merged.size());
        for (CommunityReport r : merged) {
            if (r == null) continue;

            Integer community = r.getCommunity(); // already Integer
            finalMerged.add(CommunityReport.builder()
                    .id(r.getId())
                    .humanReadableId(community)   // Python: human_readable_id = community
                    .community(community)
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
                    .build());
        }
        return finalMerged;
    }
}
