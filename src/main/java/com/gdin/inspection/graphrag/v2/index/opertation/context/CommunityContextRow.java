package com.gdin.inspection.graphrag.v2.index.opertation.context;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
@Builder
public class CommunityContextRow {
    Integer community;                 // schemas.COMMUNITY_ID
    Integer level;                     // schemas.COMMUNITY_LEVEL
    List<Map<String, Object>> allContext; // schemas.ALL_CONTEXT（list[dict]）
    String contextString;              // schemas.CONTEXT_STRING
    Integer contextSize;               // schemas.CONTEXT_SIZE
    Boolean contextExceedLimit;        // schemas.CONTEXT_EXCEED_FLAG
}
