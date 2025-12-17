package com.gdin.inspection.graphrag.v2.index.opertation;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Jacksonized
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommunityReportsResult {

    /**
     * 结构化输出，直接对应 Python 的 CommunityReportResponse。
     * 如果解析失败，可能为 null。
     */
    CommunityReportResponse structuredOutput;

    /**
     * 人类可读的 Markdown 文本输出。
     * 如果 structuredOutput 不为 null，则由其拼装而来；
     * 否则直接是模型原始输出。
     */
    String output;
}
