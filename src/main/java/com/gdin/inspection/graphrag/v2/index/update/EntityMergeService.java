package com.gdin.inspection.graphrag.v2.index.update;

import cn.hutool.core.collection.CollectionUtil;
import com.gdin.inspection.graphrag.v2.models.Entity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class EntityMergeService {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MergeEntitiesResult {
        private List<Entity> mergedEntities;
        private Map<String, String> entityIdMapping;
    }

    public MergeEntitiesResult merge(List<Entity> oldEntities, List<Entity> deltaEntities) {
        if (CollectionUtil.isEmpty(deltaEntities)) {
            return new MergeEntitiesResult(oldEntities, Collections.emptyMap());
        }

        // 1) build title -> old entity
        Map<String, Entity> titleToOld = new LinkedHashMap<>();
        for (Entity e : oldEntities) {
            if (e == null || e.getTitle() == null) continue;
            // python groupby(title) 后取 first，所以这里保留 first 即可
            titleToOld.putIfAbsent(e.getTitle(), e);
        }

        // 2) mapping: {delta.id -> old.id} when title overlaps
        Map<String, String> idMapping = new LinkedHashMap<>();
        for (Entity de : deltaEntities) {
            if (de == null) continue;
            if (de.getTitle() == null || de.getId() == null) continue;
            Entity oe = titleToOld.get(de.getTitle());
            if (oe != null && oe.getId() != null) {
                idMapping.put(de.getId(), oe.getId());
            }
        }

        // 3) delta human_readable_id = old.max + 1 ... (对齐 python np.arange)
        int oldMax = oldEntities.stream()
                .map(Entity::getHumanReadableId)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(-1);
        int next = oldMax + 1;
        for (Entity de : deltaEntities) {
            if (de == null) continue;
            de.setHumanReadableId(next++);
        }

        // 4) concat then groupby(title) and resolve conflicts by "first"
        // Python: groupby(title).agg({id:first, type:first, human_readable_id:first, description:list(str), text_unit_ids:chain, degree:first, x:first, y:first})
        // Then frequency = len(text_unit_ids)
        Map<String, List<Entity>> grouped = new LinkedHashMap<>();
        for (Entity e : concat(oldEntities, deltaEntities)) {
            if (e == null || e.getTitle() == null) continue;
            grouped.computeIfAbsent(e.getTitle(), k -> new ArrayList<>()).add(e);
        }

        List<Entity> resolved = new ArrayList<>(grouped.size());
        for (Map.Entry<String, List<Entity>> entry : grouped.entrySet()) {
            String title = entry.getKey();
            List<Entity> items = entry.getValue();
            Entity first = items.get(0);

            // merge text_unit_ids (chain)
            List<String> mergedTextUnitIds = items.stream()
                    .map(Entity::getTextUnitIds)
                    .filter(Objects::nonNull)
                    .flatMap(List::stream)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            // description：python 会 list(str)，但随后 summarize 会重写为字符串；
            // 这里先把多段 description 拼起来，避免 Java 侧字段类型变成 list
            String mergedDesc = items.stream()
                    .map(Entity::getDescription)
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .collect(Collectors.joining("\n"));

            Entity out = Entity.builder()
                    .id(first.getId())                         // first
                    .humanReadableId(first.getHumanReadableId())// first
                    .title(title)
                    .type(first.getType())                      // first
                    .description(mergedDesc)
                    .textUnitIds(mergedTextUnitIds)
                    .degree(first.getDegree())
                    .x(first.getX())
                    .y(first.getY())
                    .build();

            out.setFrequency(mergedTextUnitIds.size()); // python: len(text_unit_ids)
            resolved.add(out);
        }

        // 按 hrid 排序（你存储层 load 已排序，但这里也做一遍更稳）
        resolved.sort(Comparator.comparingInt(e -> e.getHumanReadableId() == null ? -1 : e.getHumanReadableId()));

        return new MergeEntitiesResult(resolved, idMapping);
    }

    private static <T> List<T> concat(List<T> a, List<T> b) {
        List<T> out = new ArrayList<>((a == null ? 0 : a.size()) + (b == null ? 0 : b.size()));
        if (a != null) out.addAll(a);
        if (b != null) out.addAll(b);
        return out;
    }
}
