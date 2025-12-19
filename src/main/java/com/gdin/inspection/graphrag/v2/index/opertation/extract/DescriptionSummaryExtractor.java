package com.gdin.inspection.graphrag.v2.index.opertation.extract;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.IdUtil;
import com.gdin.inspection.graphrag.assistant.ThinkAssistant;
import com.gdin.inspection.graphrag.service.AssistantGenerator;
import com.gdin.inspection.graphrag.util.IOUtil;
import com.gdin.inspection.graphrag.util.SseUtil;
import com.gdin.inspection.graphrag.v2.index.prompts.SummarizeDescriptionPromptsZh;
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
     * 主入口：按 Python 逻辑：
     * - 0 条描述 -> ""
     * - 1 条描述 -> 原文
     * - 多条描述 -> 调 LLM 做综合摘要
     */
    public String summarize(String entityName, List<String> descriptions, int maxWords) {
        if (CollectionUtil.isEmpty(descriptions)) return "";
        if (descriptions.size() == 1) {
            // 完全对齐 Python：单条描述不走 LLM
            return descriptions.get(0);
        }

        try {
            String summarizePrompt = SummarizeDescriptionPromptsZh.SUMMARIZE_PROMPT
                    .replace("{max_length}", String.valueOf(maxWords))
                    .replace("{entity_name}", entityName)
                    .replace("{description_list}", IOUtil.jsonSerialize(descriptions));
            String memoryId = IdUtil.getSnowflakeNextIdStr();
            ThinkAssistant assistant = assistantGenerator.createTempAssistant(ThinkAssistant.class);
            TokenStream tokenStream = assistant.streamChat(memoryId, summarizePrompt);
            return SseUtil.getResponseWithoutThink(null, tokenStream, memoryId);
        } catch (Exception e) {
            log.error("summarize descriptions failed for entity={}", entityName, e);
            // Python 里如果 LLM 出错通常会走 error handler，这里先回退到拼接版，保证不丢信息
            return joinDescriptions(descriptions);
        }
    }

    private String joinDescriptions(List<String> descriptions) {
        return descriptions.stream()
                .map(d -> d.replace("\n", " "))
                .collect(Collectors.joining(" "));
    }
}
