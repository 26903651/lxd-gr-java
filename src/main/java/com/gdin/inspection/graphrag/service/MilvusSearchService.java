package com.gdin.inspection.graphrag.service;

import com.gdin.inspection.graphrag.config.properties.MilvusProperties;
import com.gdin.inspection.graphrag.req.milvus.*;
import com.gdin.inspection.graphrag.search.MilvusSearch;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.SearchResp;
import jakarta.annotation.Resource;
import jakarta.validation.ValidationException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MilvusSearchService {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Resource
    private MilvusSearch milvusSearch;

    @Resource
    private MilvusClientV2 milvusClientV2;

    @Resource
    private MilvusProperties milvusProperties;

    private String searchResultToJSON(List<SearchResp.SearchResult> searchResults) {
        JsonArray jsonArray = new JsonArray();
        for(SearchResp.SearchResult searchResult : searchResults) {
            JsonObject result = new JsonObject();
            result.addProperty("id", searchResult.getId().toString());
            result.addProperty("score", searchResult.getScore());
            result.add("entity", gson.toJsonTree(searchResult.getEntity()).getAsJsonObject());
            jsonArray.add(result);
        }
        return jsonArray.toString();
    }

    private String queryResultToJSON(List<QueryResp.QueryResult> queryResults) {
        JsonArray jsonArray = new JsonArray();
        for(QueryResp.QueryResult queryResult : queryResults) {
            jsonArray.add(gson.toJsonTree(queryResult.getEntity()).getAsJsonObject());
        }
        return jsonArray.toString();
    }

    public String semantic(MilvusSemanticSearchReq semanticSearchReq) {
        // 先从Milvus中查询denseFieldName
        DescribeCollectionResp describeCollectionResp = milvusClientV2.describeCollection(DescribeCollectionReq.builder()
                .collectionName(semanticSearchReq.getCollectionName())
                .build());
        CreateCollectionReq.CollectionSchema collectionSchema = describeCollectionResp.getCollectionSchema();
        List<CreateCollectionReq.FieldSchema> fieldSchemaList = collectionSchema.getFieldSchemaList();
        String denseFieldName = null;
        for(CreateCollectionReq.FieldSchema fieldSchema : fieldSchemaList) {
            if(fieldSchema.getDataType() == DataType.FloatVector) {
                denseFieldName = fieldSchema.getName();
                break;
            }
        }
        if(denseFieldName==null) throw new ValidationException("集合中不存在稠密向量字段");
        int topK = semanticSearchReq.getTopK()==null?milvusProperties.getDefaultTopK():semanticSearchReq.getTopK();
        List<SearchResp.SearchResult> searchResults = null;
        if(semanticSearchReq.getGroupByFieldName()!=null){
            // 使用分组查询功能
            searchResults = milvusSearch.semanticSearchByGroup(semanticSearchReq.getCollectionName(), semanticSearchReq.getQuery(), denseFieldName, topK, semanticSearchReq.getFilter(), semanticSearchReq.getOutputFields(), semanticSearchReq.getGroupByFieldName(), semanticSearchReq.getGroupSize());
        }
        else {
            // 使用最小分数查询功能
            searchResults = milvusSearch.semanticSearchByScore(semanticSearchReq.getCollectionName(), semanticSearchReq.getQuery(), denseFieldName, topK, semanticSearchReq.getFilter(), semanticSearchReq.getOutputFields(), semanticSearchReq.getMinScore());
        }
        return searchResultToJSON(searchResults);
    }

    public String keyword(MilvusKeywordSearchReq keywordSearchReq) {
        // 先从Milvus中查询sparseFieldName
        DescribeCollectionResp describeCollectionResp = milvusClientV2.describeCollection(DescribeCollectionReq.builder()
                .collectionName(keywordSearchReq.getCollectionName())
                .build());
        CreateCollectionReq.CollectionSchema collectionSchema = describeCollectionResp.getCollectionSchema();
        List<CreateCollectionReq.FieldSchema> fieldSchemaList = collectionSchema.getFieldSchemaList();
        String sparseFieldName = null;
        for(CreateCollectionReq.FieldSchema fieldSchema : fieldSchemaList) {
            if(fieldSchema.getDataType() == DataType.SparseFloatVector) {
                sparseFieldName = fieldSchema.getName();
                break;
            }
        }
        if(sparseFieldName==null) throw new ValidationException("集合中不存在稀疏向量字段");
        int topK = keywordSearchReq.getTopK()==null?milvusProperties.getDefaultTopK():keywordSearchReq.getTopK();
        List<SearchResp.SearchResult> searchResults = null;
        if(keywordSearchReq.getGroupByFieldName()!=null){
            // 使用分组查询功能
            searchResults = milvusSearch.keywordSearchByGroup(keywordSearchReq.getCollectionName(), keywordSearchReq.getQuery(), sparseFieldName, topK, keywordSearchReq.getFilter(), keywordSearchReq.getOutputFields(), keywordSearchReq.getGroupByFieldName(), keywordSearchReq.getGroupSize());
        }
        else {
            // 使用最小分数查询功能
            searchResults = milvusSearch.keywordSearchByScore(keywordSearchReq.getCollectionName(), keywordSearchReq.getQuery(), sparseFieldName, topK, keywordSearchReq.getFilter(), keywordSearchReq.getOutputFields(), keywordSearchReq.getMinScore());
        }
        return searchResultToJSON(searchResults);
    }

    public String hybrid(MilvusHybridSearchReq hybridSearchReq) {
        // 先从Milvus中查询denseFieldName和sparseFieldName
        DescribeCollectionResp describeCollectionResp = milvusClientV2.describeCollection(DescribeCollectionReq.builder()
                .collectionName(hybridSearchReq.getCollectionName())
                .build());
        CreateCollectionReq.CollectionSchema collectionSchema = describeCollectionResp.getCollectionSchema();
        List<CreateCollectionReq.FieldSchema> fieldSchemaList = collectionSchema.getFieldSchemaList();
        String denseFieldName = null;
        String sparseFieldName = null;
        for(CreateCollectionReq.FieldSchema fieldSchema : fieldSchemaList) {
            if(fieldSchema.getDataType() == DataType.FloatVector) denseFieldName = fieldSchema.getName();
            if(fieldSchema.getDataType() == DataType.SparseFloatVector) sparseFieldName = fieldSchema.getName();
            if(denseFieldName!=null&&sparseFieldName!=null) break;
        }
        if(denseFieldName==null||sparseFieldName==null) throw new ValidationException("集合中不存在稠密或稀疏向量字段");
        int topK = hybridSearchReq.getTopK()==null?milvusProperties.getDefaultTopK():hybridSearchReq.getTopK();
        float denseWeight = hybridSearchReq.getDenseWeight()==null?milvusProperties.getDefaultDenseWeight():hybridSearchReq.getDenseWeight();
        float sparseWeight = hybridSearchReq.getSparseWeight()==null?milvusProperties.getDefaultSparseWeight():hybridSearchReq.getSparseWeight();
        List<SearchResp.SearchResult> searchResults = milvusSearch.hybridSearch(hybridSearchReq.getCollectionName(), hybridSearchReq.getQuery(), denseFieldName, hybridSearchReq.getQuery(), sparseFieldName, topK, denseWeight, sparseWeight, hybridSearchReq.getFilter(), hybridSearchReq.getOutputFields(), hybridSearchReq.getMinScore());
        return searchResultToJSON(searchResults);
    }

    public String get(MilvusGetReq milvusGetReq) {
        List<QueryResp.QueryResult> queryResults = milvusSearch.get(milvusGetReq.getCollectionName(), milvusGetReq.getIds(), milvusGetReq.getOutputFields());
        return queryResultToJSON(queryResults);
    }

    public String query(MilvusQueryReq milvusQueryReq) {
        List<QueryResp.QueryResult> queryResults = milvusSearch.query(milvusQueryReq.getCollectionName(), milvusQueryReq.getFilter(), milvusQueryReq.getOutputFields(), milvusQueryReq.getLimit());
        return queryResultToJSON(queryResults);
    }
}
