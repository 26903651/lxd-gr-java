package com.gdin.inspection.graphrag.v2.storage;

import cn.hutool.core.collection.CollectionUtil;
import com.gdin.inspection.graphrag.config.properties.GraphProperties;
import com.gdin.inspection.graphrag.req.milvus.MilvusQueryReq;
import com.gdin.inspection.graphrag.req.milvus.MilvusUpsertReq;
import com.gdin.inspection.graphrag.service.MilvusSearchService;
import com.gdin.inspection.graphrag.service.MilvusUpsertService;
import com.gdin.inspection.graphrag.util.MilvusUtil;
import com.gdin.inspection.graphrag.v2.models.TextUnit;
import com.google.gson.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class KnowledgeSliceWriteBackService {

    @Resource
    private GraphProperties graphProperties;

    @Resource
    private MilvusSearchService milvusSearchService;

    @Resource
    private MilvusUtil milvusUtil;

    @Resource
    private MilvusUpsertService milvusUpsertService;

    /**
     * 把 finalTextUnits 生成的 entity_ids / relationship_ids / covariate_ids / human_readable_id 写回知识库切片 metadata。
     *
     * 注意：这里假设 TextUnit.id 就是知识库切片的主键 id（强烈建议你在 LoadInputDocumentsWorkflow 查询时把 outputFields 加上 "id" 并用它赋值 TextUnit.id）
     */
    public void writeBackToKnowledgeBase(int scope, List<TextUnit> finalTextUnits) {
        if (CollectionUtil.isEmpty(finalTextUnits)) return;

        // 1) 建 docId -> finalTextUnit 映射
        Map<String, TextUnit> docIdToFinal = new LinkedHashMap<>();
        List<String> docIds = new ArrayList<>(finalTextUnits.size());
        for (TextUnit tu : finalTextUnits) {
            if (tu == null || tu.getId() == null) continue;
            docIdToFinal.put(tu.getId(), tu);
            docIds.add(tu.getId());
        }

        // 2) 分批 query 原 extra
        JsonArray arr = new JsonArray();
        List<List<String>> docIdBatches = new ArrayList<>();
        for (int i = 0; i < docIds.size(); i += 200) {
            docIdBatches.add(docIds.subList(i, Math.min(i + 200, docIds.size())));
        }
        for (List<String> docIdBatch : docIdBatches) {
            StringBuilder filter = new StringBuilder();
            filter.append("metadata[\"doc_id\"] in [");
            for (String docId : docIdBatch) {
                filter.append("\"").append(docId).append("\"").append(",");
            }
            filter.deleteCharAt(filter.length() - 1);
            filter.append("]");
            String queryJson = milvusSearchService.query(MilvusQueryReq.builder()
                    .collectionName(graphProperties.getCollectionNames().getMain().getContentCollectionName())
                    .filter(filter.toString())
                    .outputFields(List.of("id", "metadata", "extra", "graph_main", "graph_document_ids", "graph_entity_ids", "graph_relationship_ids", "graph_covariate_ids"))
                    .build());
            arr.addAll(JsonParser.parseString(queryJson).getAsJsonArray());
        }

        // 3) 组装 upsert 数据
        List<JsonObject> upserts = new ArrayList<>(arr.size());
        for (JsonElement el : arr) {
            JsonObject row = el.getAsJsonObject();
            String docId = row.getAsJsonObject("metadata").getAsJsonPrimitive("doc_id").getAsString();
            TextUnit finalTu = docIdToFinal.get(docId);
            if (finalTu == null) continue;

            JsonObject extra = row.has("extra") ? row.getAsJsonObject("extra") : new JsonObject();
            extra.addProperty("graph", scope);

            JsonObject graphMain = row.has("graph_main") ? row.getAsJsonObject("graph_main") : new JsonObject();
            graphMain.addProperty("human_readable_id", finalTu.getHumanReadableId() == null ? -1 : finalTu.getHumanReadableId());
            graphMain.addProperty("n_tokens", finalTu.getNTokens());

            Long id = row.getAsJsonPrimitive("id").getAsLong();
            JsonObject upsertRow = new JsonObject();
            upsertRow.addProperty("id", id);
            upsertRow.add("extra", extra);
            upsertRow.add("graph_main", graphMain);
            upsertRow.add("graph_document_ids", toJsonArray(finalTu.getDocumentIds()));
            upsertRow.add("graph_entity_ids", toJsonArray(finalTu.getEntityIds()));
            upsertRow.add("graph_relationship_ids", toJsonArray(finalTu.getRelationshipIds()));
            upsertRow.add("graph_covariate_ids", toJsonArray(finalTu.getCovariateIds()));
            upserts.add(upsertRow);
        }

        if (upserts.isEmpty()) {
            log.warn("writeBackToKnowledgeBase: upserts 为空，跳过");
            return;
        }

        // 4) upsert 回去
        for (JsonObject upsertRow : upserts) {
            milvusUpsertService.updateEntity(MilvusUpsertReq.builder()
                    .collectionName(graphProperties.getCollectionNames().getMain().getContentCollectionName())
                    .id(upsertRow.getAsJsonPrimitive("id").getAsLong())
                    .valueMap((Map)upsertRow.asMap())
                    .build());
        }

        log.info("writeBackToKnowledgeBase: 已写回 {} 条切片 metadata 到 {}", upserts.size(), graphProperties.getCollectionNames().getMain().getContentCollectionName());
    }

    private JsonArray toJsonArray(List<String> list) {
        JsonArray arr = new JsonArray();
        if (list == null) return arr;
        for (String s : list) {
            if (s == null) continue;
            arr.add(s);
        }
        return arr;
    }
}
