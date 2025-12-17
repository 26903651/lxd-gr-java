package com.gdin.inspection.graphrag.v2.index.update;

import com.gdin.inspection.graphrag.v2.models.TextUnit;

import java.util.*;

public class IncrementalTextUnits {

    private IncrementalTextUnits() {}

    /**
     * 对齐 Python: _update_and_merge_text_units(old, delta, entity_id_mapping)
     *
     * - 若 entity_id_mapping 非空：delta.entity_ids 中的每个 id 用 mapping 替换
     * - delta.human_readable_id = old.max + 1 起连续递增
     * - concat old + delta
     */
    public static List<TextUnit> updateAndMergeTextUnits(
            List<TextUnit> oldTextUnits,
            List<TextUnit> deltaTextUnits,
            Map<String, String> entityIdMapping
    ) {
        List<TextUnit> oldList = oldTextUnits == null ? List.of() : oldTextUnits;
        List<TextUnit> deltaList = deltaTextUnits == null ? List.of() : deltaTextUnits;

        int initialId = maxHumanReadableId(oldList) + 1;

        List<TextUnit> deltaUpdated = new ArrayList<>(deltaList.size());
        for (int i = 0; i < deltaList.size(); i++) {
            TextUnit t = deltaList.get(i);
            if (t == null) continue;

            List<String> newEntityIds = t.getEntityIds();
            if (entityIdMapping != null && !entityIdMapping.isEmpty() && newEntityIds != null) {
                List<String> replaced = new ArrayList<>(newEntityIds.size());
                for (String id : newEntityIds) {
                    replaced.add(entityIdMapping.getOrDefault(id, id));
                }
                newEntityIds = replaced;
            }

            deltaUpdated.add(TextUnit.builder()
                    .id(t.getId())
                    .humanReadableId(initialId + i)
                    .text(t.getText())
                    .nTokens(t.getNTokens())
                    .documentIds(t.getDocumentIds())
                    .entityIds(newEntityIds)
                    .relationshipIds(t.getRelationshipIds())
                    .covariateIds(t.getCovariateIds())
                    .build());
        }

        List<TextUnit> merged = new ArrayList<>(oldList.size() + deltaUpdated.size());
        merged.addAll(oldList);
        merged.addAll(deltaUpdated);

        return merged;
    }

    private static int maxHumanReadableId(List<TextUnit> list) {
        int max = -1;
        for (TextUnit t : list) {
            if (t != null && t.getHumanReadableId() != null) {
                max = Math.max(max, t.getHumanReadableId());
            }
        }
        return max;
    }
}
