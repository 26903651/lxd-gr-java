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
public class Covariate {
    @JsonProperty("id")
    String id;

    @JsonProperty("entity_id")
    String entityId;

    @JsonProperty("property")
    String property;

    @JsonProperty("value")
    Object value;

    @JsonProperty("start_at")
    Instant startAt;

    @JsonProperty("end_at")
    Instant endAt;

    @JsonProperty("source_text_unit_id")
    String sourceTextUnitId;

    @JsonProperty("metadata")
    Map<String, Object> metadata;

    @JsonProperty("human_readable_id")
    Integer humanReadableId;
}