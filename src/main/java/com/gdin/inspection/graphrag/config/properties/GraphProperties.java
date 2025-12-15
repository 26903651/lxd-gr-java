package com.gdin.inspection.graphrag.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Data
@ConfigurationProperties(prefix = "gdin.ai.graph")
@Component
public class GraphProperties implements Serializable {
    private String entityCollectionName = "GRAPH_RAG_ENTITY";
    private String relationshipCollectionName = "GRAPH_RAG_RELATIONSHIP";
    private String communityCollectionName = "GRAPH_RAG_COMMUNITY";
    private String communityReportCollectionName = "GRAPH_RAG_COMMUNITY_REPORT";
    private String covariateCollectionName = "GRAPH_RAG_COVARIATE";
    private String contentCollectionName = "AP_KNOWLEDGE_CONTENT_DEV";
    private Leiden leiden = new Leiden();

    private Extraction extraction = new Extraction();

    @Data
    public static class Extraction implements Serializable {
        private int maxGleanings = 1;
    }

    @Data
    public static class Leiden implements Serializable {
        private String baseUrl;
    }
}
