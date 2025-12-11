package com.gdin.inspection.graphrag.v2.util;

import com.gdin.inspection.graphrag.v2.models.Covariate;
import com.gdin.inspection.graphrag.v2.models.Entity;
import com.gdin.inspection.graphrag.v2.models.Relationship;
import com.gdin.inspection.graphrag.v2.models.TextUnit;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class FinalizeUtils {
    public static List<Entity> finalizeEntities(List<Entity> dedupedEntities) {
        AtomicInteger idx = new AtomicInteger(0);
        return dedupedEntities.stream()
                .map(e -> Entity.builder()
                        .id(UUID.randomUUID().toString())
                        .humanReadableId(idx.getAndIncrement())
                        .title(e.getTitle())
                        .type(e.getType())
                        .descriptionList(e.getDescriptionList())
                        .summary(e.getSummary())
                        .aliases(e.getAliases())
                        .sourceTextUnitIds(e.getSourceTextUnitIds())
                        .metadata(e.getMetadata())
                        .createdAt(Instant.now())
                        .build())
                .collect(Collectors.toList());
    }

    public static List<Relationship> finalizeRelationships(List<Relationship> relationships) {
        AtomicInteger idx = new AtomicInteger(0);
        return relationships.stream()
                .map(r -> Relationship.builder()
                        .id(UUID.randomUUID().toString())
                        .humanReadableId(idx.getAndIncrement())
                        .source(r.getSource())
                        .target(r.getTarget())
                        .weight(r.getWeight())           // ← 这里把 weight 透传下去
                        .predicate(r.getPredicate())
                        .descriptionList(r.getDescriptionList())
                        .summary(r.getSummary())
                        .sourceTextUnitIds(r.getSourceTextUnitIds())
                        .metadata(r.getMetadata())
                        .createdAt(Instant.now())
                        .build())
                .collect(Collectors.toList());
    }

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
            List<String> srcTuIds = e.getSourceTextUnitIds();
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
            List<String> srcTuIds = r.getSourceTextUnitIds();
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
                String tuId = c.getSourceTextUnitId();
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
                    // 如果前面流程保证一定有 documentIds，这里可以直接用 u.getDocumentIds()
                    if (documentIds == null || documentIds.isEmpty()) {
                        documentIds = null;
                    }

                    List<String> entityIds = toListOrNull(tuToEntityIds.get(id));
                    List<String> relationshipIds = toListOrNull(tuToRelIds.get(id));
                    List<String> covariateIds = toListOrNull(tuToCovIds.get(id));

                    return TextUnit.builder()
                            .id(id)
                            .humanReadableId(idx.getAndIncrement())
                            .text(u.getText())
                            .nTokens(u.getNTokens())
                            .documentIds(documentIds)
                            .entityIds(entityIds)
                            .relationshipIds(relationshipIds)
                            .covariateIds(covariateIds)
                            .attributes(u.getAttributes())
                            .build();
                })
                .collect(Collectors.toList());
    }

    private static List<String> toListOrNull(Set<String> set) {
        if (set == null || set.isEmpty()) {
            return null;
        }
        return new ArrayList<>(set);
    }
}
