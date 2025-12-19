package com.gdin.inspection.graphrag.v2;

import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.service.collection.request.AddCollectionFieldReq;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

@Slf4j
@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = "environment.test=true")
public class TempTest {
    @Resource
    private MilvusClientV2 milvusClientV2;

    @Test
    void addCollectionField(){
        List<String> fieldList = List.of("graph_main", "graph_document_ids", "graph_entity_ids", "graph_relationship_ids", "graph_covariate_ids");
        String collectionName = "AP_KNOWLEDGE_CONTENT_DEV";
        for (String field : fieldList) {
            milvusClientV2.addCollectionField(AddCollectionFieldReq.builder()
                    .collectionName(collectionName)
                    .fieldName(field)
                    .dataType(DataType.JSON)
                    .isNullable(true)
                    .build()
            );
        }
    }
}
