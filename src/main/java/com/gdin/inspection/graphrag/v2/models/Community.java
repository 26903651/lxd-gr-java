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
public class Community {

    @JsonProperty("id")
    String id;

    @JsonProperty("human_readable_id")
    Integer humanReadableId;

    // Python 里有一个单独的 "community" 列，通常就是聚类出来的整数编号
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

    @JsonProperty("entity_ids")
    List<String> entityIds;

    @JsonProperty("relationship_ids")
    List<String> relationshipIds;

    @JsonProperty("text_unit_ids")
    List<String> textUnitIds;

    @JsonProperty("period")
    String period;

    @JsonProperty("size")
    Integer size;

    @JsonProperty("summary")
    String summary;

    @JsonProperty("metadata")
    Map<String, Object> metadata;

    @JsonProperty("createdAt")
    Instant createdAt;
}