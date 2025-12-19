package com.gdin.inspection.graphrag.v2.index.opertation;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.gdin.inspection.graphrag.v2.index.opertation.extract.GraphExtractor;
import com.gdin.inspection.graphrag.v2.index.strategy.ExtractGraphStrategy;
import com.gdin.inspection.graphrag.v2.models.Entity;
import com.gdin.inspection.graphrag.v2.models.Relationship;
import com.gdin.inspection.graphrag.v2.models.TextUnit;
import jakarta.annotation.Resource;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 对齐 Python 版的图抽取逻辑：
 * - 在 Java 侧完成合并：
 *   - 实体按 (title, type) groupby：
 *       description = 多个描述用换行拼接
 *       text_unit_ids = source_id 列表
 *       frequency     = 出现次数
 *   - 关系按 (source, target) groupby：
 *       description = 多个描述用换行拼接
 *       text_unit_ids = source_id 列表
 *       weight        = 每条记录 weight 的和（解析失败时视为 1.0）
 * - 然后额外计算：
 *   - Entity.degree = 该实体参与的边数量
 *   - Relationship.combined_degree = degree(source) + degree(target)
 * id / human_readable_id 留给后续 finalize 步骤统一赋值。
 */
@Slf4j
@Service
public class ExtractGraphOperation {

    @Resource
    private GraphExtractor graphExtractor;

    /**
     * 返回合并并带统计信息的实体和关系列表。
     */
    public Result extractGraph(
            List<TextUnit> textUnits,
            List<String> entitySpecs,
            ExtractGraphStrategy strategy
    ) {
        if (CollectionUtil.isEmpty(textUnits)) throw new IllegalArgumentException("textUnits 不能为空");
        if (CollectionUtil.isEmpty(entitySpecs)) throw new IllegalArgumentException("entitySpecs 不能为空");

        GraphExtractor.Result graphExtratorResult = graphExtractor.extract(textUnits, entitySpecs, strategy);
        List<GraphExtractor.RawEntity> rawEntities = graphExtratorResult.getEntities();
        List<GraphExtractor.RawRelationship> rawRelationships = graphExtratorResult.getRelationships();
        if(CollectionUtil.isEmpty(rawEntities) || CollectionUtil.isEmpty(rawRelationships)) return new Result(List.of(), List.of());

        // 对齐 Python extract_graph._merge_entities / _merge_relationships
        List<Entity> mergedEntities = mergeEntities(rawEntities);
        List<Relationship> mergedRelationships = mergeRelationships(rawRelationships);

        // 根据合并后的边，计算节点度数和 edge.combined_degree
        Map<String, Integer> degreeMap = computeDegrees(mergedRelationships);
        List<Entity> finalEntities = applyDegreesToEntities(mergedEntities, degreeMap);
        List<Relationship> finalRelationships = applyCombinedDegreeToRelationships(mergedRelationships, degreeMap);

        return new Result(finalEntities, finalRelationships);
    }



    private String normalizeKey(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase(Locale.ROOT);
    }

    // ----------------------------------------------------------------------
    // Entity 合并：按 (title, type) 聚合，统计 frequency / text_unit_ids / description(list)
    // 对齐 Python extract_graph._merge_entities
    // ----------------------------------------------------------------------

    private List<Entity> mergeEntities(List<GraphExtractor.RawEntity> rawEntities) {
        Map<String, EntityBuilderHelper> map = new LinkedHashMap<>();

        for (GraphExtractor.RawEntity re : rawEntities) {
            String key = normalizeKey(re.getTitle()) + "||" + normalizeKey(re.getType());
            EntityBuilderHelper helper = map.computeIfAbsent(key, k -> new EntityBuilderHelper(re.getTitle(), re.getType()));
            helper.addMention(re.getDescription(), re.getTextUnitId());
        }

        List<Entity> entities = new ArrayList<>();
        for (EntityBuilderHelper helper : map.values()) {
            String description = String.join("\n", helper.descriptions); // 对齐 Python：用换行拼接
            entities.add(
                    Entity.builder()
                            .id(null)
                            .humanReadableId(null)
                            .title(helper.title)
                            .type(helper.type)
                            .description(description)
                            .textUnitIds(new ArrayList<>(helper.textUnitIds))
                            .frequency(helper.frequency)
                            .degree(null)   // 下面再填
                            .x(null)
                            .y(null)
                            .build()
            );
        }

        return entities;
    }

    // ----------------------------------------------------------------------
    // Relationship 合并：按 (source, target) 聚合，计算 weight / text_unit_ids / description(list)
    // 对齐 Python extract_graph._merge_relationships
    // ----------------------------------------------------------------------

    private List<Relationship> mergeRelationships(List<GraphExtractor.RawRelationship> rawRelationships) {
        Map<String, RelationshipBuilderHelper> map = new LinkedHashMap<>();

        for (GraphExtractor.RawRelationship rr : rawRelationships) {
            String key = normalizeKey(rr.getSource()) + "||" + normalizeKey(rr.getTarget());
            RelationshipBuilderHelper helper =
                    map.computeIfAbsent(key, k -> new RelationshipBuilderHelper(rr.getSource(), rr.getTarget()));
            helper.addInstance(rr.getDescription(), rr.getTextUnitId(), rr.getWeight());
        }

        List<Relationship> relationships = new ArrayList<>();
        for (RelationshipBuilderHelper helper : map.values()) {
            String description = String.join("\n", helper.descriptions);
            double weightSum = helper.weightSum; // 完全对齐 Python：weight = 所有边的 weight 之和

            relationships.add(
                    Relationship.builder()
                            .id(null)
                            .humanReadableId(null)
                            .source(helper.source)
                            .target(helper.target)
                            .description(description)
                            .weight(weightSum)
                            .combinedDegree(null)  // 下面再填
                            .textUnitIds(new ArrayList<>(helper.textUnitIds))
                            .build()
            );
        }

        return relationships;
    }

    // ----------------------------------------------------------------------
    // 计算度数 & combined_degree（Python 后续 cluster 流程里才算，我们在这里提前算好）
    // ----------------------------------------------------------------------

    private Map<String, Integer> computeDegrees(List<Relationship> relationships) {
        Map<String, Integer> degreeMap = new HashMap<>();
        for (Relationship r : relationships) {
            String sKey = normalizeKey(r.getSource());
            String tKey = normalizeKey(r.getTarget());
            degreeMap.put(sKey, degreeMap.getOrDefault(sKey, 0) + 1);
            degreeMap.put(tKey, degreeMap.getOrDefault(tKey, 0) + 1);
        }
        return degreeMap;
    }

    private List<Entity> applyDegreesToEntities(List<Entity> entities,
                                                Map<String, Integer> degreeMap) {
        return entities.stream()
                .map(e -> {
                    String key = normalizeKey(e.getTitle());
                    int deg = degreeMap.getOrDefault(key, 0);
                    return Entity.builder()
                            .id(e.getId())
                            .humanReadableId(e.getHumanReadableId())
                            .title(e.getTitle())
                            .type(e.getType())
                            .description(e.getDescription())
                            .textUnitIds(e.getTextUnitIds())
                            .frequency(e.getFrequency())
                            .degree(deg)
                            .x(e.getX())
                            .y(e.getY())
                            .build();
                })
                .collect(Collectors.toList());
    }

    private List<Relationship> applyCombinedDegreeToRelationships(List<Relationship> relationships,
                                                                  Map<String, Integer> degreeMap) {
        return relationships.stream()
                .map(r -> {
                    String sKey = normalizeKey(r.getSource());
                    String tKey = normalizeKey(r.getTarget());
                    int sDeg = degreeMap.getOrDefault(sKey, 0);
                    int tDeg = degreeMap.getOrDefault(tKey, 0);
                    double combined = sDeg + tDeg;
                    return Relationship.builder()
                            .id(r.getId())
                            .humanReadableId(r.getHumanReadableId())
                            .source(r.getSource())
                            .target(r.getTarget())
                            .description(r.getDescription())
                            .weight(r.getWeight())
                            .combinedDegree(combined)
                            .textUnitIds(r.getTextUnitIds())
                            .build();
                })
                .collect(Collectors.toList());
    }

    private static class EntityBuilderHelper {
        final String title;
        final String type;
        final List<String> descriptions = new ArrayList<>();
        final Set<String> textUnitIds = new LinkedHashSet<>();
        int frequency = 0;

        EntityBuilderHelper(String title, String type) {
            this.title = title;
            this.type = type;
        }

        void addMention(String description, String textUnitId) {
            frequency += 1;
            if (StrUtil.isNotBlank(description) && !descriptions.contains(description)) {
                descriptions.add(description);
            }
            if (StrUtil.isNotBlank(textUnitId)) {
                textUnitIds.add(textUnitId);
            }
        }
    }

    private static class RelationshipBuilderHelper {
        final String source;
        final String target;
        final List<String> descriptions = new ArrayList<>();
        final Set<String> textUnitIds = new LinkedHashSet<>();
        int instanceCount = 0;
        double weightSum = 0.0;

        RelationshipBuilderHelper(String source, String target) {
            this.source = source;
            this.target = target;
        }

        void addInstance(String description, String textUnitId, double weight) {
            instanceCount += 1;
            weightSum += weight;
            if (StrUtil.isNotBlank(description) && !descriptions.contains(description)) {
                descriptions.add(description);
            }
            if (StrUtil.isNotBlank(textUnitId)) {
                textUnitIds.add(textUnitId);
            }
        }
    }

    @Value
    public static class Result {
        List<Entity> entities;
        List<Relationship> relationships;
    }
}
