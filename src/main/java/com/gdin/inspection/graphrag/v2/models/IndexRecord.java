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
public class IndexRecord {
    @JsonProperty("id")
    String id;

    // vector 同样按需序列化
    float[] vector;

    @JsonProperty("type")
    String type;

    @JsonProperty("source_id")
    String sourceId;

    @JsonProperty("payload")
    Map<String, Object> payload;

    @JsonProperty("createdAt")
    Instant createdAt;

    @JsonProperty("human_readable_id")
    Integer humanReadableId;
}
