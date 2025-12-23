package com.gdin.inspection.graphrag.v2.index.opertation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Jacksonized
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FindingModel {

    /**
     * 单条发现的简短摘要。
     */
    @JsonProperty("summary")
    String summary;

    /**
     * 对该发现的详细解释。
     */
    @JsonProperty("explanation")
    String explanation;
}
