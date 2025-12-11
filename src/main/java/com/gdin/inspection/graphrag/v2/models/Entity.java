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
public class Entity {
    @JsonProperty("id")
    String id;

    @JsonProperty("human_readable_id")
    Integer humanReadableId;

    @JsonProperty("title")
    String title;

    @JsonProperty("type")
    String type;

    @JsonProperty("description_list")
    List<String> descriptionList;

    @JsonProperty("summary")
    String summary;

    @JsonProperty("aliases")
    List<String> aliases;

    @JsonProperty("source_text_unit_ids")
    List<String> sourceTextUnitIds;

    @JsonProperty("metadata")
    Map<String, Object> metadata;

    @JsonProperty("createdAt")
    Instant createdAt;
}
