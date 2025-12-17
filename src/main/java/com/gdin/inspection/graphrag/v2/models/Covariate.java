package com.gdin.inspection.graphrag.v2.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;

@Value
@Jacksonized
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Covariate {

    @JsonProperty("id")
    String id;

    @JsonProperty("human_readable_id")
    Integer humanReadableId;

    @JsonProperty("covariate_type")
    String covariateType;

    @JsonProperty("type")
    String type;

    @JsonProperty("description")
    String description;

    @JsonProperty("subject_id")
    String subjectId;

    @JsonProperty("object_id")
    String objectId;

    @JsonProperty("status")
    String status;

    @JsonProperty("start_date")
    Instant startDate;

    @JsonProperty("end_date")
    Instant endDate;

    @JsonProperty("source_text")
    String sourceText;

    @JsonProperty("text_unit_id")
    String textUnitId;

    @JsonProperty("record_id")
    String recordId;
}
