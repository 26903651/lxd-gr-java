package com.gdin.inspection.graphrag.v2.query.context;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.gdin.inspection.graphrag.config.properties.GraphProperties;
import com.gdin.inspection.graphrag.req.milvus.MilvusSemanticSearchReq;
import com.gdin.inspection.graphrag.service.MilvusSearchService;
import com.gdin.inspection.graphrag.v2.util.CsvUtil;
import com.gdin.inspection.graphrag.v2.util.TokenUtil;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class BasicSearchContext {

    @Resource
    private GraphProperties graphProperties;

    @Resource
    private MilvusSearchService milvusSearchService;

    @Resource
    private TokenUtil tokenUtil;

    /**
     * 对齐 Python build_context 的参数默认值
     */
    public ContextBuilderResult buildContext(
            String query,
            Integer k,
            Integer maxContextTokens,
            String contextName,
            String columnDelimiter,
            String textIdCol,
            String textCol,
            String filter
    ) {
        int topK = (k == null ? 10 : k);
        int maxTokens = (maxContextTokens == null ? 12_000 : maxContextTokens);
        String ctxName = (contextName == null ? "Sources" : contextName);
        String delim = (columnDelimiter == null ? "|" : columnDelimiter);
        String idCol = (textIdCol == null ? "source_id" : textIdCol);
        String tCol = (textCol == null ? "text" : textCol);

        // query == "" => 空表（对齐 Python）
        List<Row> relatedRows = new ArrayList<>();
        if (query != null && !query.isEmpty()) {
            String collectionName = graphProperties.getCollectionNames().getMain().getContentCollectionName();

            // 只拿 basic search 必需字段：metadata/doc_id, graph_main/human_readable_id, page_content
            List<String> outputFields = List.of("metadata", "graph_main", "page_content");

            String finalFilter = filter==null ? "extra[\"graph\"]==1" : "extra[\"graph\"]==1 and " + filter;
            String json = milvusSearchService.semantic(MilvusSemanticSearchReq.builder()
                    .collectionName(collectionName)
                    .query(query)
                    .filter(finalFilter)
                    .topK(topK)
                    .outputFields(outputFields)
                    .build());

            JSONArray results = JSON.parseArray(json);
            for (int i = 0; i < results.size(); i++) {
                JSONObject result = results.getJSONObject(i);
                JSONObject entity = result.getJSONObject("entity");
                if (entity == null) continue;

                JSONObject graphMain = entity.getJSONObject("graph_main");
                if (graphMain == null) {
                    // 对齐 Python：缺 mapping 会 KeyError，这里直接 fail-fast
                    throw new IllegalStateException("Missing graph_main in content collection entity");
                }
                Integer humanReadableId = graphMain.getInteger("human_readable_id");
                if (humanReadableId == null || humanReadableId < 0) {
                    throw new IllegalStateException("Missing or invalid graph_main.human_readable_id for basic search context");
                }

                String pageContent = entity.getString("page_content");
                if (pageContent == null) pageContent = "";

                relatedRows.add(new Row(String.valueOf(humanReadableId), pageContent));
            }
        }

        // token 截断逻辑对齐 Python：
        // current_tokens = num_tokens(headerLine)
        int currentTokens = tokenUtil.getTokenCount(idCol + delim + tCol + "\n");

        List<Row> finalRows = new ArrayList<>();
        for (Row r : relatedRows) {
            String line = r.sourceId + delim + r.text + "\n";
            int tokens = tokenUtil.getTokenCount(line);

            if (currentTokens + tokens > maxTokens) {
                log.warn("Reached token limit: {}. Reverting to previous context state", (currentTokens + tokens));
                break;
            }
            currentTokens += tokens;
            finalRows.add(r);
        }

        // 组装“DataFrame”等价结构
        List<String> columns = List.of(idCol, tCol);
        List<List<String>> rows = new ArrayList<>(finalRows.size());
        for (Row r : finalRows) {
            rows.add(List.of(r.sourceId, r.text));
        }
        TableRecords table = new TableRecords(columns, rows);

        // 组装 rows（保证列顺序固定）
        List<Map<String, Object>> csvRows = new ArrayList<>(rows.size());
        for (Row r : finalRows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put(idCol, r.sourceId);
            m.put(tCol, r.text);
            csvRows.add(m);
        }

        // 对齐 Python: sep="|", escapechar="\\"
        String csv = CsvUtil.toCsv(
                csvRows,
                delim,
                '\\',
                false,
                List.of(idCol, tCol)
        );

        Map<String, TableRecords> records = new LinkedHashMap<>();
        records.put(ctxName, table);

        return new ContextBuilderResult(csv, records, 0, 0, 0);
    }

    private record Row(String sourceId, String text) {}
}

