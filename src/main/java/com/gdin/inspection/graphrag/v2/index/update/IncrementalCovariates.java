package com.gdin.inspection.graphrag.v2.index.update;

import com.gdin.inspection.graphrag.v2.models.Covariate;

import java.util.ArrayList;
import java.util.List;

public class IncrementalCovariates {

    private IncrementalCovariates() {}

    /**
     * 对齐 Python: _merge_covariates(old, delta)
     *
     * - delta.human_readable_id = old.max + 1 起连续递增
     * - concat old + delta
     */
    public static List<Covariate> mergeCovariates(List<Covariate> oldCovariates, List<Covariate> deltaCovariates) {
        List<Covariate> oldList = oldCovariates == null ? List.of() : oldCovariates;
        List<Covariate> deltaList = deltaCovariates == null ? List.of() : deltaCovariates;

        int initialId = maxHumanReadableId(oldList) + 1;

        List<Covariate> deltaUpdated = new ArrayList<>(deltaList.size());
        for (int i = 0; i < deltaList.size(); i++) {
            Covariate c = deltaList.get(i);
            if (c == null) continue;

            deltaUpdated.add(Covariate.builder()
                    .id(c.getId())
                    .humanReadableId(initialId + i)
                    .covariateType(c.getCovariateType())
                    .type(c.getType())
                    .description(c.getDescription())
                    .subjectId(c.getSubjectId())
                    .objectId(c.getObjectId())
                    .status(c.getStatus())
                    .startDate(c.getStartDate())
                    .endDate(c.getEndDate())
                    .sourceText(c.getSourceText())
                    .textUnitId(c.getTextUnitId())
                    .build());
        }

        List<Covariate> merged = new ArrayList<>(oldList.size() + deltaUpdated.size());
        merged.addAll(oldList);
        merged.addAll(deltaUpdated);
        return merged;
    }

    private static int maxHumanReadableId(List<Covariate> list) {
        int max = -1;
        for (Covariate c : list) {
            if (c != null && c.getHumanReadableId() != null) {
                max = Math.max(max, c.getHumanReadableId());
            }
        }
        return max;
    }
}
