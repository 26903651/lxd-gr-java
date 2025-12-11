package com.gdin.inspection.graphrag.v2.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Jacksonized
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RelationshipDescriptionSummary {

    @JsonProperty("source_entity_id")
    String sourceEntityId;

    @JsonProperty("target_entity_id")
    String targetEntityId;

    @JsonProperty("summary")
    String summary;
}
