package com.gdin.inspection.graphrag.config;

import com.gdin.inspection.graphrag.config.properties.MilvusProperties;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MilvusConfig {
    @Resource
    private MilvusProperties milvusProperties;

    @Bean
    public MilvusClientV2 milvusClientV2() {
        ConnectConfig connectConfig = ConnectConfig.builder()
                .uri(milvusProperties.getUri())
                .token(milvusProperties.getToken())
                .build();
        return new MilvusClientV2(connectConfig);
    }
}
