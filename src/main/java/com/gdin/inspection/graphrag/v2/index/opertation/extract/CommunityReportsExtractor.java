package com.gdin.inspection.graphrag.v2.index.opertation.extract;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdin.inspection.graphrag.assistant.ThinkAssistant;
import com.gdin.inspection.graphrag.service.AssistantGenerator;
import com.gdin.inspection.graphrag.util.IOUtil;
import com.gdin.inspection.graphrag.util.SseUtil;
import com.gdin.inspection.graphrag.v2.index.FindingModel;
import com.gdin.inspection.graphrag.v2.index.opertation.CommunityReportResponse;
import com.gdin.inspection.graphrag.v2.index.opertation.CommunityReportsResult;
import com.gdin.inspection.graphrag.v2.index.prompts.CommunityReportPromptsZh;
import dev.langchain4j.service.TokenStream;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CommunityReportsExtractor {

    @Resource
    private AssistantGenerator assistantGenerator;

    /**
     * 对齐 Python：__call__(input_text)
     */
    public CommunityReportsResult generate(String inputText, int maxReportLength) {
        String prompt = CommunityReportPromptsZh.COMMUNITY_REPORT_PROMPT
                .replace("{input_text}", inputText == null ? "" : inputText)
                .replace("{max_report_length}", String.valueOf(maxReportLength));

        String raw;
        try {
            String memoryId = IdUtil.getSnowflakeNextIdStr();
            ThinkAssistant assistant = assistantGenerator.createTempAssistant(ThinkAssistant.class);
            TokenStream tokenStream = assistant.streamChat(memoryId, prompt);
            raw = SseUtil.getResponseWithoutThink(null, tokenStream, memoryId);
        } catch (Exception e) {
            log.error("error generating community report", e);
            // Python：异常 => output=""
            return CommunityReportsResult.builder()
                    .structuredOutput(null)
                    .output("")
                    .build();
        }

        CommunityReportResponse structured = parseAndValidate(raw);
        if (structured == null) {
            // Python：解析失败 => output=""
            return CommunityReportsResult.builder()
                    .structuredOutput(null)
                    .output("")
                    .build();
        }

        String textOutput = toPythonTextOutput(structured);
        return CommunityReportsResult.builder()
                .structuredOutput(structured)
                .output(textOutput)
                .build();
    }

    private CommunityReportResponse parseAndValidate(String raw) {
        if (StrUtil.isBlank(raw)) return null;

        String json = JsonExtractors.extractFirstJsonObject(raw);
        if (StrUtil.isBlank(json)) return null;

        try {
            CommunityReportResponse r = IOUtil.jsonDeserializeWithNoType(json, CommunityReportResponse.class);
            if (r == null) return null;

            // 对齐 Python(pydantic) 必填语义：缺字段 => 视为失败
            if (StrUtil.isBlank(r.getTitle())) return null;
            if (StrUtil.isBlank(r.getSummary())) return null;
            if (r.getFindings() == null) return null; // pydantic 里 findings 是必填 list
            if (r.getRating() == null) return null;
            if (StrUtil.isBlank(r.getRatingExplanation())) return null;

            // findings 内字段也尽量贴近 pydantic：summary/explanation 缺就保留空串（pydantic 会报错，但这里保持兼容）
            return r;
        } catch (Exception e) {
            log.warn("parse community report json failed", e);
            return null;
        }
    }

    /**
     * 对齐 Python _get_text_output：
     * report_sections = "\n\n".join(f"## {f.summary}\n\n{f.explanation}" for f in report.findings)
     * return f"# {report.title}\n\n{report.summary}\n\n{report_sections}"
     */
    private String toPythonTextOutput(CommunityReportResponse report) {
        String title = Optional.ofNullable(report.getTitle()).orElse("");
        String summary = Optional.ofNullable(report.getSummary()).orElse("");

        List<FindingModel> findings = Optional.ofNullable(report.getFindings())
                .orElse(List.of())
                .stream()
                .filter(Objects::nonNull)
                .toList();

        String sections = findings.stream()
                .map(f -> "## " + Optional.ofNullable(f.getSummary()).orElse("")
                        + "\n\n" + Optional.ofNullable(f.getExplanation()).orElse(""))
                .collect(Collectors.joining("\n\n"));

        // 不 trim：更贴近 Python 的原始拼接结果
        return "# " + title + "\n\n" + summary + "\n\n" + sections;
    }

    static final class JsonExtractors {
        private JsonExtractors() {}

        static String extractFirstJsonObject(String text) {
            if (text == null) return null;
            String t = text.trim();

            // ```json ... ```
            String fenced = extractJsonFromCodeFence(t);
            if (StrUtil.isNotBlank(fenced)) {
                String obj = extractByBraceCounting(fenced);
                if (StrUtil.isNotBlank(obj)) return obj.trim();
            }

            String obj = extractByBraceCounting(t);
            if (StrUtil.isNotBlank(obj)) return obj.trim();

            int first = t.indexOf('{');
            int last = t.lastIndexOf('}');
            if (first >= 0 && last > first) return t.substring(first, last + 1).trim();

            return null;
        }

        private static String extractJsonFromCodeFence(String t) {
            int start = t.indexOf("```json");
            if (start < 0) start = t.indexOf("```JSON");
            if (start < 0) return null;
            int bodyStart = t.indexOf('\n', start);
            if (bodyStart < 0) return null;
            int end = t.indexOf("```", bodyStart + 1);
            if (end < 0) return null;
            return t.substring(bodyStart + 1, end).trim();
        }

        private static String extractByBraceCounting(String t) {
            int first = t.indexOf('{');
            if (first < 0) return null;

            int depth = 0;
            boolean inString = false;
            char prev = 0;

            for (int i = first; i < t.length(); i++) {
                char c = t.charAt(i);

                if (c == '"' && prev != '\\') inString = !inString;

                if (!inString) {
                    if (c == '{') depth++;
                    else if (c == '}') depth--;
                    if (depth == 0) return t.substring(first, i + 1);
                }
                prev = c;
            }
            return null;
        }
    }
}
