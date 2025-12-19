package com.gdin.inspection.graphrag.v2.index.opertation.context;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
@Builder
public class SubCommunityContext {
    Integer subCommunity;                 // schemas.SUB_COMMUNITY
    List<Map<String, Object>> allContext; // schemas.ALL_CONTEXT
    String fullContent;                   // schemas.FULL_CONTENT
    Integer contextSize;                  // schemas.CONTEXT_SIZE
}
