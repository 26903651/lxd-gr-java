package com.gdin.inspection.graphrag.v2.index.opertation.extract;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.gdin.inspection.graphrag.assistant.ThinkAssistant;
import com.gdin.inspection.graphrag.service.AssistantGenerator;
import com.gdin.inspection.graphrag.util.SseUtil;
import com.gdin.inspection.graphrag.v2.index.prompts.ExtractGraphPromptsZh;
import com.gdin.inspection.graphrag.v2.index.strategy.ExtractGraphStrategy;
import com.gdin.inspection.graphrag.v2.models.Entity;
import com.gdin.inspection.graphrag.v2.models.Relationship;
import com.gdin.inspection.graphrag.v2.models.TextUnit;
import com.gdin.inspection.graphrag.v2.util.PyStrUtil;
import dev.langchain4j.service.TokenStream;
import jakarta.annotation.Resource;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 对齐 Python 版的图抽取逻辑：
 *
 * - 对每个 TextUnit 调用 LLM 抽取 ("entity", ...) 和 ("relationship", ...) 记录
 * - 支持 CONTINUE / LOOP 多轮 gleaning
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
 *
 * id / human_readable_id 留给后续 finalize 步骤统一赋值。
 */
@Slf4j
@Service
public class GraphExtractor {

    @Resource
    private AssistantGenerator assistantGenerator;

    // Python DEFAULT_* 常量对齐
    public static final String DEFAULT_TUPLE_DELIMITER = "<|>";
    public static final String DEFAULT_RECORD_DELIMITER = "##";
    public static final String DEFAULT_COMPLETION_DELIMITER = "<|COMPLETE|>";

    // 默认实体类型（保留第6点：中文化现状）
    public static final List<String> DEFAULT_ENTITY_TYPES_ZH =
            List.of("组织", "人员", "地理位置", "事件");

    // Python 默认键名对齐
    private static final String KEY_ENTITY_TYPES = "entity_types";
    private static final String KEY_TUPLE_DELIMITER = "tuple_delimiter";
    private static final String KEY_RECORD_DELIMITER = "record_delimiter";
    private static final String KEY_COMPLETION_DELIMITER = "completion_delimiter";

    /**
     * 对一批 TextUnit 执行图抽取，返回合并并带统计信息的实体和关系列表。
     */
    public ExtractionResult extract(
            List<TextUnit> textUnits,
            List<String> entitySpecs,
            ExtractGraphStrategy strategy
    ) {
        if (CollectionUtil.isEmpty(textUnits)) return new ExtractionResult(List.of(), List.of());
        if (strategy == null) throw new IllegalArgumentException("strategy is required for graph extraction");

        List<String> specs = CollectionUtil.isEmpty(entitySpecs) ? DEFAULT_ENTITY_TYPES_ZH : entitySpecs;

        List<RawEntity> rawEntities = new ArrayList<>();
        List<RawRelationship> rawRelationships = new ArrayList<>();

        String extractionPrompt = StrUtil.blankToDefault(strategy.getExtractionPrompt(), ExtractGraphPromptsZh.GRAPH_EXTRACTION_PROMPT);

        String tupleDelimiter = StrUtil.blankToDefault(strategy.getTupleDelimiter(), DEFAULT_TUPLE_DELIMITER);
        String recordDelimiter = StrUtil.blankToDefault(strategy.getRecordDelimiter(), DEFAULT_RECORD_DELIMITER);
        String completionDelimiter = StrUtil.blankToDefault(strategy.getCompletionDelimiter(), DEFAULT_COMPLETION_DELIMITER);

        int maxGleanings = (strategy.getMaxGleanings() == null) ? 0 : Math.max(strategy.getMaxGleanings(), 0);

        // 对齐 Python: entity_spec = str(list)
        String entityTypes = toPythonListString(specs);

        Map<String, String> promptArgs = new HashMap<>();
        promptArgs.put(KEY_ENTITY_TYPES, entityTypes);
        promptArgs.put(KEY_TUPLE_DELIMITER, tupleDelimiter);
        promptArgs.put(KEY_RECORD_DELIMITER, recordDelimiter);
        promptArgs.put(KEY_COMPLETION_DELIMITER, completionDelimiter);

        for (TextUnit tu : textUnits) {
            String rawOutput = extractForSingleTextUnit(extractionPrompt, promptArgs, tu.getText(), maxGleanings);
            ParsedResult parsed = parseRecords(rawOutput, tu.getId(), promptArgs);
            rawEntities.addAll(parsed.entities);
            rawRelationships.addAll(parsed.relationships);
        }

        // 对齐 Python extract_graph._merge_entities / _merge_relationships
        List<Entity> mergedEntities = mergeEntities(rawEntities);
        List<Relationship> mergedRelationships = mergeRelationships(rawRelationships);

        // 根据合并后的边，计算节点度数和 edge.combined_degree
        Map<String, Integer> degreeMap = computeDegrees(mergedRelationships);
        List<Entity> finalEntities = applyDegreesToEntities(mergedEntities, degreeMap);
        List<Relationship> finalRelationships = applyCombinedDegreeToRelationships(mergedRelationships, degreeMap);

        return new ExtractionResult(finalEntities, finalRelationships);
    }

    /**
     * 对单个 TextUnit 执行完整的「首轮抽取 + 多轮 CONTINUE + LOOP 判断」。
     */
    private String extractForSingleTextUnit(
            String extractionPrompt,
            Map<String, String> promptArgs,
            String text,
            int maxGleanings
    ) {
        // 1. 构造 first prompt（包含实体类型 + 各种分隔符配置）
        String recordDelimiter = promptArgs.get(KEY_RECORD_DELIMITER);
        String completionDelimiter = promptArgs.get(KEY_COMPLETION_DELIMITER);
        String firstPrompt = extractionPrompt
                .replace("{input_text}", text)
                .replace("{entity_types}", promptArgs.get(KEY_ENTITY_TYPES))
                .replace("{tuple_delimiter}", promptArgs.get(KEY_TUPLE_DELIMITER))
                .replace("{record_delimiter}", recordDelimiter)
                .replace("{completion_delimiter}", completionDelimiter);

        // 2. 创建 Assistant 和 memoryId，同一 TextUnit 全流程复用
        ThinkAssistant assistant = assistantGenerator.createTempAssistant(ThinkAssistant.class);
        String memoryId = IdUtil.getSnowflakeNextIdStr();

        // 3. 第一次调用：发送需要抽取的文本
        String results = callOnce(assistant, memoryId, firstPrompt);
        StringBuilder graph = new StringBuilder(stripSuffix(results, completionDelimiter));

        // 4. CONTINUE + LOOP，多轮 gleaning，对齐 Python 语义
        for (int i = 0; i < maxGleanings; i++) {
            String ext = callOnce(assistant, memoryId, ExtractGraphPromptsZh.CONTINUE_PROMPT);
            if (StrUtil.isNotBlank(ext)) {
                graph.append(recordDelimiter).append(stripSuffix(ext, completionDelimiter));
            }

            // 最后一轮就不再问 LOOP
            if (i >= maxGleanings - 1) break;

            String loopResp = callOnce(assistant, memoryId, ExtractGraphPromptsZh.LOOP_PROMPT);
            String trimmed = loopResp == null ? "" : loopResp.trim().toUpperCase(Locale.ROOT);
            if(!"Y".equals(trimmed)) break;
        }

        return graph.toString();
    }

    /**
     * 一次性调用大模型。
     */
    private String callOnce(ThinkAssistant assistant, String memoryId, String userPrompt) {
        TokenStream tokenStream = assistant.streamChat(memoryId, userPrompt);
        try {
            return SseUtil.getResponseWithoutThink(null, tokenStream, memoryId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("调用大模型被中断", e);
        }
    }

    private String stripSuffix(String s, String suffix) {
        if (s == null) return "";
        String t = s.trim();
        if (StrUtil.isBlank(suffix)) return t;
        if (t.endsWith(suffix)) return t.substring(0, t.length() - suffix.length()).trim();
        return t;
    }

    private String stripPrefix(String s, String prefix) {
        if (s == null) return "";
        String t = s.trim();
        if (StrUtil.isBlank(prefix)) return t;
        if (t.startsWith(prefix)) return t.substring(prefix.length()).trim();
        return t;
    }

    /** 模拟 Python str(list) 的展示：['a', 'b'] */
    private String toPythonListString(List<String> list) {
        if (list == null) return "[]";
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        boolean first = true;
        for (String v : list) {
            if (v == null) continue;
            if (!first) sb.append(", ");
            sb.append("'").append(v.replace("'", "\\'")).append("'");
            first = false;
        }
        sb.append("]");
        return sb.toString();
    }

    // ----------------------------------------------------------------------
    // 解析 LLM 输出，生成“原始”实体 / 关系记录（对应 graph_extractor._process_results 的反向）
    // ----------------------------------------------------------------------

    private ParsedResult parseRecords(String combined, String textUnitId, Map<String, String> promptArgs) {
        String recordDelimiter = promptArgs.get(KEY_RECORD_DELIMITER);
        String completionDelimiter = promptArgs.get(KEY_COMPLETION_DELIMITER);
        String tupleDelimiter = promptArgs.get(KEY_TUPLE_DELIMITER);

        List<RawEntity> entities = new ArrayList<>();
        List<RawRelationship> relationships = new ArrayList<>();

        if (StrUtil.isBlank(combined)) return new ParsedResult(entities, relationships);

        // 去掉 completionDelimiter
        String body = stripSuffix(StrUtil.blankToDefault(combined, ""), completionDelimiter);
        String[] records = body.trim().split(Pattern.quote(recordDelimiter));

        for (String rec : records) {
            String record = rec.trim();
            if (record.isEmpty()) continue;

            // 去掉最外层括号
            record = stripPrefix(record, "(");
            record = stripSuffix(record, ")");
            if (record.isEmpty()) continue;

            String[] fields = record.split(Pattern.quote(tupleDelimiter));
            if (fields.length == 0) continue;

            String tag = PyStrUtil.cleanStr(fields[0]).toLowerCase(Locale.ROOT);

            if ("entity".equals(tag) && fields.length >= 4) {
                String name = PyStrUtil.cleanStr(fields[1]);
                String type = PyStrUtil.cleanStr(fields[2]);
                String desc = PyStrUtil.cleanStr(fields[3]);
                if (StrUtil.isBlank(name)) continue;
                entities.add(new RawEntity(name, type, desc, textUnitId));
            } else if ("relationship".equals(tag) && fields.length >= 4) {
                String source = PyStrUtil.cleanStr(fields[1]);
                String target = PyStrUtil.cleanStr(fields[2]);
                String desc = PyStrUtil.cleanStr(fields[3]);
                if (StrUtil.isBlank(source) || StrUtil.isBlank(target)) continue;

                double weight = 1.0;
                if (fields.length >= 5) {
                    String strengthStr = PyStrUtil.cleanStr(fields[fields.length - 1]);
                    try {
                        weight = Double.parseDouble(strengthStr);
                    } catch (NumberFormatException ignored) {}
                }

                relationships.add(new RawRelationship(source, target, desc, textUnitId, weight));
            }
        }
        return new ParsedResult(entities, relationships);
    }

    private String normalizeKey(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase(Locale.ROOT);
    }

    // ----------------------------------------------------------------------
    // Entity 合并：按 (title, type) 聚合，统计 frequency / text_unit_ids / description(list)
    // 对齐 Python extract_graph._merge_entities
    // ----------------------------------------------------------------------

    private List<Entity> mergeEntities(List<RawEntity> rawEntities) {
        Map<String, EntityBuilderHelper> map = new LinkedHashMap<>();

        for (RawEntity re : rawEntities) {
            String key = normalizeKey(re.title) + "||" + normalizeKey(re.type);
            EntityBuilderHelper helper = map.computeIfAbsent(key, k -> new EntityBuilderHelper(re.title, re.type));
            helper.addMention(re.description, re.textUnitId);
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

    private List<Relationship> mergeRelationships(List<RawRelationship> rawRelationships) {
        Map<String, RelationshipBuilderHelper> map = new LinkedHashMap<>();

        for (RawRelationship rr : rawRelationships) {
            String key = normalizeKey(rr.source) + "||" + normalizeKey(rr.target);
            RelationshipBuilderHelper helper =
                    map.computeIfAbsent(key, k -> new RelationshipBuilderHelper(rr.source, rr.target));
            helper.addInstance(rr.description, rr.textUnitId, rr.weight);
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

    // ---- 内部辅助类型：原始记录 + 合并辅助器 ----
    private record RawEntity(String title, String type, String description, String textUnitId) {}

    private record RawRelationship(String source, String target, String description, String textUnitId, double weight) {}

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

    private static class ParsedResult {
        final List<RawEntity> entities;
        final List<RawRelationship> relationships;

        ParsedResult(List<RawEntity> entities, List<RawRelationship> relationships) {
            this.entities = entities;
            this.relationships = relationships;
        }
    }

    @Getter
    @NoArgsConstructor
    public static class ExtractionResult {
        private List<Entity> entities;
        private List<Relationship> relationships;

        public ExtractionResult(List<Entity> entities, List<Relationship> relationships) {
            this.entities = entities;
            this.relationships = relationships;
        }
    }
}
