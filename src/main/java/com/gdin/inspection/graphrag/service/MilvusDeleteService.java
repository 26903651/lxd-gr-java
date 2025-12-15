package com.gdin.inspection.graphrag.service;

import com.gdin.inspection.graphrag.req.milvus.MilvusDeleteReq;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.DeleteReq;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MilvusDeleteService {

    @Resource
    private MilvusClientV2 milvusClientV2;
    public void deleteDocument(MilvusDeleteReq milvusDeleteReq) {
        DeleteReq deleteReq = DeleteReq.builder()
                .collectionName(milvusDeleteReq.getCollectionName())
                .filter("metadata[\"document_id\"] == \"" + milvusDeleteReq.getDocumentId() + "\"")
                .build();
        milvusClientV2.delete(deleteReq);
    }

    public void delete(String collectionName, List<Object> ids) {
        DeleteReq deleteReq = DeleteReq.builder()
                .collectionName(collectionName)
                .ids(ids)
                .build();
        milvusClientV2.delete(deleteReq);
    }

    public void deleteByFilter(String collectionName, String filter) {
        DeleteReq deleteReq = DeleteReq.builder()
                .collectionName(collectionName)
                .filter(filter)
                .build();
        milvusClientV2.delete(deleteReq);
    }

    public void deleteAll(String collectionName) {
        deleteByFilter(collectionName, "id != \"\"");
    }
}
