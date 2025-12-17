package com.gdin.inspection.graphrag.v2.index.workflows;

import com.gdin.inspection.graphrag.v2.index.update.IncrementalCommunityReports;
import com.gdin.inspection.graphrag.v2.models.CommunityReport;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class UpdateCommunityReportsWorkflow {

    public List<CommunityReport> run(
            List<CommunityReport> oldReports,
            List<CommunityReport> deltaReports,
            Map<Integer, Integer> communityIdMapping
    ) {
        return IncrementalCommunityReports.updateAndMergeCommunityReports(oldReports, deltaReports, communityIdMapping);
    }
}