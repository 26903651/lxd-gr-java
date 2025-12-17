package com.gdin.inspection.graphrag.v2.index.workflows;

import cn.hutool.core.collection.CollectionUtil;
import com.gdin.inspection.graphrag.v2.index.opertation.extract.GraphExtractor;
import com.gdin.inspection.graphrag.v2.index.opertation.SummarizeDescriptionsOperation;
import com.gdin.inspection.graphrag.v2.models.Entity;
import com.gdin.inspection.graphrag.v2.models.EntityDescriptionSummary;
import com.gdin.inspection.graphrag.v2.models.Relationship;
import com.gdin.inspection.graphrag.v2.models.RelationshipDescriptionSummary;
import com.gdin.inspection.graphrag.v2.models.TextUnit;
import com.gdin.inspection.graphrag.v2.util.FinalizeUtils;
import jakarta.annotation.Resource;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 等价于 Python 的 index/workflows/extract_graph.py：
 * 1）调用 GraphExtractor，从 text_units 抽取实体 / 关系并做合并统计
 * 2）复制一份 rawEntities / rawRelationships（在摘要前的版本）
 * 3）调用 SummarizeDescriptionsOperation，对实体 / 关系的描述做 LLM 摘要
 * 4）把摘要写回 Entity/Relationship.description 字段
 * 5）调用 FinalizeUtils 分配 id / human_readable_id
 */
@Slf4j
@Service
public class ExtractGraphWorkflow {

    @Resource
    private GraphExtractor graphExtractor;

    @Resource
    private SummarizeDescriptionsOperation summarizeDescriptionsOperation;

    /**
     * 主入口：对 TextUnit 列表执行「抽图 + 描述摘要 + finalize」。
     *
     * @param textUnits                   文本单元列表
     * @param entityTypes                 实体类型提示字符串（例如："单位, 部门, 岗位, 人员, 制度文件, 法律法规, 问题类型, 业务事项"）
     * @param entitySummaryMaxWords       实体描述摘要长度上限（粗略）
     * @param relationshipSummaryMaxWords 关系描述摘要长度上限（粗略）
     */
    public Result run(List<TextUnit> textUnits,
                      String entityTypes,
                      Integer entitySummaryMaxWords,
                      Integer relationshipSummaryMaxWords) {
        if (CollectionUtil.isEmpty(textUnits)) throw new IllegalStateException("textUnits 不能为空");

        entitySummaryMaxWords = entitySummaryMaxWords == null ? 150 : entitySummaryMaxWords;
        relationshipSummaryMaxWords = relationshipSummaryMaxWords == null ? 150 : relationshipSummaryMaxWords;

        log.info(
                "开始抽取实体和关系：textUnits={}, entityTypes=\"{}\", entitySummaryMaxWords={}, relationshipSummaryMaxWords={}",
                textUnits.size(),
                entityTypes,
                entitySummaryMaxWords,
                relationshipSummaryMaxWords
        );

        // 1. 调 GraphExtractor：等价 Python extract_graph.extract_graph() 的合并结果
        GraphExtractor.ExtractionResult extractionResult = graphExtractor.extract(textUnits, entityTypes);

        List<Entity> extractedEntities = Optional.ofNullable(extractionResult.getEntities()).orElseGet(Collections::emptyList);
        List<Relationship> extractedRelationships = Optional.ofNullable(extractionResult.getRelationships()).orElseGet(Collections::emptyList);

        if (extractedEntities.isEmpty()) {
            throw new IllegalStateException("实体抽取失败：未检测到任何实体");
        }
        if (extractedRelationships.isEmpty()) {
            throw new IllegalStateException("关系抽取失败：未检测到任何关系");
        }

        // 2. rawEntities / rawRelationships：摘要前的拷贝（等价于 DataFrame.copy()）
        List<Entity> rawEntities = new ArrayList<>(extractedEntities);
        List<Relationship> rawRelationships = new ArrayList<>(extractedRelationships);

        // 3. 调 SummarizeDescriptionsOperation：等价 Python summarize_descriptions(...)
        List<EntityDescriptionSummary> entitySummaries = summarizeDescriptionsOperation.summarizeEntities(extractedEntities, entitySummaryMaxWords);
        List<RelationshipDescriptionSummary> relationshipSummaries = summarizeDescriptionsOperation.summarizeRelationships(extractedRelationships, relationshipSummaryMaxWords);

        // 4. 建索引：title -> summary；(source,target) -> summary
        Map<String, String> titleToSummary = entitySummaries.stream()
                .filter(es -> es.getTitle() != null)
                .collect(Collectors.toMap(
                        EntityDescriptionSummary::getTitle,
                        EntityDescriptionSummary::getSummary,
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

        // 5. 把摘要写回实体 description，然后 finalize（分配 id / human_readable_id）
        List<Entity> entitiesWithSummary = extractedEntities.stream()
                .map(e -> {
                    String title = e.getTitle();
                    String summary = titleToSummary.get(title);
                    String finalDescription = summary != null ? summary : e.getDescription();
                    return Entity.builder()
                            .id(null)                    // 交给 FinalizeUtils 统一生成
                            .humanReadableId(null)
                            .title(e.getTitle())
                            .type(e.getType())
                            .description(finalDescription)
                            .textUnitIds(e.getTextUnitIds())
                            .frequency(e.getFrequency())
                            .degree(e.getDegree())
                            .x(e.getX())
                            .y(e.getY())
                            .build();
                })
                .collect(Collectors.toList());

        List<Entity> finalizedEntities = FinalizeUtils.finalizeEntities(entitiesWithSummary);

        // 6. 把摘要写回关系 description，然后 finalize
        List<Relationship> relationshipsWithSummary = extractedRelationships.stream()
                .map(r -> {
                    String key = buildRelKey(r.getSource(), r.getTarget());
                    String summary = relKeyToSummary.get(key);
                    String finalDescription = summary != null ? summary : r.getDescription();
                    return Relationship.builder()
                            .id(null)
                            .humanReadableId(null)
                            .source(r.getSource())
                            .target(r.getTarget())
                            .description(finalDescription)
                            .weight(r.getWeight())
                            .combinedDegree(r.getCombinedDegree())
                            .textUnitIds(r.getTextUnitIds())
                            .build();
                })
                .collect(Collectors.toList());

        List<Relationship> finalizedRelationships =
                FinalizeUtils.finalizeRelationships(relationshipsWithSummary);

        // 7. 返回结果：等价 Python extract_graph() 返回的四个 DataFrame
        return new Result(finalizedEntities, finalizedRelationships, rawEntities, rawRelationships);
    }

    private static String buildRelKey(String sourceId, String targetId) {
        String s = sourceId == null ? "" : sourceId;
        String t = targetId == null ? "" : targetId;
        return s + "||" + t;
    }

    /**
     * 对应 Python extract_graph workflow 返回的四个 DataFrame。
     */
    @Getter
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
    }
}
