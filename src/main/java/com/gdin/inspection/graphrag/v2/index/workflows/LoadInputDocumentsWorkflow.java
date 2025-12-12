package com.gdin.inspection.graphrag.v2.index.workflows;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.gdin.inspection.graphrag.req.milvus.MilvusQueryReq;
import com.gdin.inspection.graphrag.service.MilvusSearchService;
import com.gdin.inspection.graphrag.v2.models.TextUnit;
import com.gdin.inspection.graphrag.v2.util.TokenUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

/**
 * LoadInputDocumentsWorkflow: 负责把 Document 切分成 TextUnit 并持久化到 TextUnitRepository。
 *
 * 语义和 Python 版本一致：这里只做切分与保存，不分配 human_readable_id（由 finalize 负责）。
 */
@Service
public class LoadInputDocumentsWorkflow {
    @Resource
    private MilvusSearchService milvusSearchService;

    @Resource
    private TokenUtil tokenUtil;

    /**
     * 批量处理文档：将每个 document 的切片生成对应的 TextUnit。
     * @param documentIds 待处理的文档列表
     * @return 生成的 TextUnit 列表（按文档顺序）
     */
    public List<TextUnit> run(Collection<String> documentIds) throws IOException {
        // 从向量库获取切片
        StringBuilder filter = new StringBuilder();
        filter.append("metadata[\"segment_type\"]==\"father\" and metadata[\"document_id\"] in [");
        for (String documentId : documentIds) {
            filter.append("\"").append(documentId).append("\"").append(",");
        }
        filter.deleteCharAt(filter.length() - 1);
        filter.append("]");
        String queryResult = milvusSearchService.query(MilvusQueryReq.builder()
                .collectionName("AP_KNOWLEDGE_CONTENT_DEV")
                .filter(filter.toString())
                .outputFields(List.of("metadata", "page_content"))
                .build());
        JSONArray slices = JSON.parseArray(queryResult);
        // 按照document, page进行排序
        slices.sort((o1, o2) -> {
            JSONObject metadata1 = ((JSONObject) o1).getJSONObject("metadata");
            JSONObject metadata2 = ((JSONObject) o2).getJSONObject("metadata");
            String documentId1 = metadata1.getString("document_id");
            String documentId2 = metadata2.getString("document_id");
            if(Objects.equals(documentId1, documentId2)) {
                Integer page1 = metadata1.getInteger("page");
                Integer page2 = metadata2.getInteger("page");
                return page1.compareTo(page2);
            }
            return documentId1.compareTo(documentId2);
        });
        List<TextUnit> textUnits = new ArrayList<>();
        for (int i = 0; i < slices.size(); i++) {
            JSONObject slice = slices.getJSONObject(i);
            JSONObject metadata = slice.getJSONObject("metadata");
            String documentId = metadata.getString("document_id");
            String pageContent = slice.getString("page_content");
            String docId = metadata.getString("doc_id");
            textUnits.add(TextUnit.builder()
                    .id(docId)
                    .humanReadableId(null)  // 按 Python 行为，finalize 阶段再统一赋值
                    .text(pageContent)
                    .nTokens(tokenUtil.getTokenCount(pageContent))
                    .documentIds(List.of(documentId))  // 目前每个 text unit 只属于一个 document
                    .entityIds(null)                  // 这三个字段后面 finalize 再填
                    .relationshipIds(null)
                    .covariateIds(null)
                    .build());
        }
        return textUnits;
    }
}
