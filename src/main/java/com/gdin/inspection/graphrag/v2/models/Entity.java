package com.gdin.inspection.graphrag.v2.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

@Data
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

    @JsonProperty("description")
    String description;

    @JsonProperty("text_unit_ids")
    List<String> textUnitIds;

    @JsonProperty("frequency")
    Integer frequency;

    @JsonProperty("degree")
    Integer degree;

    @JsonProperty("x")
    Double x;

    @JsonProperty("y")
    Double y;
}
