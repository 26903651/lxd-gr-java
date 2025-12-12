package com.gdin.inspection.graphrag.v2.index;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.gdin.inspection.graphrag.assistant.ThinkAssistant;
import com.gdin.inspection.graphrag.service.AssistantGenerator;
import com.gdin.inspection.graphrag.util.SseUtil;
import com.gdin.inspection.graphrag.v2.index.prompts.GraphExtractionPromptsZh;
import com.gdin.inspection.graphrag.v2.models.Entity;
import com.gdin.inspection.graphrag.v2.models.Relationship;
import com.gdin.inspection.graphrag.v2.models.TextUnit;
import dev.langchain4j.service.TokenStream;
import jakarta.annotation.Resource;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * - 对齐 Python graph_extractor.py 的 GRAPH_EXTRACTION + CONTINUE + LOOP 逻辑
 * - 仅实现 tuple 形式的 graph extraction（与 Python 默认 CLI 行为一致）
 */
@Slf4j
@Service
public class GraphExtractor {

    @Resource
    private AssistantGenerator assistantGenerator;

    // 对应 Python 配置中的 record_delimiter / tuple_delimiter / completion_delimiter
    // 你后面可以改成 @Value 注入，保证和 Python 配置完全一致。
    private final String recordDelimiter = "##RECORD##";
    private final String tupleDelimiter = "||";
    private final String completionDelimiter = "##END##";

    // 最大 gleaning 次数，对应 Python 的 max_gleanings
    private final int maxGleanings = 2;

    /**
     * 对一批 TextUnit 执行图抽取，返回去重后的实体和关系列表。
     *
     * @param textUnits   文本单元列表
     * @param entityTypes 实体类型集合描述（例如："法律法规, 制度文件, 单位, 岗位, 人员, 问题类别"）
     */
    public ExtractionResult extract(List<TextUnit> textUnits, String entityTypes) {
        List<Entity> allEntities = new ArrayList<>();
        List<Relationship> allRelationships = new ArrayList<>();

        for (TextUnit tu : textUnits) {
            String rawOutput = extractForSingleTextUnit(tu.getText(), entityTypes);
            ParsedResult parsed = parseRecords(rawOutput, tu.getId());
            allEntities.addAll(parsed.entities);
            allRelationships.addAll(parsed.relationships);
        }

        List<Entity> mergedEntities = mergeEntities(allEntities);
        List<Relationship> mergedRelationships = mergeRelationships(allRelationships);

        return new ExtractionResult(mergedEntities, mergedRelationships);
    }

    /**
     * 对单个 TextUnit 执行完整的「首轮抽取 + 多轮 CONTINUE + LOOP 判断」。
     * 行为对齐 Python 的 _process_document。
     */
    private String extractForSingleTextUnit(String text, String entityTypes) {
        // 1. 构造 system prompt（包含实体类型 + 各种分隔符配置）
        String systemPrompt = GraphExtractionPromptsZh.buildSystemPrompt(
                entityTypes,
                recordDelimiter,
                tupleDelimiter,
                completionDelimiter
        );

        // 2. 创建 Assistant 和 memoryId，同一 TextUnit 全流程复用
        ThinkAssistant assistant = assistantGenerator.createTempAssistant(ThinkAssistant.class, systemPrompt);
        String memoryId = IdUtil.getSnowflakeNextIdStr();

        // 3. 第一次调用：发送需要抽取的文本
        String initialUserPrompt = buildInitialUserPrompt(text);
        String result = callOnce(assistant, memoryId, initialUserPrompt);

        // 4. 若允许 gleanings，则按 Python 逻辑执行 CONTINUE + LOOP
        for (int i = 0; i < maxGleanings; i++) {
            // CONTINUE_PROMPT：让模型继续补抽
            String cont = callOnce(assistant, memoryId, GraphExtractionPromptsZh.CONTINUE_PROMPT_ZH);
            if (cont != null && !cont.isBlank()) {
                result += "\n" + cont;
            }

            // 已到最大次数，则不再询问 LOOP，直接退出
            if (i >= maxGleanings - 1) {
                break;
            }

            // LOOP_PROMPT：问模型是否还需要继续
            String loopResp = callOnce(assistant, memoryId, GraphExtractionPromptsZh.LOOP_PROMPT_ZH);
            String trimmed = loopResp == null ? "" : loopResp.trim().toUpperCase(Locale.ROOT);
            if (!"Y".equals(trimmed)) {
                break;
            }
        }

        return result;
    }

    /**
     * 首轮抽取时的 user prompt：只负责提供文本本身。
     */
    private String buildInitialUserPrompt(String text) {
        return """
下面是需要进行实体与关系抽取的文本内容：
---------------- 文本开始 ----------------
%s
---------------- 文本结束 ----------------

请严格按照 system prompt 中的规则与格式，对上述文本执行实体与关系抽取，只输出记录列表。
""".formatted(text);
    }

    /**
     * 一次性调用大模型，完全按你给的接口来使用。
     */
    private String callOnce(ThinkAssistant assistant, String memoryId, String userPrompt) {
        TokenStream tokenStream = assistant.streamChat(memoryId, userPrompt);
        try {
            // 按你给的示例：直接用 SseUtil 去掉 <think></think> 包裹的内容
            return SseUtil.getResponseWithoutThink(null, tokenStream, memoryId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("调用大模型被中断", e);
        }
    }

    // ---------------- 解析与合并：对齐 Python _process_results 与去重逻辑 ----------------

    private ParsedResult parseRecords(String combined, String textUnitId) {
        List<Entity> entities = new ArrayList<>();
        List<Relationship> relationships = new ArrayList<>();

        if (StrUtil.isBlank(combined)) return new ParsedResult(entities, relationships);

        // 去掉 completionDelimiter（如果模型遵守协议，会在末尾输出）
        combined = combined.replace(completionDelimiter, "");

        String[] records = combined.split(Pattern.quote(recordDelimiter));
        for (String rec : records) {
            String record = rec.trim();
            if (record.isEmpty()) continue;

            // 去掉最外层括号
            record = record.replaceAll("^\\(|\\)$", "").trim();
            if (record.isEmpty()) continue;

            String[] fields = record.split(Pattern.quote(tupleDelimiter));
            if (fields.length == 0) continue;

            String tag = cleanStr(fields[0]).toLowerCase(Locale.ROOT);
            if ("entity".equals(tag) && fields.length >= 4) {
                String name = cleanStr(fields[1]);
                String type = cleanStr(fields[2]);
                String desc = cleanStr(fields[3]);
                Entity entity = Entity.builder()
                        .id(null)
                        .humanReadableId(null)
                        .title(name)
                        .type(type)
                        .descriptionList(List.of(desc))
                        .summary(null)
                        .aliases(new ArrayList<>())
                        .textUnitIds(List.of(textUnitId))
                        .metadata(new HashMap<>())
                        .createdAt(Instant.now())
                        .build();
                entities.add(entity);
            } else if ("relationship".equals(tag) && fields.length >= 5) {
                String source = cleanStr(fields[1]);
                String target = cleanStr(fields[2]);
                String desc = cleanStr(fields[3]);
                String strengthStr = cleanStr(fields[fields.length - 1]);
                Double strength = null;
                try {
                    strength = Double.parseDouble(strengthStr);
                } catch (NumberFormatException ignored) {
                }

                Map<String, Object> metadata = new HashMap<>();
                if (strength != null) {
                    metadata.put("strength", strength);
                }

                Relationship rel = Relationship.builder()
                        .id(null)
                        .humanReadableId(null)
                        .source(source)
                        .target(target)
                        .predicate(null) // Python 默认不单独输出 predicate，这里先留空
                        .descriptionList(List.of(desc))
                        .summary(null)
                        .textUnitIds(List.of(textUnitId))
                        .metadata(metadata)
                        .createdAt(Instant.now())
                        .build();
                relationships.add(rel);
            }
        }

        return new ParsedResult(entities, relationships);
    }

    private String cleanStr(String s) {
        if (s == null) return "";
        return s.trim().replaceAll("^\"|\"$", "");
    }

    /**
     * 对实体进行归一去重：
     * - 以 title 的小写去空白形式作为 key；
     * - 合并 descriptionList、sourceTextUnitIds、aliases。
     */
    private List<Entity> mergeEntities(List<Entity> entities) {
        Map<String, Entity.EntityBuilder> map = new LinkedHashMap<>();

        for (Entity e : entities) {
            String key = normalizeKey(e.getTitle());
            Entity.EntityBuilder builder = map.get(key);
            if (builder == null) {
                builder = Entity.builder()
                        .id(null)
                        .humanReadableId(null)
                        .title(e.getTitle())
                        .type(e.getType())
                        .descriptionList(new ArrayList<>(safeList(e.getDescriptionList())))
                        .summary(null)
                        .aliases(new ArrayList<>(safeList(e.getAliases())))
                        .textUnitIds(new ArrayList<>(safeList(e.getTextUnitIds())))
                        .metadata(e.getMetadata())
                        .createdAt(e.getCreatedAt());
                map.put(key, builder);
            } else {
                List<String> desc = safeList(builder.build().getDescriptionList());
                for (String d : safeList(e.getDescriptionList())) {
                    if (!desc.contains(d)) desc.add(d);
                }
                List<String> src = safeList(builder.build().getTextUnitIds());
                for (String s : safeList(e.getTextUnitIds())) {
                    if (!src.contains(s)) src.add(s);
                }
                List<String> aliases = safeList(builder.build().getAliases());
                for (String a : safeList(e.getAliases())) {
                    if (!aliases.contains(a)) aliases.add(a);
                }
            }
        }

        return map.values().stream()
                .map(Entity.EntityBuilder::build)
                .collect(Collectors.toList());
    }

    /**
     * 对关系进行归一去重：
     * - 以 (sourceEntityId, targetEntityId, predicate) 的小写组合 key；
     * - 合并 descriptionList、sourceTextUnitIds。
     */
    private List<Relationship> mergeRelationships(List<Relationship> relationships) {
        Map<String, Relationship.RelationshipBuilder> map = new LinkedHashMap<>();

        for (Relationship r : relationships) {
            String key = normalizeKey(r.getSource())
                    + "||" + normalizeKey(r.getTarget())
                    + "||" + normalizeKey(r.getPredicate());
            Relationship.RelationshipBuilder builder = map.get(key);
            if (builder == null) {
                builder = Relationship.builder()
                        .id(null)
                        .humanReadableId(null)
                        .source(r.getSource())
                        .target(r.getTarget())
                        .predicate(r.getPredicate())
                        .descriptionList(new ArrayList<>(safeList(r.getDescriptionList())))
                        .summary(null)
                        .textUnitIds(new ArrayList<>(safeList(r.getTextUnitIds())))
                        .metadata(r.getMetadata())
                        .createdAt(r.getCreatedAt());
                map.put(key, builder);
            } else {
                List<String> desc = safeList(builder.build().getDescriptionList());
                for (String d : safeList(r.getDescriptionList())) {
                    if (!desc.contains(d)) desc.add(d);
                }
                List<String> src = safeList(builder.build().getTextUnitIds());
                for (String s : safeList(r.getTextUnitIds())) {
                    if (!src.contains(s)) src.add(s);
                }
            }
        }

        return map.values().stream()
                .map(Relationship.RelationshipBuilder::build)
                .collect(Collectors.toList());
    }

    private List<String> safeList(List<String> list) {
        if (list == null) return new ArrayList<>();
        return new ArrayList<>(list);
    }

    private String normalizeKey(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase(Locale.ROOT);
    }

    // ---- 内部封装的简单结果类型 ----

    private static class ParsedResult {
        final List<Entity> entities;
        final List<Relationship> relationships;

        ParsedResult(List<Entity> entities, List<Relationship> relationships) {
            this.entities = entities;
            this.relationships = relationships;
        }
    }

    @Getter
    public static class ExtractionResult {
        private List<Entity> entities;
        private List<Relationship> relationships;

        public ExtractionResult() {}

        public ExtractionResult(List<Entity> entities, List<Relationship> relationships) {
            this.entities = entities;
            this.relationships = relationships;
        }

    }
}
