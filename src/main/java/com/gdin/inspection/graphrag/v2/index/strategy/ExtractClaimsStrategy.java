package com.gdin.inspection.graphrag.v2.index.strategy;

import lombok.Builder;
import lombok.Value;

/**
 * 对齐 Python strategy_config（extract_covariates.run_extract_claims 中读取的键）
 * - extraction_prompt
 * - claim_description（required）
 * - max_gleanings
 * - tuple_delimiter / record_delimiter / completion_delimiter
 *
 * 你保留第4点（并发/回调/缓存）不做，这里只覆盖本次需要对齐的字段。
 */
@Value
@Builder
public class ExtractClaimsStrategy {

    /** 对齐 Python: strategy_config.get("extraction_prompt") */
    String extractionPrompt;

    /** 对齐 Python: claim_description is required */
    String claimDescription;

    /** 对齐 Python: max_gleanings */
    Integer maxGleanings;

    /** 对齐 Python defaults: "<|>" */
    String tupleDelimiter;

    /** 对齐 Python defaults: "##" */
    String recordDelimiter;

    /** 对齐 Python defaults: "<|COMPLETE|>" */
    String completionDelimiter;

    /** 对齐 Python: config.extract_claims.enabled */
    @Builder.Default
    boolean enabled = true;
}
