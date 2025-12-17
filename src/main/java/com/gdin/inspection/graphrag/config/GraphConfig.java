package com.gdin.inspection.graphrag.config;

import com.gdin.inspection.graphrag.config.properties.GraphProperties;
import com.gdin.inspection.graphrag.v2.index.pipeline.PipelineFactory;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
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
        initEntity();
        initRelationship();
        initCommunity();
        initCommunityReport();
        initCovariate();
    }

    @Bean
    protected PipelineFactory<Object> pipelineFactory() {
        return new PipelineFactory<>();
    }

    private void initEntity() {
        if (Boolean.FALSE.equals(milvusClientV2.hasCollection(HasCollectionReq.builder()
                .collectionName(graphProperties.getEntityCollectionName())
                .build()))) {
            CreateCollectionReq.CollectionSchema schema = MilvusClientV2.CreateSchema();
            // 内置主键 - id
            schema.addField(AddFieldReq.builder()
                    .fieldName("id")
                    .dataType(DataType.VarChar)
                    .isPrimaryKey(true)
                    .autoID(false)
                    .build());
            // human_readable_id
            schema.addField(AddFieldReq.builder()
                    .fieldName("human_readable_id")
                    .dataType(DataType.Int64)
                    .build());
            // title
            schema.addField(AddFieldReq.builder()
                    .fieldName("title")
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
            // text_unit_ids
            schema.addField(AddFieldReq.builder()
                    .fieldName("text_unit_ids")
                    .dataType(DataType.JSON)
                    .build());
            // frequency
            schema.addField(AddFieldReq.builder()
                    .fieldName("frequency")
                    .dataType(DataType.Int64)
                    .build());
            // degree
            schema.addField(AddFieldReq.builder()
                    .fieldName("degree")
                    .dataType(DataType.Int64)
                    .build());
            // x
            schema.addField(AddFieldReq.builder()
                    .fieldName("x")
                    .dataType(DataType.Double)
                    .build());
            // y
            schema.addField(AddFieldReq.builder()
                    .fieldName("y")
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
                    .collectionName(graphProperties.getEntityCollectionName())
                    .collectionSchema(schema)
                    .indexParams(indexParams)
                    .build();
            milvusClientV2.createCollection(createCollectionReq);
        }
    }

    private void initRelationship() {
        if (Boolean.FALSE.equals(milvusClientV2.hasCollection(HasCollectionReq.builder()
                .collectionName(graphProperties.getRelationshipCollectionName())
                .build()))) {
            CreateCollectionReq.CollectionSchema schema = MilvusClientV2.CreateSchema();
            // 内置主键 - id
            schema.addField(AddFieldReq.builder()
                    .fieldName("id")
                    .dataType(DataType.VarChar)
                    .isPrimaryKey(true)
                    .autoID(false)
                    .build());
            // human_readable_id
            schema.addField(AddFieldReq.builder()
                    .fieldName("human_readable_id")
                    .dataType(DataType.Int64)
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
            // description
            schema.addField(AddFieldReq.builder()
                    .fieldName("description")
                    .dataType(DataType.VarChar)
                    .maxLength(65535)
                    .build());
            // weight
            schema.addField(AddFieldReq.builder()
                    .fieldName("weight")
                    .dataType(DataType.Double)
                    .build());
            // combined_degree
            schema.addField(AddFieldReq.builder()
                    .fieldName("combined_degree")
                    .dataType(DataType.Double)
                    .build());
            // text_unit_ids
            schema.addField(AddFieldReq.builder()
                    .fieldName("text_unit_ids")
                    .dataType(DataType.JSON)
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
                    .collectionName(graphProperties.getRelationshipCollectionName())
                    .collectionSchema(schema)
                    .indexParams(indexParams)
                    .build();
            milvusClientV2.createCollection(createCollectionReq);
        }
    }

    private void initCommunity() {
        if (Boolean.FALSE.equals(milvusClientV2.hasCollection(HasCollectionReq.builder()
                .collectionName(graphProperties.getCommunityCollectionName())
                .build()))) {
            CreateCollectionReq.CollectionSchema schema = MilvusClientV2.CreateSchema();
            // 内置主键 - id
            schema.addField(AddFieldReq.builder()
                    .fieldName("id")
                    .dataType(DataType.VarChar)
                    .isPrimaryKey(true)
                    .autoID(false)
                    .build());
            // human_readable_id
            schema.addField(AddFieldReq.builder()
                    .fieldName("human_readable_id")
                    .dataType(DataType.Int64)
                    .build());
            // community
            schema.addField(AddFieldReq.builder()
                    .fieldName("community")
                    .dataType(DataType.Int64)
                    .build());
            // level
            schema.addField(AddFieldReq.builder()
                    .fieldName("level")
                    .dataType(DataType.Int64)
                    .build());
            // parent
            schema.addField(AddFieldReq.builder()
                    .fieldName("parent")
                    .dataType(DataType.Int64)
                    .build());
            // children
            schema.addField(AddFieldReq.builder()
                    .fieldName("children")
                    .dataType(DataType.JSON)
                    .build());
            // title
            schema.addField(AddFieldReq.builder()
                    .fieldName("title")
                    .dataType(DataType.VarChar)
                    .maxLength(65535)
                    .build());
            // entity_ids
            schema.addField(AddFieldReq.builder()
                    .fieldName("entity_ids")
                    .dataType(DataType.JSON)
                    .build());
            // relationship_ids
            schema.addField(AddFieldReq.builder()
                    .fieldName("relationship_ids")
                    .dataType(DataType.JSON)
                    .build());
            // text_unit_ids
            schema.addField(AddFieldReq.builder()
                    .fieldName("text_unit_ids")
                    .dataType(DataType.JSON)
                    .build());
            // period
            schema.addField(AddFieldReq.builder()
                    .fieldName("period")
                    .dataType(DataType.VarChar)
                    .maxLength(65535)
                    .build());
            // size
            schema.addField(AddFieldReq.builder()
                    .fieldName("size")
                    .dataType(DataType.Int64)
                    .build());
            CreateCollectionReq createCollectionReq = CreateCollectionReq.builder()
                    .collectionName(graphProperties.getCommunityCollectionName())
                    .collectionSchema(schema)
                    .build();
            milvusClientV2.createCollection(createCollectionReq);
        }
    }

    private void initCommunityReport() {
        if (Boolean.FALSE.equals(milvusClientV2.hasCollection(HasCollectionReq.builder()
                .collectionName(graphProperties.getCommunityReportCollectionName())
                .build()))) {
            CreateCollectionReq.CollectionSchema schema = MilvusClientV2.CreateSchema();
            // 内置主键 - id
            schema.addField(AddFieldReq.builder()
                    .fieldName("id")
                    .dataType(DataType.VarChar)
                    .isPrimaryKey(true)
                    .autoID(false)
                    .build());
            // human_readable_id
            schema.addField(AddFieldReq.builder()
                    .fieldName("human_readable_id")
                    .dataType(DataType.Int64)
                    .build());
            // community
            schema.addField(AddFieldReq.builder()
                    .fieldName("community")
                    .dataType(DataType.Int64)
                    .build());
            // level
            schema.addField(AddFieldReq.builder()
                    .fieldName("level")
                    .dataType(DataType.Int64)
                    .build());
            // parent
            schema.addField(AddFieldReq.builder()
                    .fieldName("parent")
                    .dataType(DataType.Int64)
                    .build());
            // children
            schema.addField(AddFieldReq.builder()
                    .fieldName("children")
                    .dataType(DataType.JSON)
                    .build());
            // title
            schema.addField(AddFieldReq.builder()
                    .fieldName("title")
                    .dataType(DataType.VarChar)
                    .maxLength(65535)
                    .build());
            // summary
            schema.addField(AddFieldReq.builder()
                    .fieldName("summary")
                    .dataType(DataType.VarChar)
                    .maxLength(65535)
                    .build());
            // full_content
            schema.addField(AddFieldReq.builder()
                    .fieldName("full_content")
                    .dataType(DataType.VarChar)
                    .maxLength(65535)
                    .build());
            // rank
            schema.addField(AddFieldReq.builder()
                    .fieldName("rank")
                    .dataType(DataType.Double)
                    .build());
            // rating_explanation
            schema.addField(AddFieldReq.builder()
                    .fieldName("rating_explanation")
                    .dataType(DataType.VarChar)
                    .maxLength(65535)
                    .build());
            // findings
            schema.addField(AddFieldReq.builder()
                    .fieldName("findings")
                    .dataType(DataType.JSON)
                    .build());
            // full_content_json
            schema.addField(AddFieldReq.builder()
                    .fieldName("full_content_json")
                    .dataType(DataType.JSON)
                    .build());
            // period
            schema.addField(AddFieldReq.builder()
                    .fieldName("period")
                    .dataType(DataType.VarChar)
                    .maxLength(65535)
                    .build());
            // size
            schema.addField(AddFieldReq.builder()
                    .fieldName("size")
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
                    .collectionName(graphProperties.getCommunityReportCollectionName())
                    .collectionSchema(schema)
                    .indexParams(indexParams)
                    .build();
            milvusClientV2.createCollection(createCollectionReq);
        }
    }

    private void initCovariate() {
        if (Boolean.FALSE.equals(milvusClientV2.hasCollection(HasCollectionReq.builder()
                .collectionName(graphProperties.getCovariateCollectionName())
                .build()))) {
            CreateCollectionReq.CollectionSchema schema = MilvusClientV2.CreateSchema();
            // 内置主键 - id
            schema.addField(AddFieldReq.builder()
                    .fieldName("id")
                    .dataType(DataType.VarChar)
                    .isPrimaryKey(true)
                    .autoID(false)
                    .build());
            // human_readable_id
            schema.addField(AddFieldReq.builder()
                    .fieldName("human_readable_id")
                    .dataType(DataType.Int64)
                    .build());
            // covariate_type
            schema.addField(AddFieldReq.builder()
                    .fieldName("covariate_type")
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
            // subject_id
            schema.addField(AddFieldReq.builder()
                    .fieldName("subject_id")
                    .dataType(DataType.VarChar)
                    .maxLength(65535)
                    .build());
            // object_id
            schema.addField(AddFieldReq.builder()
                    .fieldName("object_id")
                    .dataType(DataType.VarChar)
                    .maxLength(65535)
                    .build());
            // status
            schema.addField(AddFieldReq.builder()
                    .fieldName("status")
                    .dataType(DataType.VarChar)
                    .maxLength(65535)
                    .build());
            // start_date
            schema.addField(AddFieldReq.builder()
                    .fieldName("start_date")
                    .dataType(DataType.VarChar)
                    .maxLength(65535)
                    .build());
            // end_date
            schema.addField(AddFieldReq.builder()
                    .fieldName("end_date")
                    .dataType(DataType.VarChar)
                    .maxLength(65535)
                    .build());
            // source_text
            schema.addField(AddFieldReq.builder()
                    .fieldName("source_text")
                    .dataType(DataType.VarChar)
                    .maxLength(65535)
                    .build());
            // text_unit_id
            schema.addField(AddFieldReq.builder()
                    .fieldName("text_unit_id")
                    .dataType(DataType.VarChar)
                    .maxLength(65535)
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
                    .collectionName(graphProperties.getCovariateCollectionName())
                    .collectionSchema(schema)
                    .indexParams(indexParams)
                    .build();
            milvusClientV2.createCollection(createCollectionReq);
        }
    }
}
