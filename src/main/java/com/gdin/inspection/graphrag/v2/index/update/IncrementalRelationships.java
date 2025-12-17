package com.gdin.inspection.graphrag.v2.index.update;

import com.gdin.inspection.graphrag.v2.models.Relationship;

import java.util.*;
import java.util.stream.Collectors;

public class IncrementalRelationships {

    private IncrementalRelationships() {}

    /**
     * 严格对齐 Python: graphrag/index/update/relationships.py::_update_and_merge_relationships
     *
     * 关键点：
     * - delta.human_readable_id = old.max + 1 起连续递增
     * - concat old + delta
     * - groupby(source,target):
     *     id first
     *     human_readable_id first
     *     description = list(str)  -> 这里用 JSON 数组字符串承载
     *     text_unit_ids flatten
     *     weight mean
     *     combined_degree 先聚合再被覆盖
     * - 重算 source_degree / target_degree（Python 中是临时列，这里只用于 combined_degree）
     * - combined_degree = source_degree + target_degree
     */
    public static List<Relationship> updateAndMergeRelationships(
            List<Relationship> oldRelationships,
            List<Relationship> deltaRelationships
    ) {
        List<Relationship> oldList = oldRelationships == null ? List.of() : oldRelationships;
        List<Relationship> deltaList = deltaRelationships == null ? List.of() : deltaRelationships;

        int initialId = maxHumanReadableId(oldList) + 1;

        // 1) delta 的 human_readable_id 续接 old.max + 1
        List<Relationship> deltaWithHrId = new ArrayList<>(deltaList.size());
        for (int i = 0; i < deltaList.size(); i++) {
            Relationship d = deltaList.get(i);
            if (d == null) continue;

            deltaWithHrId.add(Relationship.builder()
                    .id(d.getId())
                    .humanReadableId(initialId + i)
                    .source(d.getSource())
                    .target(d.getTarget())
                    .description(d.getDescription())
                    .weight(d.getWeight())
                    .combinedDegree(d.getCombinedDegree())
                    .textUnitIds(d.getTextUnitIds())
                    .build());
        }

        // 2) concat old + delta
        List<Relationship> merged = new ArrayList<>(oldList.size() + deltaWithHrId.size());
        merged.addAll(oldList);
        merged.addAll(deltaWithHrId);

        // 3) groupby(source,target)
        Map<String, List<Relationship>> byKey = new HashMap<>();
        for (Relationship r : merged) {
            if (r == null) continue;
            String k = key(r.getSource(), r.getTarget());
            byKey.computeIfAbsent(k, _k -> new ArrayList<>()).add(r);
        }

        // pandas groupby 默认 sort=True：这里按 key 排序稳定输出
        List<String> keys = new ArrayList<>(byKey.keySet());
        keys.sort(Comparator.nullsFirst(String::compareTo));

        List<Relationship> aggregated = new ArrayList<>(keys.size());
        for (String k : keys) {
            List<Relationship> group = byKey.get(k);
            if (group == null || group.isEmpty()) continue;

            Relationship first = group.get(0);

            // description: list(str)
            List<String> descList = group.stream()
                    .map(Relationship::getDescription)
                    .map(StringListJsonCodec::decode)  // 允许 old 里已经是 JSON 数组字符串
                    .flatMap(List::stream)
                    .map(s -> s == null ? "null" : s)
                    .collect(Collectors.toList());

            // text_unit_ids: flatten
            List<String> textUnitIds = group.stream()
                    .map(Relationship::getTextUnitIds)
                    .filter(Objects::nonNull)
                    .flatMap(List::stream)
                    .collect(Collectors.toList());

            // weight: mean
            double weightMean = group.stream()
                    .map(Relationship::getWeight)
                    .filter(Objects::nonNull)
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);

            // combined_degree: sum（随后会被覆盖为 source_degree + target_degree）
            double combinedDegreeSum = group.stream()
                    .map(Relationship::getCombinedDegree)
                    .filter(Objects::nonNull)
                    .mapToDouble(Double::doubleValue)
                    .sum();

            aggregated.add(Relationship.builder()
                    .id(first.getId())
                    .humanReadableId(first.getHumanReadableId())
                    .source(first.getSource())
                    .target(first.getTarget())
                    .description(StringListJsonCodec.encode(descList)) // ✅ 用 JSON 数组字符串承载 list[str]
                    .textUnitIds(textUnitIds)
                    .weight(weightMean)
                    .combinedDegree(combinedDegreeSum)
                    .build());
        }

        // 4) 重算 source_degree / target_degree
        Map<String, Integer> sourceDegree = new HashMap<>();
        Map<String, Integer> targetDegree = new HashMap<>();
        for (Relationship r : aggregated) {
            if (r == null) continue;
            sourceDegree.merge(r.getSource(), 1, Integer::sum);
            targetDegree.merge(r.getTarget(), 1, Integer::sum);
        }

        // 5) combined_degree = source_degree + target_degree（Python 最终是 int，这里放到 Double）
        List<Relationship> finalRels = new ArrayList<>(aggregated.size());
        for (Relationship r : aggregated) {
            int sd = sourceDegree.getOrDefault(r.getSource(), 0);
            int td = targetDegree.getOrDefault(r.getTarget(), 0);
            int cd = sd + td;

            finalRels.add(Relationship.builder()
                    .id(r.getId())
                    .humanReadableId(r.getHumanReadableId())
                    .source(r.getSource())
                    .target(r.getTarget())
                    .description(r.getDescription())
                    .textUnitIds(r.getTextUnitIds())
                    .weight(r.getWeight())
                    .combinedDegree((double) cd) // ✅ 覆盖为 sd+td
                    .build());
        }

        return finalRels;
    }

    private static String key(String source, String target) {
        return String.valueOf(source) + "||" + String.valueOf(target);
    }

    private static int maxHumanReadableId(List<Relationship> list) {
        int max = -1;
        for (Relationship r : list) {
            if (r != null && r.getHumanReadableId() != null) {
                max = Math.max(max, r.getHumanReadableId());
            }
        }
        return max;
    }
}
