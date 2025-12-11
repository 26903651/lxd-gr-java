package com.gdin.inspection.graphrag.v2.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.Map;

@Value
@Jacksonized
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Embedding {
    @JsonProperty("id")
    String id;

    @JsonProperty("dimension")
    Integer dimension;

    // vector 字段视情况序列化
    float[] vector;

    @JsonProperty("source_type")
    String sourceType;

    @JsonProperty("source_id")
    String sourceId;

    @JsonProperty("metadata")
    Map<String, Object> metadata;

    @JsonProperty("createdAt")
    Instant createdAt;

    @JsonProperty("human_readable_id")
    Integer humanReadableId;
}