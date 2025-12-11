package com.gdin.inspection.graphrag.service;

import com.alibaba.fastjson2.JSONObject;
import com.gdin.inspection.graphrag.req.milvus.MilvusUpsertReq;
import com.gdin.inspection.graphrag.util.MilvusUtil;
import com.google.gson.*;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import io.milvus.v2.service.vector.request.GetReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.response.GetResp;
import io.milvus.v2.service.vector.response.InsertResp;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.UpsertResp;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class MilvusUpsertService {
    @Resource
    private MilvusClientV2 milvusClientV2;
    @Resource
    private EmbeddingModel embeddingModel;
    @Resource
    private MilvusUtil milvusUtil;

    public UpsertResp updateEntity(MilvusUpsertReq milvusUpsertReq) {
        // 先查询出collection中的所有Schema信息
        DescribeCollectionResp describeCollectionResp = milvusClientV2.describeCollection(DescribeCollectionReq.builder()
                .collectionName(milvusUpsertReq.getCollectionName())
                .build());
        // 获取除了SparseFloatVector类型字段外的所有字段名
        List<String> fieldNames = new ArrayList<>();
        CreateCollectionReq.CollectionSchema collectionSchema = describeCollectionResp.getCollectionSchema();
        List<CreateCollectionReq.FieldSchema> fieldSchemaList = collectionSchema.getFieldSchemaList();
        for(CreateCollectionReq.FieldSchema fieldSchema : fieldSchemaList) {
            if(fieldSchema.getDataType() != DataType.SparseFloatVector) fieldNames.add(fieldSchema.getName());
        }
        // 根据id查询出要更新的实体
        GetReq getReq = GetReq.builder()
                .collectionName(milvusUpsertReq.getCollectionName())
                .ids(Collections.singletonList(milvusUpsertReq.getId()))
                .outputFields(fieldNames)
                .build();
        GetResp getResp = milvusClientV2.get(getReq);
        List<QueryResp.QueryResult> results = getResp.getGetResults();
        if(results.isEmpty()) return null;
        Map<String, Object> entity = results.get(0).getEntity();
        Map<String, Object> newEntity = new HashMap<>(entity);
        newEntity.putAll(milvusUpsertReq.getValueMap());
        // 更新实体
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonObject data = gson.toJsonTree(newEntity).getAsJsonObject();
        // 解决Bug, 如果这里直接放入data去更新, 会导致比如JSON类型的字段无法进行过滤等Bug, 这里循环遍历所有的字段类型, 然后根据以下文档说明, 重新将字段写入一遍, 即可解决该问题
        for(CreateCollectionReq.FieldSchema fieldSchema : fieldSchemaList) {
            String fieldName = fieldSchema.getName();
            DataType dataType = fieldSchema.getDataType();
            if(!data.has(fieldName)) continue;
            JsonElement value = data.get(fieldName);
            // If dataType is Bool/ Int8/ Int16/ Int32/ Int64/ Float/ Double/ Varchar, use JsonObject. addProperty(key, value) to input;
            if(dataType == DataType.Bool) data.addProperty(fieldName, value.getAsBoolean());
            else if(dataType == DataType.Int8 || dataType == DataType.Int16 || dataType == DataType.Int32) data.addProperty(fieldName, value.getAsInt());
            else if(dataType == DataType.Int64) data.addProperty(fieldName, value.getAsLong());
            else if(dataType == DataType.Float) data.addProperty(fieldName, value.getAsFloat());
            else if(dataType == DataType.Double) data.addProperty(fieldName, value.getAsDouble());
            else if(dataType == DataType.VarChar) data.addProperty(fieldName, value.getAsString());
            // If dataType is FloatVector, use JsonObject. add(key, gson. toJsonTree(List[Float]) to input;
            else if(dataType == DataType.FloatVector) {
                // 先将JsonArray转换成List<Float>
                List<Float> floatList = new ArrayList<>();
                value.getAsJsonArray().forEach(element -> floatList.add(element.getAsFloat()));
                // 再放入
                data.add(fieldName, gson.toJsonTree(floatList));
            }
            // If dataType is BinaryVector/ Float16Vector/ BFloat16Vector, use JsonObject. add(key, gson. toJsonTree(byte[])) to input;
            else if(dataType == DataType.BinaryVector || dataType == DataType.Float16Vector || dataType == DataType.BFloat16Vector) {
                // 先将JsonArray转换成byte[]
                JsonArray jsonArr = value.getAsJsonArray();
                byte[] bytes = new byte[jsonArr.size()];
                for(int i = 0; i < jsonArr.size(); i++)  bytes[i] = jsonArr.get(i).getAsByte();
                // 再放入
                data.add(fieldName, gson.toJsonTree(bytes));
            }
            // If dataType is SparseFloatVector, use JsonObject. add(key, gson. toJsonTree(SortedMap[Long, Float])) to input; 这个不实现, 这个字段一般由milvus自动管理生成
            // If dataType is Array, use JsonObject. add(key, gson. toJsonTree(List of Boolean/ Integer/ Short/ Long/ Float/ Double/ String)) to input;
            else if(dataType == DataType.Array) {
                List<Object> arrayList = new ArrayList<>();
                value.getAsJsonArray().forEach(element -> {
                        if(element.getAsJsonPrimitive().isBoolean()) arrayList.add(element.getAsBoolean());
                        else if(element.getAsJsonPrimitive().isNumber()) arrayList.add(element.getAsNumber());
                        else if(element.getAsJsonPrimitive().isString()) arrayList.add(element.getAsString());
                    }
                );
                data.add(fieldName, gson.toJsonTree(arrayList));
            }
            // If dataType is JSON, use JsonObject. add(key, JsonElement) to input;
            else if(dataType == DataType.JSON) data.add(fieldName, value);
        }
        UpsertReq upsertReq = UpsertReq.builder()
                .collectionName(milvusUpsertReq.getCollectionName())
                .data(Collections.singletonList(data))
                .build();
        return milvusClientV2.upsert(upsertReq);
    }

    public InsertResp createEntity(MilvusUpsertReq milvusUpsertReq) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonObject paramJson = gson.toJsonTree(milvusUpsertReq.getValueMap()).getAsJsonObject();
        // 先查询出collection中的所有Schema信息
        DescribeCollectionResp describeCollectionResp = milvusClientV2.describeCollection(DescribeCollectionReq.builder()
                .collectionName(milvusUpsertReq.getCollectionName())
                .build());
        // 获取所有字段名
        CreateCollectionReq.CollectionSchema collectionSchema = describeCollectionResp.getCollectionSchema();
        List<CreateCollectionReq.FieldSchema> fieldSchemaList = collectionSchema.getFieldSchemaList();
        JsonObject createJson = new JsonObject();
        for (CreateCollectionReq.FieldSchema fieldSchema : fieldSchemaList) {
            String fieldName = fieldSchema.getName();
            DataType dataType = fieldSchema.getDataType();
            if (paramJson.has(fieldName)) {
                JsonElement value = paramJson.get(fieldName);
                if (dataType == DataType.Bool) {
                    createJson.addProperty(fieldName, value.getAsBoolean());
                } else if (dataType == DataType.Int8 || dataType == DataType.Int16 || dataType == DataType.Int32) {
                    createJson.addProperty(fieldName, value.getAsInt());
                } else if (dataType == DataType.Int64) {
                    createJson.addProperty(fieldName, value.getAsLong());
                } else if (dataType == DataType.Float) {
                    createJson.addProperty(fieldName, value.getAsFloat());
                } else if (dataType == DataType.Double) {
                    createJson.addProperty(fieldName, value.getAsDouble());
                } else if (dataType == DataType.VarChar) {
                    createJson.addProperty(fieldName, value.getAsString());
                } else if (dataType == DataType.JSON) {
                    createJson.add(fieldName, value);
                } else if (dataType == DataType.Array) {
                    List<Object> arrayList = new ArrayList<>();
                    value.getAsJsonArray().forEach(element -> {
                                if (element.getAsJsonPrimitive().isBoolean()) arrayList.add(element.getAsBoolean());
                                else if (element.getAsJsonPrimitive().isNumber()) arrayList.add(element.getAsNumber());
                                else if (element.getAsJsonPrimitive().isString()) arrayList.add(element.getAsString());
                            }
                    );
                    createJson.add(fieldName, gson.toJsonTree(arrayList));
                }
            }

            if (dataType == DataType.FloatVector) {
                //获取该字段的自定义描述信息，初始化时已写入处理的目标字段名称
                String description = fieldSchema.getDescription();
                JSONObject descriptionJson = JSONObject.parseObject(description);
                String target = descriptionJson.getString("target");

                float[] vectorArr = embeddingModel.embed(paramJson.get(target).getAsString()).content().vector();
                JsonArray vector = new JsonArray();
                for (float v : vectorArr) vector.add(v);
                createJson.add(fieldName, vector);
            }
        }
        try {
            InsertResp insertResp = milvusUtil.insertByBatch(milvusUpsertReq.getCollectionName(), Collections.singletonList(createJson));
            return insertResp;
        } catch (Exception e) {
            log.error("milvus插入数据失败", e);
            throw new RuntimeException("milvus插入数据失败");
        }
    }

    public List<JsonObject> mapToEntity(String collectionName, List<Map<String, Object>> dataMaps) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        // 先查询出collection中的所有Schema信息
        DescribeCollectionResp describeCollectionResp = milvusClientV2.describeCollection(DescribeCollectionReq.builder()
                .collectionName(collectionName)
                .build());
        // 获取所有字段名
        CreateCollectionReq.CollectionSchema collectionSchema = describeCollectionResp.getCollectionSchema();
        List<CreateCollectionReq.FieldSchema> fieldSchemaList = collectionSchema.getFieldSchemaList();
        List<JsonObject> dataList = new ArrayList<>();
        for(Map<String, Object> dataMap : dataMaps) {
            JsonObject paramJson = gson.toJsonTree(dataMap).getAsJsonObject();
            JsonObject createJson = new JsonObject();
            for (CreateCollectionReq.FieldSchema fieldSchema : fieldSchemaList) {
                String fieldName = fieldSchema.getName();
                DataType dataType = fieldSchema.getDataType();
                if (paramJson.has(fieldName)) {
                    JsonElement value = paramJson.get(fieldName);
                    if (dataType == DataType.Bool) {
                        createJson.addProperty(fieldName, value.getAsBoolean());
                    } else if (dataType == DataType.Int8 || dataType == DataType.Int16 || dataType == DataType.Int32) {
                        createJson.addProperty(fieldName, value.getAsInt());
                    } else if (dataType == DataType.Int64) {
                        createJson.addProperty(fieldName, value.getAsLong());
                    } else if (dataType == DataType.Float) {
                        createJson.addProperty(fieldName, value.getAsFloat());
                    } else if (dataType == DataType.Double) {
                        createJson.addProperty(fieldName, value.getAsDouble());
                    } else if (dataType == DataType.VarChar) {
                        createJson.addProperty(fieldName, value.getAsString());
                    } else if (dataType == DataType.JSON) {
                        createJson.add(fieldName, value);
                    } else if(dataType == DataType.FloatVector) {
                        // 先将JsonArray转换成List<Float>
                        List<Float> floatList = new ArrayList<>();
                        value.getAsJsonArray().forEach(element -> floatList.add(element.getAsFloat()));
                        // 再放入
                        createJson.add(fieldName, gson.toJsonTree(floatList));
                    } else if(dataType == DataType.BinaryVector || dataType == DataType.Float16Vector || dataType == DataType.BFloat16Vector) {
                        // 先将JsonArray转换成byte[]
                        JsonArray jsonArr = value.getAsJsonArray();
                        byte[] bytes = new byte[jsonArr.size()];
                        for(int i = 0; i < jsonArr.size(); i++)  bytes[i] = jsonArr.get(i).getAsByte();
                        // 再放入
                        createJson.add(fieldName, gson.toJsonTree(bytes));
                    } else if(dataType == DataType.Array) {
                        List<Object> arrayList = new ArrayList<>();
                        value.getAsJsonArray().forEach(element -> {
                                    if(element.getAsJsonPrimitive().isBoolean()) arrayList.add(element.getAsBoolean());
                                    else if(element.getAsJsonPrimitive().isNumber()) arrayList.add(element.getAsNumber());
                                    else if(element.getAsJsonPrimitive().isString()) arrayList.add(element.getAsString());
                                }
                        );
                        createJson.add(fieldName, gson.toJsonTree(arrayList));
                    }
                }
            }
            dataList.add(createJson);
        }
        return dataList;
    }
}
