package com.gdin.inspection.graphrag.v2.index.strategy;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ExtractGraphStrategy {

    /** 对齐 Python: strategy_config.get("extraction_prompt") */
    String extractionPrompt;

    /** 对齐 Python: max_gleanings */
    Integer maxGleanings;

    /** 对齐 Python defaults: "<|>" */
    String tupleDelimiter;

    /** 对齐 Python defaults: "##" */
    String recordDelimiter;

    /** 对齐 Python defaults: "<|COMPLETE|>" */
    String completionDelimiter;

    Integer concurrentRequests;
}
