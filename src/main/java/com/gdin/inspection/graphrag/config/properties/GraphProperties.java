package com.gdin.inspection.graphrag.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Data
@ConfigurationProperties(prefix = "gdin.ai.graph")
@Component
public class GraphProperties implements Serializable {
    private String nodeCollectionName = "GRAPH_NODES";
    private String edgeCollectionName = "GRAPH_EDGES";
    private Leiden leiden = new Leiden();

    private Extraction extraction = new Extraction();

    @Data
    public static class Extraction implements Serializable {
        private int maxGleanings = 3;
    }

    @Data
    public static class Leiden implements Serializable {
        private String baseUrl;
    }
}
