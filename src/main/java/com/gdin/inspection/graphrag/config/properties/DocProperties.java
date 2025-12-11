package com.gdin.inspection.graphrag.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Data
@ConfigurationProperties(prefix = "gdin.ai.doc")
@Component
public class DocProperties implements Serializable {
    private String tokenizerType;

    private HanlpTokenizerProperties hanlpTokenizer = new HanlpTokenizerProperties();

    @Data
    public static class HanlpTokenizerProperties implements Serializable {
        private String baseUrl;
        private String model;
        private String posModel;
        private Long timeoutInSeconds;
    }
}
