package com.gdin.inspection.graphrag.v2.index.opertation;

import com.gdin.inspection.graphrag.v2.index.update.CommunityUpdateUtils;
import com.gdin.inspection.graphrag.v2.models.CommunityReport;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 对齐 Python graphrag.index.update.communities._update_and_merge_community_reports
 *
 * 同样只负责合并逻辑：
 * - oldReports：旧 community_reports
 * - deltaReports：增量跑出来的 community_reports
 * - communityIdMapping：来自 UpdateCommunitiesOperation 的映射
 */
@Component
public class UpdateCommunityReportsOperation {

    public List<CommunityReport> updateCommunityReports(
            List<CommunityReport> oldReports,
            List<CommunityReport> deltaReports,
            Map<Integer, Integer> communityIdMapping
    ) {
        return CommunityUpdateUtils.updateAndMergeCommunityReports(
                oldReports,
                deltaReports,
                communityIdMapping
        );
    }
}
