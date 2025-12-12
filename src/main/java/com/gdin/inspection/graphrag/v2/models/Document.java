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
public class Document {

    @JsonProperty("id")
    String id;

    @JsonProperty("human_readable_id")
    Integer humanReadableId;

    @JsonProperty("title")
    String title;

    @JsonProperty("text")
    String text;

    @JsonProperty("text_unit_ids")
    List<String> textUnitIds;

    @JsonProperty("creation_date")
    Instant creationDate;

    @JsonProperty("metadata")
    Map<String, Object> metadata;
}
