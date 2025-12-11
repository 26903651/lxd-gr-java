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

    @JsonProperty("title")
    String title;

    @JsonProperty("source")
    String source;

    @JsonProperty("content")
    String content;

    @JsonProperty("metadata")
    Map<String, Object> metadata;

    @JsonProperty("createdAt")
    Instant createdAt;

    @JsonProperty("language")
    String language;

    @JsonProperty("tags")
    List<String> tags;

    @JsonProperty("human_readable_id")
    Integer humanReadableId;
}
