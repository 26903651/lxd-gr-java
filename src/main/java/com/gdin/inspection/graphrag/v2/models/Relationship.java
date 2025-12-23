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
public class Relationship {

    @JsonProperty("id")
    String id;

    @JsonProperty("human_readable_id")
    Integer humanReadableId;

    @JsonProperty("source")
    String source;

    @JsonProperty("target")
    String target;

    @JsonProperty("description")
    String description;

    @JsonProperty("weight")
    Double weight;

    @JsonProperty("combined_degree")
    Double combinedDegree;

    @JsonProperty("text_unit_ids")
    List<String> textUnitIds;
}
