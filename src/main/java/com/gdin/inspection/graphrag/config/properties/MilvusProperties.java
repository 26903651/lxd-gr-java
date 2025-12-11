package com.gdin.inspection.graphrag.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Data
@ConfigurationProperties(prefix = "gdin.ai.milvus")
@Component
public class MilvusProperties implements Serializable {
    private String uri;
    private String token;
    private Integer defaultTopK = 10;
    private Float defaultMinScore = 0.5f;
    private Float defaultDenseWeight = 0.7f;
    private Float defaultSparseWeight = 0.3f;
    private Integer threadPoolSize = 3;
    private Integer insertBatchSize = 1000;
}
