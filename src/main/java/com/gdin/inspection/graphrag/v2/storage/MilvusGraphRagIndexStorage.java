package com.gdin.inspection.graphrag.v2.storage;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.gdin.inspection.graphrag.config.properties.GraphProperties;
import com.gdin.inspection.graphrag.util.IOUtil;
import com.gdin.inspection.graphrag.util.MilvusUtil;
import com.gdin.inspection.graphrag.v2.models.*;
import com.gdin.inspection.graphrag.v2.util.TokenUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.milvus.orm.iterator.QueryIterator;
import io.milvus.response.QueryResultsWrapper;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.QueryIteratorReq;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 把 GraphRAG 的几个“表”写入 Milvus：
 *
 * - entities           -> ENTITY collection
 * - relationships      -> RELATIONSHIP collection
 * - communities        -> COMMUNITY collection
 * - community_reports  -> COMMUNITY_REPORT collection
 *
 * 字段严格对齐 Python 的 data_model.schemas 中的 *_FINAL_COLUMNS。
 */
@Slf4j
@Component
public class MilvusGraphRagIndexStorage implements GraphRagIndexStorage {
    @Resource
    private EmbeddingModel embeddingModel;

    @Resource
    private MilvusUtil milvusUtil;

    @Resource
    private GraphProperties graphProperties;

    @Resource
    private MilvusClientV2 milvusClientV2;

    @Resource
    private TokenUtil tokenUtil;

    private final Gson gson = new Gson();


    /* ========== entities.parquet -> ENTITY_COLLECTION ========== */

    public void saveEntities(List<Entity> entities) {
        if (CollectionUtil.isEmpty(entities)) {
            log.info("saveEntities: 没有实体需要写入");
            return;
        }
        List<JsonObject> rows = new ArrayList<>(entities.size());
        for (Entity e : entities) {
            if (e == null) continue;
            JsonObject obj = new JsonObject();

            safeAddString(obj, "id", e.getId());
            safeAddInt(obj, "human_readable_id", e.getHumanReadableId());
            safeAddString(obj, "title", e.getTitle());
            safeAddString(obj, "type", e.getType());
            safeAddString(obj, "description", e.getDescription());
            addStringList(obj, "text_unit_ids", e.getTextUnitIds());
            safeAddInt(obj, "frequency", e.getFrequency());
            safeAddInt(obj, "degree", e.getDegree());
            safeAddDouble(obj, "x", e.getX());
            safeAddDouble(obj, "y", e.getY());
            safeAddEmbedding(obj, "embedding", e.getDescription());

            rows.add(obj);
        }

        try {
            milvusUtil.insertByBatch(graphProperties.getCollectionNames().getEntityCollectionName(), rows);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        log.info("saveEntities: 已写入 {} 条实体到 {}", rows.size(), graphProperties.getCollectionNames().getEntityCollectionName());
    }

    @Override
    public List<Entity> loadEntities() {
        List<QueryResultsWrapper.RowRecord> rowRecords = queryAllData(graphProperties.getCollectionNames().getEntityCollectionName(), List.of("id", "human_readable_id", "title", "type", "description", "text_unit_ids", "frequency", "degree", "x", "y"));
        try {
            List<Entity> entities = rowRecordsToModels(rowRecords, Entity.class);
            entities.sort(Comparator.comparingInt(t -> t.getHumanReadableId() == null ? -1 : t.getHumanReadableId()));
            return entities;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /* ========== relationships.parquet -> RELATIONSHIP_COLLECTION ========== */

    public void saveRelationships(List<Relationship> relationships) {
        if (CollectionUtil.isEmpty(relationships)) {
            log.info("saveRelationships: 没有关系需要写入");
            return;
        }
        List<JsonObject> rows = new ArrayList<>(relationships.size());
        for (Relationship r : relationships) {
            if (r == null) continue;
            JsonObject obj = new JsonObject();

            safeAddString(obj, "id", r.getId());
            safeAddInt(obj, "human_readable_id", r.getHumanReadableId());
            safeAddString(obj, "source", r.getSource());
            safeAddString(obj, "target", r.getTarget());
            safeAddString(obj, "description", r.getDescription());
            safeAddDouble(obj, "weight", r.getWeight());
            safeAddDouble(obj, "combined_degree", r.getCombinedDegree());
            addStringList(obj, "text_unit_ids", r.getTextUnitIds());
            safeAddEmbedding(obj, "embedding", r.getDescription());

            rows.add(obj);
        }

        try {
            milvusUtil.insertByBatch(graphProperties.getCollectionNames().getRelationshipCollectionName(), rows);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        log.info("saveRelationships: 已写入 {} 条关系到 {}", rows.size(), graphProperties.getCollectionNames().getRelationshipCollectionName());
    }

    @Override
    public List<Relationship> loadRelationships() {
        List<QueryResultsWrapper.RowRecord> rowRecords = queryAllData(graphProperties.getCollectionNames().getRelationshipCollectionName(), List.of("id", "human_readable_id", "source", "target", "description", "weight", "combined_degree", "text_unit_ids"));
        try {
            List<Relationship> relationships = rowRecordsToModels(rowRecords, Relationship.class);
            relationships.sort(Comparator.comparingInt(t -> t.getHumanReadableId() == null ? -1 : t.getHumanReadableId()));
            return relationships;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<TextUnit> loadTextUnits() {
        try {
            List<TextUnit> textUnits = new ArrayList<>();
            String filter = "extra[\"graph\"] == \"1\"";
            List<QueryResultsWrapper.RowRecord> rowRecords = queryAllData(graphProperties.getCollectionNames().getContentCollectionName(), List.of("metadata", "page_content", "graph_main", "graph_document_ids", "graph_entity_ids", "graph_relationship_ids", "graph_covariate_ids"), filter);
            for (QueryResultsWrapper.RowRecord rowRecord : rowRecords) {
                Map<String, Object> fieldValues = rowRecord.getFieldValues();
                String pageContent = (String) fieldValues.get("page_content");
                Map<String, Object> metadata = (Map<String, Object>) fieldValues.get("metadata");
                Map<String, Object> graphMain = (Map<String, Object>) fieldValues.get("graph_main");
                Integer humanReadableId = graphMain.containsKey("human_readable_id")? (Integer) graphMain.get("human_readable_id") : null;
                int nTokens = graphMain.containsKey("n_tokens")? (Integer) graphMain.get("n_tokens") : tokenUtil.getTokenCount(pageContent);
                String docId = (String) metadata.get("doc_id");
                List<String> documentIds = IOUtil.convertValue(fieldValues.get("graph_document_ids"), List.class);
                List<String> entityIds = IOUtil.convertValue(fieldValues.get("graph_entity_ids"), List.class);
                List<String> relationshipIds = IOUtil.convertValue(fieldValues.get("graph_relationship_ids"), List.class);
                List<String> covariateIds = IOUtil.convertValue(fieldValues.get("graph_covariate_ids"), List.class);
                textUnits.add(TextUnit.builder()
                        .id(docId)
                        .humanReadableId(humanReadableId)
                        .text(pageContent)
                        .nTokens(nTokens)
                        .documentIds(documentIds)
                        .entityIds(entityIds)
                        .relationshipIds(relationshipIds)
                        .covariateIds(covariateIds)
                        .build());
            }
            // 对齐 Python：上一轮 human_readable_id 是连续的，所以这里也按 hrid 排序，避免后续 max() 被乱序影响
            textUnits.sort(Comparator.comparingInt(t -> t.getHumanReadableId() == null ? -1 : t.getHumanReadableId()));

            return textUnits;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Document> loadDocuments() {
        throw new RuntimeException("不应该被调用, 没有必要使用这个方法");
    }

    /* ========== communities.parquet -> COMMUNITY_COLLECTION ========== */

    public void saveCommunities(List<Community> communities) {
        if (CollectionUtil.isEmpty(communities)) {
            log.info("saveCommunities: 没有社区需要写入");
            return;
        }
        List<JsonObject> rows = new ArrayList<>(communities.size());
        for (Community c : communities) {
            if (c == null) continue;
            JsonObject obj = new JsonObject();

            safeAddString(obj, "id", c.getId());
            safeAddInt(obj, "human_readable_id", c.getHumanReadableId());
            safeAddInt(obj, "community", c.getCommunity());
            safeAddInt(obj, "level", c.getLevel());
            safeAddInt(obj, "parent", c.getParent());
            addIntList(obj, "children", c.getChildren());
            safeAddString(obj, "title", c.getTitle());
            addStringList(obj, "entity_ids", c.getEntityIds());
            addStringList(obj, "relationship_ids", c.getRelationshipIds());
            addStringList(obj, "text_unit_ids", c.getTextUnitIds());
            safeAddString(obj, "period", c.getPeriod());
            safeAddInt(obj, "size", c.getSize());
            safeAddEmbedding(obj, "embedding", c.getTitle());

            rows.add(obj);
        }

        try {
            milvusUtil.insertByBatch(graphProperties.getCollectionNames().getCommunityCollectionName(), rows);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        log.info("saveCommunities: 已写入 {} 条社区到 {}", rows.size(), graphProperties.getCollectionNames().getCommunityCollectionName());
    }

    @Override
    public List<Community> loadCommunities() {
        List<QueryResultsWrapper.RowRecord> rowRecords = queryAllData(graphProperties.getCollectionNames().getCommunityCollectionName(), List.of("id", "human_readable_id", "community", "level", "parent", "children", "title", "entity_ids", "relationship_ids", "text_unit_ids", "period", "size"));
        try {
            List<Community> communities = rowRecordsToModels(rowRecords, Community.class);
            communities.sort(Comparator.comparingInt(t -> t.getHumanReadableId() == null ? -1 : t.getHumanReadableId()));
            return communities;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /* ========== community_reports.parquet -> COMMUNITY_REPORT_COLLECTION ========== */

    public void saveCommunityReports(List<CommunityReport> reports) {
        if (CollectionUtil.isEmpty(reports)) {
            log.info("saveCommunityReports: 没有社区报告需要写入");
            return;
        }
        List<JsonObject> rows = new ArrayList<>(reports.size());
        for (CommunityReport r : reports) {
            if (r == null) continue;
            JsonObject obj = new JsonObject();

            safeAddString(obj, "id", r.getId());
            safeAddInt(obj, "human_readable_id", r.getHumanReadableId());
            safeAddInt(obj, "community", r.getCommunity());
            safeAddInt(obj, "level", r.getLevel());
            safeAddInt(obj, "parent", r.getParent());
            addIntList(obj, "children", r.getChildren());
            safeAddString(obj, "title", r.getTitle());
            safeAddString(obj, "summary", r.getSummary());
            safeAddString(obj, "full_content", r.getFullContent());
            safeAddDouble(obj, "rank", r.getRank());
            safeAddString(obj, "rating_explanation", r.getRatingExplanation());
            // 这里的 findings / full_content_json 在 Java 模型里已经是 JSON 字符串
            safeAddString(obj, "findings", r.getFindings());
            safeAddString(obj, "full_content_json", r.getFullContentJson());
            safeAddString(obj, "period", r.getPeriod());
            safeAddInt(obj, "size", r.getSize());
            safeAddEmbedding(obj, "embedding", r.getSummary());

            rows.add(obj);
        }

        try {
            milvusUtil.insertByBatch(graphProperties.getCollectionNames().getCommunityReportCollectionName(), rows);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        log.info("saveCommunityReports: 已写入 {} 条社区报告到 {}", rows.size(), graphProperties.getCollectionNames().getCommunityReportCollectionName());
    }

    @Override
    public List<CommunityReport> loadCommunityReports() {
        List<QueryResultsWrapper.RowRecord> rowRecords = queryAllData(graphProperties.getCollectionNames().getCommunityReportCollectionName(), List.of("id", "human_readable_id", "community", "level", "parent", "children", "title", "summary", "full_content", "rank", "rating_explanation", "findings", "full_content_json", "period", "size"));
        try {
            List<CommunityReport> communityReports = rowRecordsToModels(rowRecords, CommunityReport.class);
            communityReports.sort(Comparator.comparingInt(t -> t.getHumanReadableId() == null ? -1 : t.getHumanReadableId()));
            return communityReports;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void saveCovariates(List<Covariate> covariates) {
        if (CollectionUtil.isEmpty(covariates)) {
            log.info("saveCovariates: 没有 covariates 需要写入");
            return;
        }

        List<JsonObject> rows = new ArrayList<>(covariates.size());
        for (Covariate c : covariates) {
            if (c == null) continue;
            JsonObject obj = new JsonObject();

            safeAddString(obj, "id", c.getId());
            safeAddInt(obj, "human_readable_id", c.getHumanReadableId());
            safeAddString(obj, "covariate_type", c.getCovariateType());
            safeAddString(obj, "type", c.getType());
            safeAddString(obj, "description", c.getDescription());
            safeAddString(obj, "subject_id", c.getSubjectId());
            safeAddString(obj, "object_id", c.getObjectId());
            safeAddString(obj, "status", c.getStatus());
            safeAddInstantAsString(obj, "start_date", c.getStartDate());
            safeAddInstantAsString(obj, "end_date", c.getEndDate());
            safeAddString(obj, "source_text", c.getSourceText());
            safeAddString(obj, "text_unit_id", c.getTextUnitId());
            safeAddString(obj, "record_id", c.getRecordId());

            // 向量：description 优先，其次 source_text
            String embedText = !StrUtil.isBlank(c.getDescription()) ? c.getDescription() : c.getSourceText();
            safeAddEmbedding(obj, "embedding", embedText);

            rows.add(obj);
        }

        try {
            milvusUtil.insertByBatch(graphProperties.getCollectionNames().getCovariateCollectionName(), rows);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        log.info("saveCovariates: 已写入 {} 条到 {}", rows.size(), graphProperties.getCollectionNames().getCovariateCollectionName());
    }

    @Override
    public List<Covariate> loadCovariates() {
        List<QueryResultsWrapper.RowRecord> rowRecords = queryAllData(graphProperties.getCollectionNames().getCovariateCollectionName(), List.of("id", "human_readable_id", "covariate_type", "type", "description", "subject_id", "object_id", "status", "start_date", "end_date", "source_text", "text_unit_id", "record_id"));
        try {
            List<Covariate> covariates = rowRecordsToModels(rowRecords, Covariate.class);
            covariates.sort(Comparator.comparingInt(t -> t.getHumanReadableId() == null ? -1 : t.getHumanReadableId()));
            return covariates;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    /* =================== 小工具方法 =================== */

    private List<QueryResultsWrapper.RowRecord> queryAllData(String collectionName, List<String> outputFields) {
        return queryAllData(collectionName, outputFields, null);
    }

    private List<QueryResultsWrapper.RowRecord> queryAllData(String collectionName, List<String> outputFields, String filter) {
        List<QueryResultsWrapper.RowRecord> allRows = new ArrayList<>();
        QueryIteratorReq.QueryIteratorReqBuilder queryIteratorReqBuilder = QueryIteratorReq.builder()
                .collectionName(collectionName)
                .batchSize(2000)
                .outputFields(outputFields);
        if(!StrUtil.isBlank(filter)) queryIteratorReqBuilder.expr(filter);
        QueryIteratorReq iteratorReq = queryIteratorReqBuilder.build();

        QueryIterator iterator = milvusClientV2.queryIterator(iteratorReq);
        while (true) {
            List<QueryResultsWrapper.RowRecord> rows = iterator.next();
            if (CollectionUtil.isEmpty(rows)) {
                iterator.close();
                break;
            }
            allRows.addAll(rows);
        }
        return allRows;
    }

    private <T> List<T> rowRecordsToModels(List<QueryResultsWrapper.RowRecord> rowRecordList, Class<T> modelClass) throws IOException {
        List<T> modelList = new ArrayList<>();
        for (QueryResultsWrapper.RowRecord rowRecord : rowRecordList) {
            modelList.add(IOUtil.convertValue(rowRecord, modelClass));
        }
        return modelList;
    }

    private void safeAddString(JsonObject obj, String field, String value) {
        if (value != null) obj.addProperty(field, value);
    }

    private void safeAddInt(JsonObject obj, String field, Integer value) {
        if (value != null) obj.addProperty(field, value);
    }

    private void safeAddDouble(JsonObject obj, String field, Double value) {
        if (value != null) obj.addProperty(field, value);
    }

    private void safeAddEmbedding(JsonObject obj, String field, String text) {
        if (StrUtil.isBlank(text)) text = "";
        float[] vectorArr = embeddingModel.embed(text).content().vector();
        JsonArray vector = new JsonArray();
        for (float v : vectorArr) vector.add(v);
        obj.add(field, vector);
    }

    private void addStringList(JsonObject obj, String field, List<String> list) {
        if (CollectionUtil.isEmpty(list)) return;
        JsonArray arr = new JsonArray();
        for (String s : list) {
            if (s == null) continue;
            arr.add(s);
        }
        if (!arr.isEmpty()) obj.add(field, arr);
    }

    private void addIntList(JsonObject obj, String field, List<Integer> list) {
        if (CollectionUtil.isEmpty(list)) return;
        JsonArray arr = new JsonArray();
        for (Integer v : list) {
            if (v == null) continue;
            arr.add(v);
        }
        if (!arr.isEmpty()) obj.add(field, arr);
    }

    private void safeAddInstantAsString(JsonObject obj, String field, Instant value) {
        if (value != null) obj.addProperty(field, value.toString()); // ISO-8601
    }

    private void safeAddMetadata(JsonObject obj, String field, Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) return;
        JsonElement tree = gson.toJsonTree(metadata);
        if (tree != null && tree.isJsonObject()) {
            obj.add(field, tree.getAsJsonObject());
        } else if (tree != null) {
            obj.add(field, tree);
        }
    }
}
