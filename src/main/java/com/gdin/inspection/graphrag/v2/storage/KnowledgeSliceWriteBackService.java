package com.gdin.inspection.graphrag.v2.storage;

import com.gdin.inspection.graphrag.config.properties.GraphProperties;
import com.gdin.inspection.graphrag.req.milvus.MilvusQueryReq;
import com.gdin.inspection.graphrag.service.MilvusSearchService;
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

    private static final Gson gson = new GsonBuilder().create();

    @Resource
    private GraphProperties graphProperties;

    @Resource
    private MilvusSearchService milvusSearchService;

    @Resource
    private MilvusUtil milvusUtil;

    /**
     * 把 finalTextUnits 生成的 entity_ids / relationship_ids / covariate_ids / human_readable_id 写回知识库切片 metadata。
     *
     * 注意：这里假设 TextUnit.id 就是知识库切片的主键 id（强烈建议你在 LoadInputDocumentsWorkflow 查询时把 outputFields 加上 "id" 并用它赋值 TextUnit.id）
     */
    public void writeBackToKnowledgeBase(List<TextUnit> finalTextUnits) {
        if (finalTextUnits == null || finalTextUnits.isEmpty()) return;

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
            for (String docId : docIds) {
                filter.append("\"").append(docId).append("\"").append(",");
            }
            filter.deleteCharAt(filter.length() - 1);
            filter.append("]");
            String queryJson = milvusSearchService.query(MilvusQueryReq.builder()
                    .collectionName(graphProperties.getContentCollectionName())
                    .filter(filter.toString())
                    .outputFields(List.of("id", "metadata", "extra"))
                    .build());
            arr.add(JsonParser.parseString(queryJson).getAsJsonArray());
        }

        // 3) 组装 upsert 数据
        List<JsonObject> upserts = new ArrayList<>(arr.size());
        for (JsonElement el : arr) {
            JsonObject row = el.getAsJsonObject();
            String docId = row.getAsJsonObject("metadata").getAsJsonPrimitive("doc_id").getAsString();
            TextUnit finalTu = docIdToFinal.get(docId);
            if (finalTu == null) continue;

            JsonObject extra = row.has("extra") && row.get("extra").isJsonObject()
                    ? row.getAsJsonObject("extra")
                    : new JsonObject();

            // 覆盖/补充字段：对齐 Python TEXT_UNITS_FINAL_COLUMNS 的核心字段
            extra.addProperty("human_readable_id", finalTu.getHumanReadableId() == null ? -1 : finalTu.getHumanReadableId());
            extra.add("entity_ids", toJsonArray(finalTu.getEntityIds()));
            extra.add("relationship_ids", toJsonArray(finalTu.getRelationshipIds()));
            extra.add("covariate_ids", toJsonArray(finalTu.getCovariateIds()));

            Long id = row.getAsJsonPrimitive("id").getAsLong();
            JsonObject upsertRow = new JsonObject();
            upsertRow.addProperty("id", id);
            upsertRow.add("extra", extra);
            upserts.add(upsertRow);
        }

        if (upserts.isEmpty()) {
            log.warn("writeBackToKnowledgeBase: upserts 为空，跳过");
            return;
        }

        // 4) upsert 回去
        milvusUtil.upsertByBatch(graphProperties.getContentCollectionName(), upserts);

        log.info("writeBackToKnowledgeBase: 已写回 {} 条切片 metadata 到 {}", upserts.size(), graphProperties.getContentCollectionName());
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
