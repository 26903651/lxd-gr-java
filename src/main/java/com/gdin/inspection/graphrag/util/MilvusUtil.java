package com.gdin.inspection.graphrag.util;

import cn.hutool.core.collection.CollectionUtil;
import com.google.gson.JsonObject;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.response.InsertResp;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.UpsertResp;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class MilvusUtil {
    @Resource
    private MilvusClientV2 milvusClientV2;

    public InsertResp insertByBatch(String collectionName, List<JsonObject> datas) throws InterruptedException {
        if (CollectionUtil.isEmpty(datas)) return InsertResp.builder().InsertCnt(0L).build();

        int batchSize = 1000;
        InsertResp lastResp = null;

        for (int i = 0; i < datas.size(); i += batchSize) {
            List<JsonObject> subList = datas.subList(i, Math.min(i + batchSize, datas.size()));
            InsertReq insertReq = InsertReq.builder()
                    .collectionName(collectionName)
                    .data(subList)
                    .build();

            InsertResp insertResp = milvusClientV2.insert(insertReq);
            ensureInserted(collectionName, insertResp); // 只要验证本批里任意一个 pk 可查到即可
            lastResp = insertResp;
        }
        return lastResp == null ? InsertResp.builder().InsertCnt(0L).build() : lastResp;
    }

    private void ensureInserted(String collectionName, InsertResp insertResp) throws InterruptedException {
        List<Object> primaryKeys = insertResp.getPrimaryKeys();
        if (CollectionUtil.isEmpty(primaryKeys)) return;

        Object pk = primaryKeys.iterator().next();
        String filter = (pk instanceof String s)
                ? "id == \"" + s + "\""
                : "id == " + pk;

        QueryReq queryReq = QueryReq.builder()
                .collectionName(collectionName)
                .filter(filter)
                .outputFields(Collections.singletonList("id"))
                .build();

        boolean ok = false;
        for (int i = 0; i < 20 && !ok; i++) {
            Thread.sleep(500);
            QueryResp queryResp = milvusClientV2.query(queryReq);
            ok = queryResp != null && queryResp.getQueryResults() != null && !queryResp.getQueryResults().isEmpty();
        }
        if (!ok) throw new RuntimeException("Milvus insert check failed, filter=" + filter);
    }

    public UpsertResp upsertByBatch(String collectionName, List<JsonObject> datas) {
        if (CollectionUtil.isEmpty(datas)) return UpsertResp.builder().upsertCnt(0L).build();

        int batchSize = 1000;
        UpsertResp lastResp = null;

        for (int i = 0; i < datas.size(); i += batchSize) {
            List<JsonObject> subList = datas.subList(i, Math.min(i + batchSize, datas.size()));

            lastResp = milvusClientV2.upsert(UpsertReq.builder()
                    .collectionName(collectionName)
                    .data(subList)
                    .build());
        }
        return lastResp == null ? UpsertResp.builder().upsertCnt(0L).build() : lastResp;
    }
}
