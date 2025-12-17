package com.gdin.inspection.graphrag.v2.index.update;

import com.gdin.inspection.graphrag.v2.models.Entity;
import lombok.Value;

import java.util.*;
import java.util.stream.Collectors;

public class IncrementalEntities {

    private IncrementalEntities() {}

    @Value
    public static class Result {
        List<Entity> mergedEntities;
        Map<String, String> entityIdMapping; // {delta.id -> old.id}
    }

    /**
     * 对齐 Python: _group_and_resolve_entities(old, delta) -> (resolved, id_mapping)
     *
     * - 先基于 title 做 id_mapping: {B.id : A.id}
     * - delta.human_readable_id 续接 old.max + 1（即使之后 groupby 只取 first，也要先做这一句）
     * - concat old + delta
     * - groupby(title) 聚合：
     *     id first
     *     type first
     *     human_readable_id first
     *     description list(str)
     *     text_unit_ids flatten
     *     degree first
     *     x first
     *     y first
     * - frequency = len(text_unit_ids)
     */
    public static Result groupAndResolveEntities(List<Entity> oldEntities, List<Entity> deltaEntities) {
        List<Entity> oldList = oldEntities == null ? List.of() : oldEntities;
        List<Entity> deltaList = deltaEntities == null ? List.of() : deltaEntities;

        // 1) id_mapping: title 相同 => {delta.id -> old.id}
        Map<String, String> oldTitleToId = oldList.stream()
                .filter(Objects::nonNull)
                .filter(e -> e.getTitle() != null)
                .collect(Collectors.toMap(
                        Entity::getTitle,
                        Entity::getId,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        Map<String, String> idMapping = new LinkedHashMap<>();
        for (Entity d : deltaList) {
            if (d == null) continue;
            String oldId = oldTitleToId.get(d.getTitle());
            if (oldId != null && d.getId() != null) {
                idMapping.put(d.getId(), oldId);
            }
        }

        // 2) delta.human_readable_id 续接
        int initialId = maxHumanReadableId(oldList) + 1;
        List<Entity> deltaWithHr = new ArrayList<>(deltaList.size());
        for (int i = 0; i < deltaList.size(); i++) {
            Entity d = deltaList.get(i);
            if (d == null) continue;
            deltaWithHr.add(Entity.builder()
                    .id(d.getId())
                    .humanReadableId(initialId + i)
                    .title(d.getTitle())
                    .type(d.getType())
                    .description(d.getDescription())
                    .textUnitIds(d.getTextUnitIds())
                    .frequency(d.getFrequency())
                    .degree(d.getDegree())
                    .x(d.getX())
                    .y(d.getY())
                    .build());
        }

        // 3) concat old + delta
        List<Entity> combined = new ArrayList<>(oldList.size() + deltaWithHr.size());
        combined.addAll(oldList);
        combined.addAll(deltaWithHr);

        // 4) groupby(title)
        Map<String, List<Entity>> byTitle = new HashMap<>();
        for (Entity e : combined) {
            if (e == null) continue;
            String title = e.getTitle();
            if (title == null) continue; // Python groupby(title) 这里 title 为空会被当成一组；你的数据里不应为空，空就直接跳过
            byTitle.computeIfAbsent(title, _k -> new ArrayList<>()).add(e);
        }

        // pandas groupby 默认 sort=True，这里按 title 排序保证一致
        List<String> titles = new ArrayList<>(byTitle.keySet());
        titles.sort(String::compareTo);

        List<Entity> resolved = new ArrayList<>(titles.size());
        for (String title : titles) {
            List<Entity> group = byTitle.get(title);
            if (group == null || group.isEmpty()) continue;

            Entity first = group.get(0);

            // description = list(str)
            List<String> descList = group.stream()
                    .map(Entity::getDescription)
                    .map(StringListJsonCodec::decode) // 允许旧数据里 description 已经是 JSON
                    .flatMap(List::stream)
                    .map(s -> s == null ? "null" : s)
                    .collect(Collectors.toList());

            // text_unit_ids flatten
            List<String> textUnitIds = group.stream()
                    .map(Entity::getTextUnitIds)
                    .filter(Objects::nonNull)
                    .flatMap(List::stream)
                    .collect(Collectors.toList());

            int frequency = textUnitIds.size();

            resolved.add(Entity.builder()
                    .id(first.getId())
                    .humanReadableId(first.getHumanReadableId())
                    .title(title)
                    .type(first.getType())
                    // 合并阶段先放 list[str]（JSON 数组字符串），后续 summarization 会写回摘要字符串
                    .description(StringListJsonCodec.encode(descList))
                    .textUnitIds(textUnitIds)
                    .frequency(frequency)
                    .degree(first.getDegree())
                    .x(first.getX())
                    .y(first.getY())
                    .build());
        }

        return new Result(resolved, idMapping);
    }

    private static int maxHumanReadableId(List<Entity> list) {
        int max = -1;
        for (Entity e : list) {
            if (e != null && e.getHumanReadableId() != null) {
                max = Math.max(max, e.getHumanReadableId());
            }
        }
        return max;
    }
}
