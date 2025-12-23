package com.gdin.inspection.graphrag.v2.index.workflows;

import cn.hutool.core.collection.CollectionUtil;
import com.gdin.inspection.graphrag.v2.index.opertation.SummarizeDescriptionsOperation;
import com.gdin.inspection.graphrag.v2.models.Entity;
import com.gdin.inspection.graphrag.v2.models.EntityDescriptionSummary;
import com.gdin.inspection.graphrag.v2.models.Relationship;
import com.gdin.inspection.graphrag.v2.models.RelationshipDescriptionSummary;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 对齐 Python：
 * graphrag/index/workflows/update_entities_relationships.py
 * 读取 previous(main) + delta(delta) 的 entities/relationships
 * 1) entities：按 title 合并，冲突时保留 old.id/hrid；delta 的 hrid 统一重排到 oldMax+1 开始
 * 2) relationships：按 (source,target) 合并；delta hrid 同样重排；weight=mean；再重算 combined_degree
 * 3) 对合并后的 entities/relationships 再做一次 summarize（只回写 description，不改 id/hrid）
 * 4) 写回 main（scope=1）
 * 5) 输出 entity_id_mapping：{deltaEntityId -> oldEntityId}（title 重名才映射）
 */
@Slf4j
@Service
public class UpdateGraphWorkflow {

    @Resource
    private SummarizeDescriptionsOperation summarizeDescriptionsOperation;

    public Result run(
            List<Entity> oldEntities,
            List<Entity> deltaEntities,
            List<Relationship> oldRelationships,
            List<Relationship> deltaRelationships,
            Integer entitySummaryMaxWords,
            Integer relationshipSummaryMaxWords
    ) throws Exception {
        if (CollectionUtil.isEmpty(oldEntities) && CollectionUtil.isEmpty(deltaEntities)) throw new IllegalStateException("entities(main+delta) 都为空，拒绝继续");
        if (CollectionUtil.isEmpty(oldRelationships) && CollectionUtil.isEmpty(deltaRelationships)) throw new IllegalStateException("relationships(main+delta) 都为空，拒绝继续");

        oldEntities = oldEntities == null ? Collections.emptyList() : oldEntities;
        deltaEntities = deltaEntities == null ? Collections.emptyList() : deltaEntities;
        oldRelationships = oldRelationships == null ? Collections.emptyList() : oldRelationships;
        deltaRelationships = deltaRelationships == null ? Collections.emptyList() : deltaRelationships;
        entitySummaryMaxWords = entitySummaryMaxWords == null ? 150 : entitySummaryMaxWords;
        relationshipSummaryMaxWords = relationshipSummaryMaxWords == null ? 150 : relationshipSummaryMaxWords;

        log.info(
                "开始合并实体和关系：oldEntities={}, deltaEntities={}, oldRelationships={}, deltaRelationships={}, entitySummaryMaxWords={}, relationshipSummaryMaxWords={}",
                oldEntities.size(),
                deltaEntities.size(),
                oldRelationships.size(),
                deltaRelationships.size(),
                entitySummaryMaxWords,
                relationshipSummaryMaxWords
        );

        // 1) merge entities + mapping
        MergeEntitiesResult mergedEntitiesResult = groupAndResolveEntities(oldEntities, deltaEntities);
        List<Entity> mergedEntities = mergedEntitiesResult.mergedEntities;
        Map<String, String> entityIdMapping = mergedEntitiesResult.entityIdMapping;

        // 2) merge relationships
        List<Relationship> mergedRelationships = updateAndMergeRelationships(oldRelationships, deltaRelationships);

        // 3) summarize merged (ONLY overwrite description, do not finalize / do not touch id/hrid)
        ApplySummaryResult summarized = summarizeMergedEntitiesAndRelationships(
                mergedEntities,
                mergedRelationships,
                entitySummaryMaxWords,
                relationshipSummaryMaxWords
        );

        return new Result(summarized.entities, summarized.relationships, entityIdMapping);
    }

    /**
     * 对齐 Python: _group_and_resolve_entities(old_entities_df, delta_entities_df)
     */
    private MergeEntitiesResult groupAndResolveEntities(List<Entity> oldEntities, List<Entity> deltaEntities) {
        // title -> oldId
        Map<String, String> titleToOldId = oldEntities.stream()
                .filter(e -> e != null && e.getTitle() != null && e.getId() != null)
                .collect(Collectors.toMap(Entity::getTitle, Entity::getId, (a, b) -> a, LinkedHashMap::new));

        // {deltaId -> oldId} if same title exists
        Map<String, String> idMapping = new LinkedHashMap<>();
        for (Entity d : deltaEntities) {
            if (d == null) continue;
            String title = d.getTitle();
            String deltaId = d.getId();
            if (title == null || deltaId == null) continue;
            String oldId = titleToOldId.get(title);
            if (oldId != null) idMapping.put(deltaId, oldId);
        }

        // delta human_readable_id = range(oldMax+1, oldMax+1+len(delta))
        int oldMax = oldEntities.stream()
                .filter(Objects::nonNull)
                .map(Entity::getHumanReadableId)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(-1);
        int initialId = oldMax + 1;

        List<Entity> deltaAdjusted = new ArrayList<>(deltaEntities.size());
        int i = 0;
        for (Entity d : deltaEntities) {
            if (d == null) continue;
            deltaAdjusted.add(copyEntityWithHrid(d, initialId + (i++)));
        }

        // combined = old + deltaAdjusted (order matters because python takes "first")
        List<Entity> combined = new ArrayList<>(oldEntities.size() + deltaAdjusted.size());
        combined.addAll(oldEntities);
        combined.addAll(deltaAdjusted);

        // group by title (python groupby 默认 sort=True)，这里用 TreeMap 保证 key 有序
        Map<String, List<Entity>> groups = new TreeMap<>();
        for (Entity e : combined) {
            if (e == null || e.getTitle() == null) continue;
            groups.computeIfAbsent(e.getTitle(), k -> new ArrayList<>()).add(e);
        }

        List<Entity> resolved = new ArrayList<>(groups.size());
        for (Map.Entry<String, List<Entity>> entry : groups.entrySet()) {
            String title = entry.getKey();
            List<Entity> list = entry.getValue();
            if (CollectionUtil.isEmpty(list)) continue;

            Entity first = list.get(0);

            // description 聚合为“list”，Java 用字符串拼接来模拟，再交给 summarize
            List<String> descs = new ArrayList<>();
            List<String> allTextUnitIds = new ArrayList<>();
            for (Entity e : list) {
                if (e == null) continue;
                if (e.getDescription() != null) descs.add(String.valueOf(e.getDescription()));
                if (e.getTextUnitIds() != null) allTextUnitIds.addAll(e.getTextUnitIds());
            }

            Entity merged = Entity.builder()
                    .id(first.getId())                          // first => old优先
                    .humanReadableId(first.getHumanReadableId()) // first => old优先
                    .title(title)
                    .type(first.getType())
                    .description(joinDescriptions(descs))
                    .textUnitIds(allTextUnitIds.isEmpty() ? null : allTextUnitIds)
                    .degree(first.getDegree())
                    .x(first.getX())
                    .y(first.getY())
                    // python: frequency = len(text_unit_ids)
                    .frequency(allTextUnitIds.size())
                    .build();

            resolved.add(merged);
        }

        return new MergeEntitiesResult(resolved, idMapping);
    }

    private Entity copyEntityWithHrid(Entity e, Integer newHrid) {
        if (e == null) return null;
        return Entity.builder()
                .id(e.getId())
                .humanReadableId(newHrid)
                .title(e.getTitle())
                .type(e.getType())
                .description(e.getDescription())
                .textUnitIds(e.getTextUnitIds())
                .frequency(e.getFrequency())
                .degree(e.getDegree())
                .x(e.getX())
                .y(e.getY())
                .build();
    }

    /**
     * 对齐 Python: _update_and_merge_relationships(old_relationships, delta_relationships)
     */
    private List<Relationship> updateAndMergeRelationships(List<Relationship> oldRels, List<Relationship> deltaRels) {
        int oldMax = oldRels.stream()
                .filter(Objects::nonNull)
                .map(Relationship::getHumanReadableId)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(-1);
        int initialId = oldMax + 1;

        // delta hrid reassigned
        List<Relationship> deltaAdjusted = new ArrayList<>(deltaRels.size());
        int i = 0;
        for (Relationship d : deltaRels) {
            if (d == null) continue;
            deltaAdjusted.add(copyRelationshipWithHrid(d, initialId + (i++)));
        }

        // merged_relationships = concat(old, deltaAdjusted) (order matters for "first")
        List<Relationship> combined = new ArrayList<>(oldRels.size() + deltaAdjusted.size());
        combined.addAll(oldRels);
        combined.addAll(deltaAdjusted);

        // group by (source,target) with sorting
        Map<RelKey, List<Relationship>> groups = new TreeMap<>();
        for (Relationship r : combined) {
            if (r == null) continue;
            String s = r.getSource();
            String t = r.getTarget();
            if (s == null || t == null) continue;
            groups.computeIfAbsent(new RelKey(s, t), k -> new ArrayList<>()).add(r);
        }

        List<Relationship> aggregated = new ArrayList<>(groups.size());
        for (Map.Entry<RelKey, List<Relationship>> entry : groups.entrySet()) {
            RelKey key = entry.getKey();
            List<Relationship> list = entry.getValue();
            if (CollectionUtil.isEmpty(list)) continue;

            Relationship first = list.get(0);

            List<String> descs = new ArrayList<>();
            List<String> allTextUnitIds = new ArrayList<>();
            double weightSum = 0.0;
            int weightCnt = 0;

            for (Relationship r : list) {
                if (r == null) continue;
                if (r.getDescription() != null) descs.add(String.valueOf(r.getDescription()));
                if (r.getTextUnitIds() != null) allTextUnitIds.addAll(r.getTextUnitIds());
                if (r.getWeight() != null) {
                    weightSum += r.getWeight();
                    weightCnt++;
                }
            }

            Double meanWeight = weightCnt == 0 ? first.getWeight() : (weightSum / weightCnt);

            Relationship merged = Relationship.builder()
                    .id(first.getId())                          // first => old优先
                    .humanReadableId(first.getHumanReadableId()) // first => old优先
                    .source(key.source)
                    .target(key.target)
                    .description(joinDescriptions(descs))
                    .weight(meanWeight)
                    // python: 先 agg sum，再整体重算 combined_degree，这里直接先占位，后面重算
                    .combinedDegree(first.getCombinedDegree())
                    .textUnitIds(allTextUnitIds.isEmpty() ? null : allTextUnitIds)
                    .build();

            aggregated.add(merged);
        }

        // python: source_degree = count rows per source; target_degree = count rows per target
        Map<String, Integer> sourceDegree = new HashMap<>();
        Map<String, Integer> targetDegree = new HashMap<>();
        for (Relationship r : aggregated) {
            if (r == null) continue;
            if (r.getSource() != null) sourceDegree.merge(r.getSource(), 1, Integer::sum);
            if (r.getTarget() != null) targetDegree.merge(r.getTarget(), 1, Integer::sum);
        }

        // combined_degree = source_degree + target_degree
        List<Relationship> finalList = new ArrayList<>(aggregated.size());
        for (Relationship r : aggregated) {
            if (r == null) continue;
            int sd = sourceDegree.getOrDefault(r.getSource(), 0);
            int td = targetDegree.getOrDefault(r.getTarget(), 0);
            double cd = sd + td;

            finalList.add(Relationship.builder()
                    .id(r.getId())
                    .humanReadableId(r.getHumanReadableId())
                    .source(r.getSource())
                    .target(r.getTarget())
                    .description(r.getDescription())
                    .weight(r.getWeight())
                    .combinedDegree(cd)
                    .textUnitIds(r.getTextUnitIds())
                    .build());
        }

        return finalList;
    }

    private Relationship copyRelationshipWithHrid(Relationship r, Integer newHrid) {
        if (r == null) return null;
        return Relationship.builder()
                .id(r.getId())
                .humanReadableId(newHrid)
                .source(r.getSource())
                .target(r.getTarget())
                .description(r.getDescription())
                .weight(r.getWeight())
                .combinedDegree(r.getCombinedDegree())
                .textUnitIds(r.getTextUnitIds())
                .build();
    }

    /**
     * 对齐 Python：get_summarized_entities_relationships
     * 但注意：这里只回写 description，不做 finalize，不改变 id/hrid。
     */
    private ApplySummaryResult summarizeMergedEntitiesAndRelationships(
            List<Entity> mergedEntities,
            List<Relationship> mergedRelationships,
            int entitySummaryMaxWords,
            int relationshipSummaryMaxWords
    ) {
        List<EntityDescriptionSummary> entitySummaries = summarizeDescriptionsOperation.summarizeEntities(mergedEntities, entitySummaryMaxWords);
        List<RelationshipDescriptionSummary> relSummaries = summarizeDescriptionsOperation.summarizeRelationships(mergedRelationships, relationshipSummaryMaxWords);

        Map<String, String> titleToSummary = entitySummaries.stream()
                .filter(x -> x != null && x.getTitle() != null && x.getSummary() != null)
                .collect(Collectors.toMap(
                        EntityDescriptionSummary::getTitle,
                        EntityDescriptionSummary::getSummary,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        Map<String, String> relKeyToSummary = relSummaries.stream()
                .filter(x -> x != null && x.getSourceEntityId() != null && x.getTargetEntityId() != null && x.getSummary() != null)
                .collect(Collectors.toMap(
                        x -> buildRelKey(x.getSourceEntityId(), x.getTargetEntityId()),
                        RelationshipDescriptionSummary::getSummary,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        List<Entity> entitiesOut = mergedEntities.stream()
                .map(e -> {
                    if (e == null) return null;
                    String summary = e.getTitle() == null ? null : titleToSummary.get(e.getTitle());
                    return Entity.builder()
                            .id(e.getId())
                            .humanReadableId(e.getHumanReadableId())
                            .title(e.getTitle())
                            .type(e.getType())
                            .description(summary != null ? summary : e.getDescription())
                            .textUnitIds(e.getTextUnitIds())
                            .frequency(e.getFrequency())
                            .degree(e.getDegree())
                            .x(e.getX())
                            .y(e.getY())
                            .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<Relationship> relsOut = mergedRelationships.stream()
                .map(r -> {
                    if (r == null) return null;
                    String key = buildRelKey(r.getSource(), r.getTarget());
                    String summary = relKeyToSummary.get(key);
                    return Relationship.builder()
                            .id(r.getId())
                            .humanReadableId(r.getHumanReadableId())
                            .source(r.getSource())
                            .target(r.getTarget())
                            .description(summary != null ? summary : r.getDescription())
                            .weight(r.getWeight())
                            .combinedDegree(r.getCombinedDegree())
                            .textUnitIds(r.getTextUnitIds())
                            .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return new ApplySummaryResult(entitiesOut, relsOut);
    }

    private static String joinDescriptions(List<String> descs) {
        if (CollectionUtil.isEmpty(descs)) return "";
        // 模拟 Python: list(x.astype(str))，再交给 summarize
        // 这里用空行分隔，避免一坨糊成一团
        return descs.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining("\n\n"));
    }

    private static String buildRelKey(String sourceId, String targetId) {
        String s = sourceId == null ? "" : sourceId;
        String t = targetId == null ? "" : targetId;
        return s + "||" + t;
    }

    private static class RelKey implements Comparable<RelKey> {
        private final String source;
        private final String target;

        private RelKey(String source, String target) {
            this.source = source;
            this.target = target;
        }

        @Override
        public int compareTo(RelKey o) {
            int c = this.source.compareTo(o.source);
            if (c != 0) return c;
            return this.target.compareTo(o.target);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RelKey relKey)) return false;
            return Objects.equals(source, relKey.source) && Objects.equals(target, relKey.target);
        }

        @Override
        public int hashCode() {
            return Objects.hash(source, target);
        }
    }

    private static class MergeEntitiesResult {
        private final List<Entity> mergedEntities;
        private final Map<String, String> entityIdMapping;

        private MergeEntitiesResult(List<Entity> mergedEntities, Map<String, String> entityIdMapping) {
            this.mergedEntities = mergedEntities;
            this.entityIdMapping = entityIdMapping;
        }
    }

    private static class ApplySummaryResult {
        private final List<Entity> entities;
        private final List<Relationship> relationships;

        private ApplySummaryResult(List<Entity> entities, List<Relationship> relationships) {
            this.entities = entities;
            this.relationships = relationships;
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Result {
        private List<Entity> mergedEntities;
        private List<Relationship> mergedRelationships;
        private Map<String, String> entityIdMapping;
    }
}
