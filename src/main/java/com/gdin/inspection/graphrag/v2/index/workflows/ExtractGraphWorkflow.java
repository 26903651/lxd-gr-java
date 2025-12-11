package com.gdin.inspection.graphrag.v2.index.workflows;

import cn.hutool.core.collection.CollectionUtil;
import com.gdin.inspection.graphrag.v2.index.GraphExtractor;
import com.gdin.inspection.graphrag.v2.index.SummarizeDescriptionsOperation;
import com.gdin.inspection.graphrag.v2.models.*;
import com.gdin.inspection.graphrag.v2.util.FinalizeUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 等价于 Python 的 index/workflows/extract_graph.py：
 * - 调用 GraphExtractor 抽取实体 / 关系
 * - 保留一份 rawEntities / rawRelationships（无 summary）
 * - 调用 SummarizeDescriptionsOperation 生成描述摘要
 * - 按 title / (source, target) 把 summary merge 回实体 / 关系
 */
@Service
public class ExtractGraphWorkflow {

    @Resource
    private GraphExtractor graphExtractor;

    @Resource
    private SummarizeDescriptionsOperation summarizeDescriptionsOperation;

    /**
     * 主入口：对 TextUnit 列表执行「抽图 + 摘要」。
     *
     * @param textUnits                    文本单元列表
     * @param entityTypes                  实体类型提示字符串（例如："单位, 部门, 岗位, 人员, 制度文件, 法律法规, 问题类型, 业务事项"）
     * @param entitySummaryMaxWords        实体摘要最大字数（粗略控制）
     * @param relationshipSummaryMaxWords  关系摘要最大字数（粗略控制）
     */
    public Result run(List<TextUnit> textUnits,
                      String entityTypes,
                      int entitySummaryMaxWords,
                      int relationshipSummaryMaxWords) {

        if (CollectionUtil.isEmpty(textUnits)) throw new IllegalArgumentException("textUnits 不能为空");

        // 1. 调 GraphExtractor：等价于 Python extractor(...)
        GraphExtractor.ExtractionResult extractionResult = graphExtractor.extract(textUnits, entityTypes);

        List<Entity> extractedEntities = safeList(extractionResult.getEntities());
        List<Relationship> extractedRelationships = safeList(extractionResult.getRelationships());

        if (extractedEntities.isEmpty()) throw new IllegalStateException("实体抽取失败：未检测到任何实体");
        if (extractedRelationships.isEmpty()) throw new IllegalStateException("关系抽取失败：未检测到任何关系");

        // 2. rawEntities / rawRelationships：在加 summary 前拷贝一份（等价于 DataFrame.copy()）
        List<Entity> rawEntities = new ArrayList<>(extractedEntities);
        List<Relationship> rawRelationships = new ArrayList<>(extractedRelationships);

        // 3. 调 SummarizeDescriptionsOperation：等价于 Python summarize_descriptions(...)
        List<EntityDescriptionSummary> entitySummaries = summarizeDescriptionsOperation.summarizeEntities(extractedEntities, entitySummaryMaxWords);
        List<RelationshipDescriptionSummary> relationshipSummaries = summarizeDescriptionsOperation.summarizeRelationships(extractedRelationships, relationshipSummaryMaxWords);

        // 4. 建索引：title -> summary；(source,target) -> summary
        Map<String, String> titleToSummary = entitySummaries.stream()
                .filter(es -> es.getTitle() != null)
                .collect(Collectors.toMap(
                        EntityDescriptionSummary::getTitle,
                        EntityDescriptionSummary::getSummary,
                        // 如果出现重复 title，简单选择第一条（正常情况下不会有）
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        Map<String, String> relKeyToSummary = relationshipSummaries.stream()
                .filter(rs -> rs.getSourceEntityId() != null && rs.getTargetEntityId() != null)
                .collect(Collectors.toMap(
                        rs -> buildRelKey(rs.getSourceEntityId(), rs.getTargetEntityId()),
                        RelationshipDescriptionSummary::getSummary,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        // 5. 把 summary merge 回实体，然后执行 finalize（分配 id / human_readable_id）
        List<Entity> entitiesWithSummary = extractedEntities.stream()
                .map(e -> {
                    String title = e.getTitle();
                    String summary = titleToSummary.getOrDefault(title, e.getSummary());
                    return Entity.builder()
                            .id(null)  // 这里先不设 id，交给 FinalizeUtils 统一生成
                            .humanReadableId(null)
                            .title(e.getTitle())
                            .type(e.getType())
                            .descriptionList(e.getDescriptionList())
                            .summary(summary)
                            .aliases(e.getAliases())
                            .sourceTextUnitIds(e.getSourceTextUnitIds())
                            .metadata(e.getMetadata())
                            .createdAt(e.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());

        // 关键：真正 finalize，一次性生成 UUID 和 human_readable_id
        List<Entity> finalizedEntities = FinalizeUtils.finalizeEntities(entitiesWithSummary);

        // 6. 把 summary merge 回关系，然后执行 finalize（分配 id / human_readable_id）
        List<Relationship> relationshipsWithSummary = extractedRelationships.stream()
                .map(r -> {
                    String key = buildRelKey(r.getSource(), r.getTarget());
                    String summary = relKeyToSummary.getOrDefault(key, r.getSummary());
                    return Relationship.builder()
                            .id(null)
                            .humanReadableId(null)
                            .source(r.getSource())
                            .target(r.getTarget())
                            .predicate(r.getPredicate())
                            .descriptionList(r.getDescriptionList())
                            .summary(summary)
                            .sourceTextUnitIds(r.getSourceTextUnitIds())
                            .metadata(r.getMetadata())
                            .createdAt(r.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());

        List<Relationship> finalizedRelationships = FinalizeUtils.finalizeRelationships(relationshipsWithSummary);

        // 7. 返回结果
        return new Result(finalizedEntities, finalizedRelationships, rawEntities, rawRelationships);
    }

    private static <T> List<T> safeList(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }

    private static String buildRelKey(String sourceId, String targetId) {
        String s = sourceId == null ? "" : sourceId;
        String t = targetId == null ? "" : targetId;
        return s + "||" + t;
    }

    /**
     * 对应 Python extract_graph workflow 返回的四个 DataFrame。
     */
    public static class Result {
        private List<Entity> entities;
        private List<Relationship> relationships;
        private List<Entity> rawEntities;
        private List<Relationship> rawRelationships;

        public Result() {}

        public Result(List<Entity> entities,
                      List<Relationship> relationships,
                      List<Entity> rawEntities,
                      List<Relationship> rawRelationships) {
            this.entities = entities;
            this.relationships = relationships;
            this.rawEntities = rawEntities;
            this.rawRelationships = rawRelationships;
        }

        public List<Entity> getEntities() {
            return entities;
        }

        public List<Relationship> getRelationships() {
            return relationships;
        }

        public List<Entity> getRawEntities() {
            return rawEntities;
        }

        public List<Relationship> getRawRelationships() {
            return rawRelationships;
        }
    }
}
