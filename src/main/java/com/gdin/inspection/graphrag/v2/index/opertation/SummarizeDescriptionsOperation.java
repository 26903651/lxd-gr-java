package com.gdin.inspection.graphrag.v2.index.opertation;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.gdin.inspection.graphrag.v2.index.opertation.extract.DescriptionSummaryExtractor;
import com.gdin.inspection.graphrag.v2.models.Entity;
import com.gdin.inspection.graphrag.v2.models.Relationship;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class SummarizeDescriptionsOperation {

    @Resource
    private DescriptionSummaryExtractor extractor;

    /**
     * 对实体做描述摘要，对齐 Python 的 entity_summaries 表：
     * 输入：抽取阶段合并后的 Entity（description 为多条描述按换行拼接）
     * 输出：每个 title -> 一条 summary。
     */
    public List<EntityDescriptionSummary> summarizeEntities(List<Entity> entities, int maxWords) {
        if (CollectionUtil.isEmpty(entities)) return Collections.emptyList();

        // 以 title 为 key 聚合
        Map<String, List<String>> titleToDescriptions = new LinkedHashMap<>();

        for (Entity entity : entities) {
            String title = entity.getTitle();
            String desc = entity.getDescription();
            if (StrUtil.isBlank(title) || StrUtil.isBlank(desc)) continue;

            List<String> pieces = splitDescription(desc);
            if (pieces.isEmpty()) continue;

            titleToDescriptions
                    .computeIfAbsent(title, k -> new ArrayList<>())
                    .addAll(pieces);
        }

        List<EntityDescriptionSummary> result = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : titleToDescriptions.entrySet()) {
            String title = entry.getKey();

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
        if (CollectionUtil.isEmpty(relationships)) return Collections.emptyList();

        // 以 (source, target) 为 key 聚合
        Map<String, List<String>> keyToDescriptions = new LinkedHashMap<>();

        for (Relationship rel : relationships) {
            String source = rel.getSource();
            String target = rel.getTarget();
            String desc = rel.getDescription();
            if (StrUtil.isBlank(source) || StrUtil.isBlank(target) || StrUtil.isBlank(desc)) continue;

            List<String> pieces = splitDescription(desc);
            if (pieces.isEmpty()) continue;

            String key = source + "||" + target;
            keyToDescriptions
                    .computeIfAbsent(key, k -> new ArrayList<>())
                    .addAll(pieces);
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

            // 纯提示用 id，和 Python 一样只是个标签
            String idForPrompt = "源实体=" + sourceId + "，目标实体=" + targetId;
            String summary = extractor.summarize(idForPrompt, descs, maxWords);

            result.add(RelationshipDescriptionSummary.builder()
                    .sourceEntityId(sourceId)
                    .targetEntityId(targetId)
                    .summary(summary)
                    .build());
        }

        return result;
    }

    /**
     * 把 GraphExtractor 合并好的 description（换行拼起来）拆成多条。
     */
    private List<String> splitDescription(String description) {
        if (StrUtil.isBlank(description)) return Collections.emptyList();
        return Arrays.stream(description.split("[\\n；]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
