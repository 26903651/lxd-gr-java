package com.gdin.inspection.graphrag.v2.index;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdin.inspection.graphrag.assistant.ThinkAssistant;
import com.gdin.inspection.graphrag.service.AssistantGenerator;
import com.gdin.inspection.graphrag.util.SseUtil;
import com.gdin.inspection.graphrag.v2.index.prompts.CommunityReportPromptsZh;
import com.gdin.inspection.graphrag.v2.util.JsonUtils;
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

    private final ObjectMapper objectMapper = JsonUtils.mapper();

    /**
     * 为单个社区生成报告。
     *
     * @param communityContext 该社区的上下文字符串（后续 SummarizeCommunities 步骤会构造）
     * @param maxReportLength  报告最大长度（和 Python 的 max_report_length 一致）
     */
    public CommunityReportsResult generateReport(String communityContext, int maxReportLength) {
        String systemPrompt = CommunityReportPromptsZh.buildSystemPrompt(maxReportLength);
        String userPrompt = CommunityReportPromptsZh.buildUserPrompt(communityContext);

        String rawOutput;
        try {
            rawOutput = callLlm(systemPrompt, userPrompt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("生成社区报告时线程被中断", e);
            return CommunityReportsResult.builder()
                    .structuredOutput(null)
                    .output("")
                    .build();
        } catch (Exception e) {
            log.error("调用大模型生成社区报告失败", e);
            return CommunityReportsResult.builder()
                    .structuredOutput(null)
                    .output("")
                    .build();
        }

        // 尝试从输出中提取 JSON，并解析为 CommunityReportResponse
        CommunityReportResponse structured = parseStructuredOutput(rawOutput);

        if (structured == null) {
            // 解析失败：直接把模型原始输出作为文本返回
            return CommunityReportsResult.builder()
                    .structuredOutput(null)
                    .output(rawOutput == null ? "" : rawOutput.trim())
                    .build();
        }

        // 和 Python 的 _get_text_output 一致：把结构化结果转成 Markdown 文本
        String textOutput = buildTextOutput(structured);

        return CommunityReportsResult.builder()
                .structuredOutput(structured)
                .output(textOutput)
                .build();
    }

    /**
     * 调用大模型
     */
    private String callLlm(String systemPrompt, String userPrompt) throws InterruptedException {
        String memoryId = IdUtil.getSnowflakeNextIdStr();
        ThinkAssistant assistant = assistantGenerator.createTempAssistant(ThinkAssistant.class, systemPrompt);
        TokenStream tokenStream = assistant.streamChat(memoryId, userPrompt);
        return SseUtil.getResponseWithoutThink(null, tokenStream, memoryId);
    }

    /**
     * 从模型输出中提取 JSON，并解析为 CommunityReportResponse。
     * 这里考虑模型可能在 JSON 前后加了多余文字，因此做一次“截取第一个 { 到最后一个 }”的容错处理。
     */
    private CommunityReportResponse parseStructuredOutput(String rawOutput) {
        if (StrUtil.isBlank(rawOutput)) return null;

        String trimmed = rawOutput.trim();

        // 简单容错：截取第一个 { 到最后一个 } 之间的内容
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            trimmed = trimmed.substring(firstBrace, lastBrace + 1);
        }

        try {
            return objectMapper.readValue(trimmed, CommunityReportResponse.class);
        } catch (JsonProcessingException e) {
            log.warn("解析社区报告 JSON 失败，输出内容为：{}", trimmed, e);
            return null;
        }
    }

    /**
     * 对齐 Python CommunityReportsExtractor._get_text_output 的行为：
     *
     * # {title}
     *
     * {summary}
     *
     * ## {finding.summary}
     *
     * {finding.explanation}
     * ...
     */
    private String buildTextOutput(CommunityReportResponse report) {
        String title = Optional.ofNullable(report.getTitle()).orElse("").trim();
        String summary = Optional.ofNullable(report.getSummary()).orElse("").trim();

        List<FindingModel> findings = Optional.ofNullable(report.getFindings())
                .orElse(List.of())
                .stream()
                .filter(Objects::nonNull)
                .toList();

        String findingsSection = findings.stream()
                .map(f -> {
                    String fs = Optional.ofNullable(f.getSummary()).orElse("").trim();
                    String fe = Optional.ofNullable(f.getExplanation()).orElse("").trim();
                    return "## " + fs + "\n\n" + fe;
                })
                .collect(Collectors.joining("\n\n"));

        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(title).append("\n\n");
        sb.append(summary);
        if (!findingsSection.isEmpty()) {
            sb.append("\n\n").append(findingsSection);
        }

        return sb.toString().trim();
    }
}
