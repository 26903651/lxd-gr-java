package com.gdin.inspection.graphrag.v2.index.opertation.extract;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.gdin.inspection.graphrag.assistant.ThinkAssistant;
import com.gdin.inspection.graphrag.service.AssistantGenerator;
import com.gdin.inspection.graphrag.util.SseUtil;
import com.gdin.inspection.graphrag.v2.index.prompts.ExtractGraphPromptsZh;
import com.gdin.inspection.graphrag.v2.index.strategy.ExtractGraphStrategy;
import com.gdin.inspection.graphrag.v2.models.TextUnit;
import com.gdin.inspection.graphrag.v2.util.PyStrUtil;
import dev.langchain4j.service.TokenStream;
import jakarta.annotation.Resource;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * 对齐 Python 版的图抽取逻辑：
 * - 对每个 TextUnit 调用 LLM 抽取 ("entity", ...) 和 ("relationship", ...) 记录
 * - 支持 CONTINUE / LOOP 多轮 gleaning
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
    public Result extract(
            List<TextUnit> textUnits,
            List<String> entitySpecs,
            ExtractGraphStrategy strategy
    ) {
        if (CollectionUtil.isEmpty(textUnits)) return new Result(List.of(), List.of());
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

        int threads = Math.max(1, strategy.getConcurrentRequests());
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        try {
            List<CompletableFuture<Result>> futures = new ArrayList<>();
            for (TextUnit tu : textUnits) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    String rawOutput = extractForSingleTextUnit(extractionPrompt, promptArgs, tu.getText(), maxGleanings);
                    return parseRecords(rawOutput, tu.getId(), promptArgs);
                }, pool));
            }

            for (CompletableFuture<Result> future : futures) {
                Result parsed = future.join();
                rawEntities.addAll(parsed.entities);
                rawRelationships.addAll(parsed.relationships);
            }
        } finally {
            pool.shutdown();
        }
        return new Result(rawEntities, rawRelationships);
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

    private Result parseRecords(String combined, String textUnitId, Map<String, String> promptArgs) {
        String recordDelimiter = promptArgs.get(KEY_RECORD_DELIMITER);
        String completionDelimiter = promptArgs.get(KEY_COMPLETION_DELIMITER);
        String tupleDelimiter = promptArgs.get(KEY_TUPLE_DELIMITER);

        List<RawEntity> entities = new ArrayList<>();
        List<RawRelationship> relationships = new ArrayList<>();

        if (StrUtil.isBlank(combined)) return new Result(entities, relationships);

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

            if ("\"entity\"".equals(tag) && fields.length >= 4) {
                String name = PyStrUtil.cleanStr(fields[1]);
                String type = PyStrUtil.cleanStr(fields[2]);
                String desc = PyStrUtil.cleanStr(fields[3]);
                if (StrUtil.isBlank(name)) continue;
                entities.add(new RawEntity(name, type, desc, textUnitId));
            } else if ("\"relationship\"".equals(tag) && fields.length >= 4) {
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
        return new Result(entities, relationships);
    }

    @Value
    public static class RawEntity {
        String title;
        String type;
        String description;
        String textUnitId;
    }

    @Value
    public static class RawRelationship {
        String source;
        String target;
        String description;
        String textUnitId;
        double weight;
    }

    @Value
    public static class Result {
        List<RawEntity> entities;
        List<RawRelationship> relationships;
    }
}
