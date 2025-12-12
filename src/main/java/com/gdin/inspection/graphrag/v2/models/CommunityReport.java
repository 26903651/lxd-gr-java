package com.gdin.inspection.graphrag.v2.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Value
@Jacksonized
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommunityReport {

    @JsonProperty("id")
    String id;

    /** Python 的 short_id / human_readable_id */
    @JsonProperty("human_readable_id")
    Integer humanReadableId;

    /** 对齐 Python 的 community 列：Leiden 社区编号 */
    @JsonProperty("community")
    Integer community;

    @JsonProperty("level")
    Integer level;

    @JsonProperty("parent")
    Integer parent;

    @JsonProperty("children")
    List<Integer> children;

    @JsonProperty("title")
    String title;

    @JsonProperty("summary")
    String summary;

    @JsonProperty("full_content")
    String fullContent;

    @JsonProperty("rank")
    Double rank;

    @JsonProperty("rating_explanation")
    String ratingExplanation;

    /** 底层 findings 的 JSON 串，方便以后解析 */
    @JsonProperty("findings")
    String findings;

    /** 完整结构化输出 JSON（CommunityReportResponse） */
    @JsonProperty("full_content_json")
    String fullContentJson;

    @JsonProperty("period")
    String period;

    @JsonProperty("size")
    Integer size;
}
