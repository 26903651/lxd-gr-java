package com.gdin.inspection.graphrag.v2.util;

import cn.hutool.core.collection.CollectionUtil;
import com.gdin.inspection.graphrag.v2.models.Covariate;
import com.gdin.inspection.graphrag.v2.models.Entity;
import com.gdin.inspection.graphrag.v2.models.Relationship;
import com.gdin.inspection.graphrag.v2.models.TextUnit;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 对齐 Python finalize 阶段：
 * - 为 entity / relationship / text_unit 分配 id & human_readable_id
 * - 根据实体 / 关系 / 协变量，反向填充 text_unit_ids 上的引用字段
 */
public class FinalizeUtils {

    /**
     * 为实体分配 id / human_readable_id，保留其它统计字段。
     * 最终列对应：id, human_readable_id, title, type, description,
     *              text_unit_ids, degree, frequency, x, y
     */
    public static List<Entity> finalizeEntities(List<Entity> dedupedEntities) {
        AtomicInteger idx = new AtomicInteger(0);
        return dedupedEntities.stream()
                .map(e -> Entity.builder()
                        .id(UUID.randomUUID().toString())
                        .humanReadableId(idx.getAndIncrement())
                        .title(e.getTitle())
                        .type(e.getType())
                        .description(e.getDescription())
                        .textUnitIds(e.getTextUnitIds())
                        .frequency(e.getFrequency())
                        .degree(e.getDegree())
                        .x(e.getX())
                        .y(e.getY())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 为关系分配 id / human_readable_id。
     * 最终列对应：id, human_readable_id, source, target,
     *            description, weight, combined_degree, text_unit_ids
     */
    public static List<Relationship> finalizeRelationships(List<Relationship> relationships) {
        AtomicInteger idx = new AtomicInteger(0);
        return relationships.stream()
                .map(r -> Relationship.builder()
                        .id(UUID.randomUUID().toString())
                        .humanReadableId(idx.getAndIncrement())
                        .source(r.getSource())
                        .target(r.getTarget())
                        .description(r.getDescription())
                        .weight(r.getWeight())
                        .combinedDegree(r.getCombinedDegree())
                        .textUnitIds(r.getTextUnitIds())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 根据实体 / 关系 / 协变量，反推每个 TextUnit 对应的 entity_ids / relationship_ids / covariate_ids，
     * 并分配 text_unit 的 human_readable_id。
     */
    public static List<TextUnit> finalizeTextUnits(
            List<TextUnit> units,
            List<Entity> entities,
            List<Relationship> relationships,
            List<Covariate> covariates
    ) {
        AtomicInteger idx = new AtomicInteger(0);

        // 1. text_unit_id -> entity_ids
        Map<String, Set<String>> tuToEntityIds = new HashMap<>();
        for (Entity e : entities) {
            List<String> srcTuIds = e.getTextUnitIds();
            if (srcTuIds == null) continue;
            for (String tuId : srcTuIds) {
                if (tuId == null) continue;
                tuToEntityIds
                        .computeIfAbsent(tuId, k -> new LinkedHashSet<>())
                        .add(e.getId());
            }
        }

        // 2. text_unit_id -> relationship_ids
        Map<String, Set<String>> tuToRelIds = new HashMap<>();
        for (Relationship r : relationships) {
            List<String> srcTuIds = r.getTextUnitIds();
            if (srcTuIds == null) continue;
            for (String tuId : srcTuIds) {
                if (tuId == null) continue;
                tuToRelIds
                        .computeIfAbsent(tuId, k -> new LinkedHashSet<>())
                        .add(r.getId());
            }
        }

        // 3. text_unit_id -> covariate_ids
        Map<String, Set<String>> tuToCovIds = new HashMap<>();
        if (covariates != null) {
            for (Covariate c : covariates) {
                String tuId = c.getTextUnitId();  // 注意：新版 Covariate 字段
                if (tuId == null) continue;
                tuToCovIds
                        .computeIfAbsent(tuId, k -> new LinkedHashSet<>())
                        .add(c.getId());
            }
        }

        // 4. 汇总生成最终 text_units
        return units.stream()
                .map(u -> {
                    String id = u.getId();

                    List<String> documentIds = u.getDocumentIds();
                    if (documentIds == null || documentIds.isEmpty()) {
                        documentIds = null;
                    }

                    List<String> entityIds = toListOrNull(tuToEntityIds.get(id));
                    List<String> relationshipIds = toListOrNull(tuToRelIds.get(id));
                    List<String> covariateIds = toListOrEmpty(tuToCovIds.get(id));

                    return TextUnit.builder()
                            .id(id)
                            .humanReadableId(idx.getAndIncrement())
                            .text(u.getText())
                            .nTokens(u.getNTokens())
                            .documentIds(documentIds)
                            .entityIds(entityIds)
                            .relationshipIds(relationshipIds)
                            .covariateIds(covariateIds)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private static List<String> toListOrNull(Set<String> set) {
        if (CollectionUtil.isEmpty(set)) return null;
        return new ArrayList<>(set);
    }

    private static List<String> toListOrEmpty(Set<String> set) {
        if (CollectionUtil.isEmpty(set)) return Collections.emptyList();
        return new ArrayList<>(set);
    }
}
