package com.gdin.inspection.graphrag.v2.index.opertation.extract;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.gdin.inspection.graphrag.assistant.ThinkAssistant;
import com.gdin.inspection.graphrag.service.AssistantGenerator;
import com.gdin.inspection.graphrag.util.SseUtil;
import com.gdin.inspection.graphrag.v2.index.prompts.ExtractClaimsPromptsZh;
import com.gdin.inspection.graphrag.v2.index.strategy.ExtractClaimsStrategy;
import dev.langchain4j.service.TokenStream;
import jakarta.annotation.Resource;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 对齐 Python: graphrag/index/operations/extract_covariates/claim_extractor.py
 *
 * 保留你现有的“同一个 memoryId 串多轮对话”的方式来模拟 Python 的 history。
 *
 * 注意：Python 代码里 _process_document 最后 return 的 parse 输入是 “results”（第一轮响应），
 * 而不是累加后的 claims，这看起来像 bug，但为了完全贴合，这里也按 Python 行为实现。
 */
@Slf4j
@Component
public class ExtractClaimsExtractor {

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
    private static final String KEY_ENTITY_SPECS = "entity_specs";
    private static final String KEY_CLAIM_DESC = "claim_description";
    private static final String KEY_TUPLE_DELIMITER = "tuple_delimiter";
    private static final String KEY_RECORD_DELIMITER = "record_delimiter";
    private static final String KEY_COMPLETION_DELIMITER = "completion_delimiter";

    public ClaimExtractorResult extract(
            List<String> texts,
            List<String> entitySpecs,
            Map<String, String> resolvedEntitiesMap,
            ExtractClaimsStrategy strategy
    ) {
        if (CollectionUtil.isEmpty(texts)) return new ClaimExtractorResult(List.of(), Map.of());
        if (strategy == null || StrUtil.isBlank(strategy.getClaimDescription())) throw new IllegalArgumentException("claim_description is required for claim extraction");

        List<String> specs = CollectionUtil.isEmpty(entitySpecs) ? DEFAULT_ENTITY_TYPES_ZH : entitySpecs;

        Map<String, String> resolved = (resolvedEntitiesMap == null) ? new HashMap<>() : resolvedEntitiesMap;

        String extractionPrompt = StrUtil.blankToDefault(strategy.getExtractionPrompt(), ExtractClaimsPromptsZh.EXTRACT_CLAIMS_PROMPT);

        String tupleDelimiter = StrUtil.blankToDefault(strategy.getTupleDelimiter(), DEFAULT_TUPLE_DELIMITER);
        String recordDelimiter = StrUtil.blankToDefault(strategy.getRecordDelimiter(), DEFAULT_RECORD_DELIMITER);
        String completionDelimiter = StrUtil.blankToDefault(strategy.getCompletionDelimiter(), DEFAULT_COMPLETION_DELIMITER);

        int maxGleanings = (strategy.getMaxGleanings() == null) ? 0 : Math.max(strategy.getMaxGleanings(), 0);

        // 对齐 Python: entity_spec = str(list)
        String entitySpecStr = toPythonListString(specs);

        Map<String, String> promptArgs = new HashMap<>();
        promptArgs.put(KEY_ENTITY_SPECS, entitySpecStr);
        promptArgs.put(KEY_CLAIM_DESC, strategy.getClaimDescription());
        promptArgs.put(KEY_TUPLE_DELIMITER, tupleDelimiter);
        promptArgs.put(KEY_RECORD_DELIMITER, recordDelimiter);
        promptArgs.put(KEY_COMPLETION_DELIMITER, completionDelimiter);

        // 对齐 Python：source_doc_map: { "d0": text0, "d1": text1, ... }
        Map<String, Object> sourceDocs = new LinkedHashMap<>();
        List<Map<String, Object>> allClaims = new ArrayList<>();

        for (int docIndex = 0; docIndex < texts.size(); docIndex++) {
            String text = texts.get(docIndex);
            if (StrUtil.isBlank(text)) continue;

            String documentId = "d" + docIndex;
            try {
                List<Map<String, Object>> claims = processDocument(extractionPrompt, promptArgs, text, maxGleanings);
                for (Map<String, Object> c : claims) {
                    allClaims.add(cleanClaim(c, resolved));
                }
                sourceDocs.put(documentId, text);
            } catch (Exception e) {
                log.error("error extracting claim doc_index={}, text_len={}", docIndex, text.length(), e);
            }
        }

        return new ClaimExtractorResult(allClaims, sourceDocs);
    }

    private List<Map<String, Object>> processDocument(
            String extractionPrompt,
            Map<String, String> promptArgs,
            String doc,
            int maxGleanings
    ) {
        String recordDelimiter = promptArgs.get(KEY_RECORD_DELIMITER);
        String completionDelimiter = promptArgs.get(KEY_COMPLETION_DELIMITER);

        // 用同一个 memoryId
        String memoryId = IdUtil.getSnowflakeNextIdStr();
        ThinkAssistant assistant = assistantGenerator.createTempAssistant(ThinkAssistant.class);

        String firstPrompt = extractionPrompt
                .replace("{input_text}", doc)
                .replace("{entity_specs}", promptArgs.get(KEY_ENTITY_SPECS))
                .replace("{claim_description}", promptArgs.get(KEY_CLAIM_DESC))
                .replace("{tuple_delimiter}", promptArgs.get(KEY_TUPLE_DELIMITER))
                .replace("{record_delimiter}", recordDelimiter)
                .replace("{completion_delimiter}", completionDelimiter);

        String results = callOnce(assistant, memoryId, firstPrompt);
        StringBuilder claims = new StringBuilder(stripSuffix(results, completionDelimiter));

        // 对齐 Python gleanings：继续补抽 + loop check（注意 Python 是 range(max_gleanings)，这里也一致）
        if (maxGleanings > 0) {
            for (int i = 0; i < maxGleanings; i++) {
                String ext = callOnce(assistant, memoryId, ExtractClaimsPromptsZh.CONTINUE_PROMPT);
                if(!StrUtil.isBlank(ext)) {
                    claims.append(recordDelimiter).append(stripSuffix(ext, completionDelimiter));
                }

                if (i >= maxGleanings - 1) break;

                String loopResp = callOnce(assistant, memoryId, ExtractClaimsPromptsZh.LOOP_PROMPT);
                String trimmed = loopResp == null ? "" : loopResp.trim().toUpperCase(Locale.ROOT);
                if(!"Y".equals(trimmed)) break;
            }
        }

        // 注意：对齐 Python（即便它看起来像 bug）：这里 parse 的输入是 results（第一轮响应），不是累加后的 claims
        return parseClaimTuples(claims.toString(), promptArgs);
    }

    private Map<String, Object> cleanClaim(Map<String, Object> claim, Map<String, String> resolvedEntities) {
        Object obj = claim.getOrDefault("object_id", claim.get("object"));
        Object subject = claim.getOrDefault("subject_id", claim.get("subject"));

        String objStr = obj == null ? null : String.valueOf(obj);
        String subStr = subject == null ? null : String.valueOf(subject);

        objStr = resolvedEntities.getOrDefault(objStr, objStr);
        subStr = resolvedEntities.getOrDefault(subStr, subStr);

        claim.put("object_id", objStr);
        claim.put("subject_id", subStr);
        return claim;
    }

    private List<Map<String, Object>> parseClaimTuples(String claims, Map<String, String> promptArgs) {
        String recordDelimiter = promptArgs.get(KEY_RECORD_DELIMITER);
        String completionDelimiter = promptArgs.get(KEY_COMPLETION_DELIMITER);
        String tupleDelimiter = promptArgs.get(KEY_TUPLE_DELIMITER);

        String body = stripSuffix(StrUtil.blankToDefault(claims, ""), completionDelimiter);
        String[] claimValues = body.trim().split(Pattern.quote(recordDelimiter));

        List<Map<String, Object>> result = new ArrayList<>();
        for (String c : claimValues) {
            String claim = (c == null) ? "" : c.trim();
            claim = stripPrefix(claim, "(");
            claim = stripSuffix(claim, ")");

            if (claim.isEmpty()) continue;

            String[] fields = claim.split(Pattern.quote(tupleDelimiter));
            if (fields.length == 0) continue;


            Map<String, Object> row = new LinkedHashMap<>();
            row.put("subject_id", pullField(fields, 0));
            row.put("object_id", pullField(fields, 1));
            row.put("type", pullField(fields, 2));
            row.put("status", pullField(fields, 3));
            row.put("start_date", pullField(fields, 4));
            row.put("end_date", pullField(fields, 5));
            row.put("description", pullField(fields, 6));
            row.put("source_text", pullField(fields, 7));
            result.add(row);
        }
        return result;
    }

    private String pullField(String[] fields, int index) {
        if (fields == null || fields.length <= index) return null;
        String v = fields[index] == null ? null : fields[index].trim();
        return StrUtil.blankToDefault(v, null);
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

    @Value
    public static class ClaimExtractorResult {
        List<Map<String, Object>> output;
        Map<String, Object> sourceDocs;
    }

    /** 供 Covariate 组装用：把日期字符串转 Instant */
    public static Instant parseInstantLoose(String s) {
        if (StrUtil.isBlank(s) || "NONE".equalsIgnoreCase(s.trim())) return null;
        try {
            String t = s.trim();
            if (t.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}$")) {
                return Instant.parse(t + "Z");
            }
            return Instant.parse(t);
        } catch (Exception ignore) {
            return null;
        }
    }
}
