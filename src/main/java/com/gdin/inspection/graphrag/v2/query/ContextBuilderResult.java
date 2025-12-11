package com.gdin.inspection.graphrag.v2.query;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
@Builder
public class ContextBuilderResult {

    /**
     * 真正要喂给 LLM 的上下文字符串
     */
    String contextText;

    /**
     * 每一类上下文的结构化记录，方便调试：
     *  key：如 "communities" / "entities" / "relationships" / "text_units"
     *  value：这一类下面的若干行记录，每行用 Map 表示字段
     */
    Map<String, List<Map<String, Object>>> contextRecords;
}
