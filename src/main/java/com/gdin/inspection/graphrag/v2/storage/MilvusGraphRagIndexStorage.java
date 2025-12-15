package com.gdin.inspection.graphrag.v2.storage;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.gdin.inspection.graphrag.config.properties.GraphProperties;
import com.gdin.inspection.graphrag.util.MilvusUtil;
import com.gdin.inspection.graphrag.v2.models.*;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
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
public class MilvusGraphRagIndexStorage {
    @Resource
    private EmbeddingModel embeddingModel;

    @Resource
    private MilvusUtil milvusUtil;

    @Resource
    private GraphProperties graphProperties;

    private final Gson gson = new Gson();


    /* ========== entities.parquet -> ENTITY_COLLECTION ========== */

    public void saveEntities(List<Entity> entities) throws InterruptedException {
        if (entities == null || entities.isEmpty()) {
            log.info("saveEntities: 没有实体需要写入");
            return;
        }
        List<JsonObject> rows = new ArrayList<>(entities.size());
        for (Entity e : entities) {
            if (e == null) continue;
            JsonObject obj = new JsonObject();

            // ENTITIES_FINAL_COLUMNS:
            // id, human_readable_id, title, type, description,
            // text_unit_ids, frequency, degree, x, y

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

        milvusUtil.insertByBatch(graphProperties.getEntityCollectionName(), rows);
        log.info("saveEntities: 已写入 {} 条实体到 {}", rows.size(), graphProperties.getEntityCollectionName());
    }

    /* ========== relationships.parquet -> RELATIONSHIP_COLLECTION ========== */

    public void saveRelationships(List<Relationship> relationships) throws InterruptedException {
        if (relationships == null || relationships.isEmpty()) {
            log.info("saveRelationships: 没有关系需要写入");
            return;
        }
        List<JsonObject> rows = new ArrayList<>(relationships.size());
        for (Relationship r : relationships) {
            if (r == null) continue;
            JsonObject obj = new JsonObject();

            // RELATIONSHIPS_FINAL_COLUMNS:
            // id, human_readable_id, source, target, description,
            // weight, combined_degree, text_unit_ids

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

        milvusUtil.insertByBatch(graphProperties.getRelationshipCollectionName(), rows);
        log.info("saveRelationships: 已写入 {} 条关系到 {}", rows.size(), graphProperties.getRelationshipCollectionName());
    }

    /* ========== communities.parquet -> COMMUNITY_COLLECTION ========== */

    public void saveCommunities(List<Community> communities) throws InterruptedException {
        if (communities == null || communities.isEmpty()) {
            log.info("saveCommunities: 没有社区需要写入");
            return;
        }
        List<JsonObject> rows = new ArrayList<>(communities.size());
        for (Community c : communities) {
            if (c == null) continue;
            JsonObject obj = new JsonObject();

            // COMMUNITIES_FINAL_COLUMNS:
            // id, human_readable_id, community, level, parent, children,
            // title, entity_ids, relationship_ids, text_unit_ids, period, size

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

            rows.add(obj);
        }

        milvusUtil.insertByBatch(graphProperties.getCommunityCollectionName(), rows);
        log.info("saveCommunities: 已写入 {} 条社区到 {}", rows.size(), graphProperties.getCommunityCollectionName());
    }

    /* ========== community_reports.parquet -> COMMUNITY_REPORT_COLLECTION ========== */

    public void saveCommunityReports(List<CommunityReport> reports) throws InterruptedException {
        if (reports == null || reports.isEmpty()) {
            log.info("saveCommunityReports: 没有社区报告需要写入");
            return;
        }
        List<JsonObject> rows = new ArrayList<>(reports.size());
        for (CommunityReport r : reports) {
            if (r == null) continue;
            JsonObject obj = new JsonObject();

            // COMMUNITY_REPORTS_FINAL_COLUMNS:
            // id, human_readable_id, community, level, parent, children,
            // title, summary, full_content, rank, rating_explanation,
            // findings, full_content_json, period, size

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

        milvusUtil.insertByBatch(graphProperties.getCommunityReportCollectionName(), rows);
        log.info("saveCommunityReports: 已写入 {} 条社区报告到 {}", rows.size(), graphProperties.getCommunityReportCollectionName());
    }

    public void saveCovariates(List<Covariate> covariates) throws InterruptedException {
        if (covariates == null || covariates.isEmpty()) {
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

            // 向量：description 优先，其次 source_text
            String embedText = !StrUtil.isBlank(c.getDescription()) ? c.getDescription() : c.getSourceText();
            safeAddEmbedding(obj, "embedding", embedText);

            rows.add(obj);
        }

        milvusUtil.insertByBatch(graphProperties.getCovariateCollectionName(), rows);
        log.info("saveCovariates: 已写入 {} 条到 {}", rows.size(), graphProperties.getCovariateCollectionName());
    }


    /* =================== 小工具方法 =================== */

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
