package com.gdin.inspection.graphrag.v2.index.strategy;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CommunityReportsStrategy {
    Integer maxContextTokens;
    Integer maxReportLength;
    Integer concurrentRequests;
}
