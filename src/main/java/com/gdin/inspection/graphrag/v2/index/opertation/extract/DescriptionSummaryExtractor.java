package com.gdin.inspection.graphrag.v2.index.opertation.extract;

import cn.hutool.core.util.IdUtil;
import com.gdin.inspection.graphrag.assistant.ThinkAssistant;
import com.gdin.inspection.graphrag.service.AssistantGenerator;
import com.gdin.inspection.graphrag.util.SseUtil;
import dev.langchain4j.service.TokenStream;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class DescriptionSummaryExtractor {

    @Resource
    private AssistantGenerator assistantGenerator;

    /**
     * 对齐 Python 的 max_length（字数限制），这里用“字数”粗略代替。
     */
    private static final int DEFAULT_MAX_WORDS = 150;

    /**
     * 主入口：按 Python 逻辑：
     * - 0 条描述 -> ""
     * - 1 条描述 -> 原文
     * - 多条描述 -> 调 LLM 做综合摘要
     */
    public String summarize(String id, List<String> descriptions) {
        return summarize(id, descriptions, DEFAULT_MAX_WORDS);
    }

    public String summarize(String id, List<String> descriptions, int maxWords) {
        if (descriptions == null || descriptions.isEmpty()) {
            return "";
        }
        if (descriptions.size() == 1) {
            // 完全对齐 Python：单条描述不走 LLM
            return descriptions.get(0);
        }

        try {
            String systemPrompt = buildSystemPrompt();
            String userPrompt = buildUserPromptForDescriptions(id, descriptions, maxWords);
            return callLlm(systemPrompt, userPrompt);
        } catch (Exception e) {
            log.error("summarize descriptions failed for id={}", id, e);
            // Python 里如果 LLM 出错通常会走 error handler，这里先回退到拼接版，保证不丢信息
            return joinDescriptions(descriptions);
        }
    }

    private String buildSystemPrompt() {
        // 中文版 SUMMARIZE_PROMPT，语义对齐。
        return """
                你是一名负责为实体生成综合描述的智能助手。
                现在会给你一个或多个实体的名称，以及与这些实体相关的多条描述。
                你的任务是将这些描述整合成一段完整、连贯的第三人称简介。
                需要尽量综合所有描述中的信息，不遗漏重要细节。
                如果不同描述之间存在矛盾，请进行合理归纳，输出一个自洽的总结。
                输出时请保留实体名称，方便在脱离上下文时仍能看懂。
                """;
    }

    // 这里实现的是 单轮 summarization。Python的实现是多轮“分块 + reduce”, 有空再对齐 TODO
    private String buildUserPromptForDescriptions(String id, List<String> descriptions, int maxWords) {
        String descBlock = descriptions.stream()
                .map(d -> "- " + d.replace("\n", " "))
                .collect(Collectors.joining("\n"));

        // 这里对齐 Python 模板的结构，但内容换成中文
        return """
                请根据下面给出的实体名称和描述列表，生成一段综合性的中文简介。

                要求：
                1. 用第三人称描述。
                2. 在可能的情况下尽量覆盖所有描述中的关键信息。
                3. 如有矛盾，请自行判断并给出一个自洽的综合表述。
                4. 在总结中明确提到实体名称，以便单独使用。
                5. 控制整体长度，大约不超过 %d 个汉字（可略有浮动，不需要严格计数）。

                实体（或实体组合）标识：
                %s

                描述列表：
                %s

                请直接给出最终的综合简介，不要解释推理过程。
                """.formatted(maxWords, id, descBlock);
    }

    private String callLlm(String systemPrompt, String userPrompt) throws InterruptedException {
        String memoryId = IdUtil.getSnowflakeNextIdStr();
        ThinkAssistant assistant = assistantGenerator.createTempAssistant(ThinkAssistant.class, systemPrompt);
        TokenStream tokenStream = assistant.streamChat(memoryId, userPrompt);
        return SseUtil.getResponseWithoutThink(null, tokenStream, memoryId);
    }

    private String joinDescriptions(List<String> descriptions) {
        return descriptions.stream()
                .map(d -> d.replace("\n", " "))
                .collect(Collectors.joining(" "));
    }
}
