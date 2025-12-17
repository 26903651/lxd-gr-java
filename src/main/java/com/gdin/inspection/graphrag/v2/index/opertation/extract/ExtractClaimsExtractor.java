package com.gdin.inspection.graphrag.v2.index.opertation.extract;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.gdin.inspection.graphrag.assistant.ThinkAssistant;
import com.gdin.inspection.graphrag.service.AssistantGenerator;
import com.gdin.inspection.graphrag.util.SseUtil;
import com.gdin.inspection.graphrag.v2.index.prompts.ExtractClaimsPromptsZh;
import dev.langchain4j.service.TokenStream;
import jakarta.annotation.Resource;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 对齐 Python: graphrag.index.operations.extract_covariates.claim_extractor + prompts/index/extract_claims.py
 *
 * 输出解析严格依赖：
 * - tuple_delimiter
 * - record_delimiter
 * - completion_delimiter
 *
 * 并实现 max_gleanings 的 CONTINUE_PROMPT + LOOP_PROMPT 迭代逻辑。
 */
@Slf4j
@Component
public class ExtractClaimsExtractor {

    @Resource
    private AssistantGenerator assistantGenerator;

    /**
     * 对齐 Python DEFAULT_ENTITY_TYPES 的中文环境版本（你要求中文）
     * 但仍作为 entity_specs 注入 prompt（Python 允许 entity_specs 是类型列表或名称列表）。
     */
    public static final List<String> DEFAULT_ENTITY_TYPES_ZH =
            List.of("组织", "人员", "地理位置", "事件");

    public List<ClaimRecord> extract(
            String inputText,
            List<String> entitySpecs,
            String claimDescription,
            String tupleDelimiter,
            String recordDelimiter,
            String completionDelimiter,
            Integer maxGleanings
    ) {
        if (StrUtil.isBlank(inputText)) return Collections.emptyList();
        if (StrUtil.isBlank(claimDescription)) {
            throw new IllegalArgumentException("claimDescription 不能为空（对齐 Python claim_description required）");
        }

        List<String> specs = (entitySpecs == null || entitySpecs.isEmpty())
                ? DEFAULT_ENTITY_TYPES_ZH
                : entitySpecs;

        String td = StrUtil.blankToDefault(tupleDelimiter, "|");
        String rd = StrUtil.blankToDefault(recordDelimiter, "\n");
        String cd = StrUtil.blankToDefault(completionDelimiter, "<DONE>");
        int mg = (maxGleanings == null || maxGleanings <= 0) ? 1 : maxGleanings;

        // 注意：为了对齐 Python“链式多轮对话”，这里用同一个 memoryId 贯穿 base + continue + loop
        String memoryId = IdUtil.getSnowflakeNextIdStr();
        ThinkAssistant assistant = assistantGenerator.createTempAssistant(ThinkAssistant.class, ""); // system 置空，prompt 在 user 里

        String basePrompt = renderBasePrompt(specs, claimDescription, inputText, td, rd, cd);
        String first = chat(assistant, memoryId, basePrompt);
        List<ClaimRecord> all = new ArrayList<>(parse(first, td, rd, cd));

        // 对齐 max_gleanings：每轮补抽取 + loop 判断是否继续
        for (int i = 1; i < mg; i++) {
            String cont = chat(assistant, memoryId, ExtractClaimsPromptsZh.CONTINUE_PROMPT);
            all.addAll(parse(cont, td, rd, cd));

            String loop = chat(assistant, memoryId, ExtractClaimsPromptsZh.LOOP_PROMPT);
            if (startsWithN(loop)) {
                break;
            }
        }

        return all;
    }

    private String renderBasePrompt(
            List<String> entitySpecs,
            String claimDescription,
            String inputText,
            String tupleDelimiter,
            String recordDelimiter,
            String completionDelimiter
    ) {
        // Python 中 entity_specs 可能是 list[str]，这里用 JSON 数组字符串更贴近“列表”语义
        String specs = JSON.toJSONString(entitySpecs);

        return ExtractClaimsPromptsZh.EXTRACT_CLAIMS_PROMPT
                .replace("{entity_specs}", specs)
                .replace("{claim_description}", claimDescription)
                .replace("{input_text}", inputText)
                .replace("{tuple_delimiter}", tupleDelimiter)
                .replace("{record_delimiter}", recordDelimiter)
                .replace("{completion_delimiter}", completionDelimiter);
    }

    private String chat(ThinkAssistant assistant, String memoryId, String prompt) {
        try {
            TokenStream tokenStream = assistant.streamChat(memoryId, prompt);
            return SseUtil.getResponseWithoutThink(null, tokenStream, memoryId);
        } catch (Exception e) {
            log.error("extract claims llm call failed", e);
            return "";
        }
    }

    private boolean startsWithN(String loopAnswer) {
        if (loopAnswer == null) return false;
        String t = loopAnswer.trim().toUpperCase();
        return t.startsWith("N");
    }

    /**
     * 将模型输出解析为 ClaimRecord 列表：
     * - 按 completion_delimiter 截断
     * - 按 record_delimiter 分割记录
     * - 每条记录必须是 (...) 格式，内部字段用 tuple_delimiter 分隔，共 8 段
     */
    private List<ClaimRecord> parse(String raw, String td, String rd, String cd) {
        if (StrUtil.isBlank(raw)) return Collections.emptyList();

        String text = raw;

        int end = text.indexOf(cd);
        if (end >= 0) {
            text = text.substring(0, end);
        }

        // 有些模型会把 record_delimiter 单独输出在行首行尾，这里容忍多余空白
        String[] parts = text.split(java.util.regex.Pattern.quote(rd));

        List<ClaimRecord> out = new ArrayList<>();
        for (String p : parts) {
            String s = (p == null) ? "" : p.trim();
            if (s.isEmpty()) continue;

            // 允许记录跨行：如果 rd 是 "\n"，那 split 会碎；因此再尝试按 rd “整体”拆不是很稳。
            // 这里的做法：只有包含 '(' ')' 才当作一条记录；否则跳过。
            int l = s.indexOf('(');
            int r = s.lastIndexOf(')');
            if (l < 0 || r <= l) continue;

            String body = s.substring(l + 1, r);

            // 8 段：subject, object, type, status, start, end, description, source
            String[] f = body.split(java.util.regex.Pattern.quote(td), 8);
            if (f.length < 8) continue;

            String subject = normalize(f[0]);
            String object = normalize(f[1]);
            if ("NONE".equalsIgnoreCase(object)) object = null;

            String type = normalize(f[2]);
            String status = normalize(f[3]);

            String startRaw = normalize(f[4]);
            String endRaw = normalize(f[5]);
            if ("NONE".equalsIgnoreCase(startRaw)) startRaw = null;
            if ("NONE".equalsIgnoreCase(endRaw)) endRaw = null;

            String desc = normalize(f[6]);
            String source = normalize(f[7]);

            Instant start = parseInstantLoose(startRaw);
            Instant endI = parseInstantLoose(endRaw);

            out.add(new ClaimRecord(
                    subject, object, type, status, start, endI, desc, source
            ));
        }

        return out;
    }

    private String normalize(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private Instant parseInstantLoose(String s) {
        if (StrUtil.isBlank(s)) return null;
        try {
            // Python 要求 ISO-8601，示例是 2022-01-10T00:00:00
            // Instant.parse 需要 Z 或 offset；这里做一个宽松兼容：无时区就当 UTC
            String t = s.trim();
            if (t.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}$")) {
                return Instant.parse(t + "Z");
            }
            return Instant.parse(t);
        } catch (Exception ignore) {
            return null;
        }
    }

    @Value
    public static class ClaimRecord {
        String subjectId;
        String objectId;
        String type;
        String status;
        Instant startDate;
        Instant endDate;
        String description;
        String sourceText;
    }
}
