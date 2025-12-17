package com.gdin.inspection.graphrag.v2.index.opertation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gdin.inspection.graphrag.v2.index.FindingModel;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

@Value
@Jacksonized
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommunityReportResponse {

    /**
     * 报告标题。
     */
    @JsonProperty("title")
    String title;

    /**
     * 报告总的执行摘要。
     */
    @JsonProperty("summary")
    String summary;

    /**
     * 报告中的各条发现。
     */
    @JsonProperty("findings")
    List<FindingModel> findings;

    /**
     * 对社区重要性 / 风险等的评分，0~1 的浮点数。
     */
    @JsonProperty("rating")
    Double rating;

    /**
     * 对评分的解释说明。
     */
    @JsonProperty("rating_explanation")
    String ratingExplanation;
}