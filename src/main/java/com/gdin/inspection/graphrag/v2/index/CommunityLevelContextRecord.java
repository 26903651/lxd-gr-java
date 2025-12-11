package com.gdin.inspection.graphrag.v2.index;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * 对齐 Python summarize_communities 里每一行 level_context 的含义：
 * - communityId: 社区 ID
 * - level: 社区层级
 * - contextString: 该社区最终用于生成报告的上下文字符串
 */
@Value
@Jacksonized
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommunityLevelContextRecord {
    String communityId;
    Integer level;
    String contextString;
}
