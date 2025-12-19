package com.gdin.inspection.graphrag.v2.index.opertation;

import com.gdin.inspection.graphrag.v2.models.Community;
import com.gdin.inspection.graphrag.v2.models.CommunityReport;
import lombok.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class FinalizeCommunityReportsOperation {

    @Value
    public static class RawReportRow {
        Integer community;
        Integer level;
        String title;
        String summary;
        String fullContent;
        Double rank;
        String ratingExplanation;
        String findingsJson;
        String fullContentJson;
    }

    public List<CommunityReport> finalizeReports(List<RawReportRow> reports, List<Community> communities) {
        if (reports == null || reports.isEmpty()) return List.of();
        Map<Integer, Community> communityById = communities == null ? Map.of()
                : communities.stream().collect(Collectors.toMap(
                Community::getCommunity,
                c -> c,
                (a, b) -> a,
                LinkedHashMap::new
        ));

        List<CommunityReport> out = new ArrayList<>(reports.size());

        for (RawReportRow r : reports) {
            Community c = communityById.get(r.getCommunity());
            // Python：merge communities 的 parent/children/size/period
            Integer parent = c == null ? null : c.getParent();
            List<Integer> children = c == null ? null : c.getChildren();
            String period = c == null ? null : c.getPeriod();
            Integer size = c == null ? null : c.getSize();

            out.add(CommunityReport.builder()
                    .id(UUID.randomUUID().toString().replace("-", ""))
                    .humanReadableId(r.getCommunity())    // Python: human_readable_id = community
                    .community(r.getCommunity())
                    .level(r.getLevel())
                    .parent(parent)
                    .children(children)
                    .title(r.getTitle())
                    .summary(r.getSummary())
                    .fullContent(r.getFullContent())
                    .rank(r.getRank())
                    .ratingExplanation(r.getRatingExplanation())
                    .findings(r.getFindingsJson())
                    .fullContentJson(r.getFullContentJson())
                    .period(period)
                    .size(size)
                    .build());
        }

        return out;
    }
}
