package com.gdin.inspection.graphrag.util;

import com.google.gson.JsonObject;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.response.InsertResp;
import io.milvus.v2.service.vector.response.QueryResp;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class MilvusUtil {
    @Resource
    private MilvusClientV2 milvusClientV2;

    public InsertResp insertByBatch(String collectionName, List<JsonObject> datas) throws InterruptedException {
        // 将datas拆成1000个一批来进行插入
        int batchSize = 1000;
        for (int i = 0; i < datas.size(); i += batchSize) {
            List<JsonObject> subList = datas.subList(i, Math.min(i + batchSize, datas.size()));
            InsertReq insertReq = InsertReq.builder()
                    .collectionName(collectionName)
                    .data(subList)
                    .build();
            InsertResp insertResp = milvusClientV2.insert(insertReq);
            ensureInserted(collectionName, insertResp);
            return insertResp;
        }
        return InsertResp.builder().InsertCnt(0L).build();
    }

    /**
     * 插入后大概300-400毫秒左右可以查询到, 所以每500毫秒循环查询一次
     * @param collectionName
     * @param insertResp
     */
    private void ensureInserted(String collectionName, InsertResp insertResp) throws InterruptedException {
        List<Object> primaryKeys = insertResp.getPrimaryKeys();
        Object next = primaryKeys.iterator().next();
        String filter = "id == ";
        if(next instanceof String id) filter += "\"" + id + "\"";
        else filter += next.toString();
        Long id = (Long) primaryKeys.iterator().next();
        QueryReq queryReq = QueryReq.builder()
                .collectionName(collectionName)
                .filter(filter)
                .outputFields(Collections.singletonList("id"))
                .build();
        boolean insertDone = false;
        int count = 0;
        int maxCount = 20;
        while(!insertDone&&count<maxCount){
            Thread.sleep(500);
            QueryResp queryResp = milvusClientV2.query(queryReq);
            var results = queryResp.getQueryResults();
            insertDone = !results.isEmpty();
            ++count;
        }
        if(!insertDone) throw new RuntimeException("Milvus insert check failed");
    }
}
