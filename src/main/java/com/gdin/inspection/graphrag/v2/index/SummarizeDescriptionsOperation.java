package com.gdin.inspection.graphrag.v2.index;

import cn.hutool.core.collection.CollectionUtil;
import com.gdin.inspection.graphrag.v2.models.Entity;
import com.gdin.inspection.graphrag.v2.models.EntityDescriptionSummary;
import com.gdin.inspection.graphrag.v2.models.Relationship;
import com.gdin.inspection.graphrag.v2.models.RelationshipDescriptionSummary;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class SummarizeDescriptionsOperation {

    @Resource
    private DescriptionSummaryExtractor extractor;

    /**
     * 对实体做描述摘要，对齐 Python 的 entity_summaries 表。
     */
    public List<EntityDescriptionSummary> summarizeEntities(List<Entity> entities, int maxWords) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }

        // 以 title 为 key 聚合（如果你之前已经去重，这里基本是一对一）
        Map<String, List<String>> titleToDescriptions = new LinkedHashMap<>();

        for (Entity entity : entities) {
            String title = entity.getTitle();
            List<String> descList = entity.getDescriptionList();
            if (title == null) continue;
            if (CollectionUtil.isEmpty(descList)) continue;

            titleToDescriptions
                    .computeIfAbsent(title, k -> new ArrayList<>())
                    .addAll(descList);
        }

        List<EntityDescriptionSummary> result = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : titleToDescriptions.entrySet()) {
            String title = entry.getKey();
            // 去重一下，和 Python 的 sorted(set(...)) 行为类似
            List<String> descs = entry.getValue().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .collect(Collectors.toList());

            String summary = extractor.summarize(title, descs, maxWords);

            result.add(EntityDescriptionSummary.builder()
                    .title(title)
                    .summary(summary)
                    .build());
        }

        return result;
    }

    /**
     * 对关系做描述摘要，对齐 Python 的 relationship_summaries 表。
     */
    public List<RelationshipDescriptionSummary> summarizeRelationships(List<Relationship> relationships, int maxWords) {
        if (relationships == null || relationships.isEmpty()) {
            return Collections.emptyList();
        }

        // 以 (sourceEntityId, targetEntityId) 为 key 聚合
        Map<String, List<String>> keyToDescriptions = new LinkedHashMap<>();

        for (Relationship rel : relationships) {
            String source = rel.getSource();
            String target = rel.getTarget();
            List<String> descList = rel.getDescriptionList();
            if (source == null || target == null) continue;
            if (CollectionUtil.isEmpty(descList)) continue;

            String key = source + "||" + target;
            keyToDescriptions
                    .computeIfAbsent(key, k -> new ArrayList<>())
                    .addAll(descList);
        }

        List<RelationshipDescriptionSummary> result = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : keyToDescriptions.entrySet()) {
            String key = entry.getKey();
            String[] parts = key.split("\\|\\|", 2);
            String sourceId = parts[0];
            String targetId = parts[1];

            List<String> descs = entry.getValue().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .collect(Collectors.toList());

            String idForPrompt = "源实体ID=" + sourceId + "，目标实体ID=" + targetId;
            String summary = extractor.summarize(idForPrompt, descs, maxWords);

            result.add(RelationshipDescriptionSummary.builder()
                    .sourceEntityId(sourceId)
                    .targetEntityId(targetId)
                    .summary(summary)
                    .build());
        }

        return result;
    }
}
