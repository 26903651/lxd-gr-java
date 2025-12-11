package com.gdin.inspection.graphrag.config;

import com.gdin.inspection.graphrag.config.properties.GraphProperties;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;

import java.util.*;

@Configuration
public class GraphConfig {
    @Resource
    private MilvusClientV2 milvusClientV2;
    @Resource
    private GraphProperties graphProperties;

    @PostConstruct
    private void init() {
        initNodes();
        initEdges();
    }

    private void initNodes() {
        if (Boolean.FALSE.equals(milvusClientV2.hasCollection(HasCollectionReq.builder()
                .collectionName(graphProperties.getNodeCollectionName())
                .build()))) {
            CreateCollectionReq.CollectionSchema schema = MilvusClientV2.CreateSchema();
            // 内置主键 - id
            schema.addField(AddFieldReq.builder()
                    .fieldName("id")
                    .dataType(DataType.VarChar)
                    .isPrimaryKey(true)
                    .autoID(false)
                    .build());
            // name
            schema.addField(AddFieldReq.builder()
                    .fieldName("name")
                    .dataType(DataType.VarChar)
                    .maxLength(65535)
                    .build());
            // type
            schema.addField(AddFieldReq.builder()
                    .fieldName("type")
                    .dataType(DataType.VarChar)
                    .maxLength(65535)
                    .build());
            // description
            schema.addField(AddFieldReq.builder()
                    .fieldName("description")
                    .dataType(DataType.VarChar)
                    .maxLength(65535)
                    .build());
            // 元数据 - source_doc_ids
            schema.addField(AddFieldReq.builder()
                    .fieldName("source_doc_ids")
                    .dataType(DataType.JSON)
                    .build());
            // frequency
            schema.addField(AddFieldReq.builder()
                    .fieldName("frequency")
                    .dataType(DataType.Int64)
                    .build());
            // embedding
            schema.addField(AddFieldReq.builder()
                    .fieldName("embedding")
                    .dataType(DataType.FloatVector)
                    .dimension(1024)
                    .build());

            // 创建索引
            IndexParam vectorIndex = IndexParam.builder()
                    .fieldName("embedding")
                    .indexType(IndexParam.IndexType.HNSW)
                    .metricType(IndexParam.MetricType.IP)
                    .extraParams(Map.of("M", 8, "efConstruction", 64))
                    .build();
            List<IndexParam> indexParams = new ArrayList<>();
            indexParams.add(vectorIndex);
            CreateCollectionReq createCollectionReq = CreateCollectionReq.builder()
                    .collectionName(graphProperties.getNodeCollectionName())
                    .collectionSchema(schema)
                    .indexParams(indexParams)
                    .build();
            milvusClientV2.createCollection(createCollectionReq);
        }
    }

    private void initEdges() {
        if (Boolean.FALSE.equals(milvusClientV2.hasCollection(HasCollectionReq.builder()
                .collectionName(graphProperties.getEdgeCollectionName())
                .build()))) {
            CreateCollectionReq.CollectionSchema schema = MilvusClientV2.CreateSchema();
            // 内置主键 - id
            schema.addField(AddFieldReq.builder()
                    .fieldName("id")
                    .dataType(DataType.VarChar)
                    .isPrimaryKey(true)
                    .autoID(false)
                    .build());
            // source
            schema.addField(AddFieldReq.builder()
                    .fieldName("source")
                    .dataType(DataType.VarChar)
                    .maxLength(65535)
                    .build());
            // target
            schema.addField(AddFieldReq.builder()
                    .fieldName("target")
                    .dataType(DataType.VarChar)
                    .maxLength(65535)
                    .build());
            // relation_type
            schema.addField(AddFieldReq.builder()
                    .fieldName("relation_type")
                    .dataType(DataType.VarChar)
                    .maxLength(65535)
                    .build());
            // description
            schema.addField(AddFieldReq.builder()
                    .fieldName("description")
                    .dataType(DataType.VarChar)
                    .maxLength(65535)
                    .build());
            // source_doc_ids
            schema.addField(AddFieldReq.builder()
                    .fieldName("source_doc_ids")
                    .dataType(DataType.JSON)
                    .build());
            // frequency
            schema.addField(AddFieldReq.builder()
                    .fieldName("weight")
                    .dataType(DataType.Double)
                    .build());
            // embedding
            schema.addField(AddFieldReq.builder()
                    .fieldName("embedding")
                    .dataType(DataType.FloatVector)
                    .dimension(1024)
                    .build());

            // 创建索引
            IndexParam vectorIndex = IndexParam.builder()
                    .fieldName("embedding")
                    .indexType(IndexParam.IndexType.HNSW)
                    .metricType(IndexParam.MetricType.IP)
                    .extraParams(Map.of("M", 8, "efConstruction", 64))
                    .build();
            List<IndexParam> indexParams = new ArrayList<>();
            indexParams.add(vectorIndex);
            CreateCollectionReq createCollectionReq = CreateCollectionReq.builder()
                    .collectionName(graphProperties.getEdgeCollectionName())
                    .collectionSchema(schema)
                    .indexParams(indexParams)
                    .build();
            milvusClientV2.createCollection(createCollectionReq);
        }
    }
}
