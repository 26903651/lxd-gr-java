package com.gdin.inspection.graphrag.v2.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;
import java.util.Map;

@Value
@Jacksonized
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TextUnit {

    @JsonProperty("id")
    String id;

    @JsonProperty("human_readable_id")
    Integer humanReadableId;

    @JsonProperty("text")
    String text;

    @JsonProperty("n_tokens")
    Integer nTokens;

    @JsonProperty("document_ids")
    List<String> documentIds;

    @JsonProperty("entity_ids")
    List<String> entityIds;

    @JsonProperty("relationship_ids")
    List<String> relationshipIds;

    @JsonProperty("covariate_ids")
    List<String> covariateIds;

    @JsonProperty("attributes")
    Map<String, Object> attributes;
}

