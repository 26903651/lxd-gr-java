package com.gdin.inspection.graphrag.search;

import com.gdin.inspection.graphrag.config.properties.MilvusProperties;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.*;
import io.milvus.v2.service.vector.request.data.BaseVector;
import io.milvus.v2.service.vector.request.data.EmbeddedText;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.GetResp;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.SearchResp;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Milvus搜索器
 */
@Component
@Slf4j
public class MilvusSearch {
    /**
     * 最大分数，凭经验
     * 小型库（1~5万文档） → maxScore=30
     * 中型库（10万级文档） → maxScore=50
     * 大型库（百万级文档） → maxScore=80
     */
    private static final double THEORETICAL_MAX_SCORE = 30.0; // 经验值, BM25可获取到的最大分值

    @Resource
    private MilvusProperties milvusProperties;

    @Resource
    private MilvusClientV2 milvusClientV2;

    @Resource
    private EmbeddingModel embeddingModel;

    private FloatVec getFloatVec(String query) {
        return new FloatVec(embeddingModel.embed(query).content().vector());
    }

    // -------------------- 构建搜索的通用方法 --------------------

    /**
     * 构建搜索请求并执行搜索
     *
     * @param filter 过滤条件字符串，用于结果过滤（可为空）
     * @param outputFields 需要返回的字段列表（可为空）
     * @param minScore 最小分数阈值，用于相似度筛选（可为空）
     * @param groupByFieldName 分组字段名称（可为空）
     * @param groupSize 分组数量（可为空）
     * @param searchParams 搜索参数映射，用于传递额外参数
     * @param searchReqBuilder 搜索请求构建器，用于构造最终请求
     * @return 搜索结果列表
     */
    private List<SearchResp.SearchResult> buildSearchReqAndSearch(String filter, List<String> outputFields, Float minScore, String groupByFieldName, Integer groupSize, Map<String, Object> searchParams, SearchReq.SearchReqBuilder searchReqBuilder) {
        // 处理过滤条件逻辑
        if(filter!=null&&!filter.isBlank()){
            // 当过滤条件长度超过32时，启用迭代过滤优化
//            if(filter.length()>32) searchParams.put("hints", "iterative_filter");
            searchReqBuilder.filter(filter);
        }

        // 设置需要返回的字段
        if(outputFields!=null&&!outputFields.isEmpty()) searchReqBuilder.outputFields(outputFields);

        // 注意: 分数设置和分组设置只能同时存在一个, 当两者冲突时, 默认只使用最低分数设置
        if(minScore!=null&&groupByFieldName!=null&&!groupByFieldName.isBlank()) groupByFieldName = null;
        // 处理最小分数阈值设置（注意：server-side range 对 BM25 无效；这里只保留向量 path 可使用）
        /*if(minScore!=null){
            searchParams.put("radius", minScore);
            searchParams.put("range_filter", Float.MAX_VALUE);
        }*/

        // 设置分组参数
        if(groupByFieldName!=null&&!groupByFieldName.isBlank()) searchReqBuilder.groupByFieldName(groupByFieldName);
        if(groupSize!=null&&groupSize!=0) searchReqBuilder.groupSize(groupSize);

        // 应用所有设置的搜索参数
        if(!searchParams.isEmpty()) searchReqBuilder.searchParams(searchParams);

        // 构建最终请求并执行搜索
        SearchReq searchReq = searchReqBuilder.build();
//        log.info("searchReq:{}", searchReq.toString());
        SearchResp searchResp = milvusClientV2.search(searchReq);
        // 单 query 的结果放在 searchResults.get(0)
        List<SearchResp.SearchResult> searchResults = searchResp.getSearchResults().get(0);
        List<SearchResp.SearchResult> out = searchResults;
        // 使用minScore进行过滤
        if(minScore!=null) {
            out = new ArrayList<>();
            for (SearchResp.SearchResult searchResult : searchResults) {
                if(minScore<=searchResult.getScore()) out.add(searchResult);
            }
        }
        return out;
    }

    // -------------------- 语义/稠密搜索 --------------------

    public List<SearchResp.SearchResult> semanticSearchByScore(String collectionName, String query, String denseFieldName) {
        return semanticSearchByScore(collectionName, query, denseFieldName, milvusProperties.getDefaultTopK());
    }

    public List<SearchResp.SearchResult> semanticSearchByScore(String collectionName, String query, String denseFieldName, int topK) {
        return semanticSearchByScore(collectionName, query, denseFieldName, topK, null);
    }

    public List<SearchResp.SearchResult> semanticSearchByScore(String collectionName, String query, String denseFieldName, int topK, List<String> outputFields) {
        return semanticSearchByScore(collectionName, query, denseFieldName, topK, null, outputFields);
    }

    public List<SearchResp.SearchResult> semanticSearchByScore(String collectionName, String query, String denseFieldName, int topK, String filter, List<String> outputFields) {
        return semanticSearchByScore(collectionName, query, denseFieldName, topK, filter, outputFields, milvusProperties.getDefaultMinScore());
    }

    /**
     * 执行语义搜索
     *
     * @param collectionName 目标集合名称（需预先创建）
     * @param query 自然语言查询文本（将自动转换为向量）
     * @param denseFieldName 稠密向量字段名称（需预先创建）
     * @param topK 每个查询请求返回的最相关结果数量
     * @param filter 结果过滤条件表达式（符合milvus语法）
     * @param outputFields 需要在结果中返回的字段列表（null表示只返回主键和相似度距离/分数）
     * @param minScore 结果最小相关性分数阈值（null表示不过滤）
     * @return 搜索结果列表
     */
    public List<SearchResp.SearchResult> semanticSearchByScore(String collectionName, String query, String denseFieldName, int topK, String filter, List<String> outputFields, Float minScore) {
        return semanticSearch(collectionName, query, denseFieldName, topK, filter, outputFields, minScore, null, null);
    }

    public List<SearchResp.SearchResult> semanticSearchByGroup(String collectionName, String query, String denseFieldName, String groupByFieldName, Integer groupSize) {
        return semanticSearchByGroup(collectionName, query, denseFieldName, milvusProperties.getDefaultTopK(), groupByFieldName, groupSize);
    }

    public List<SearchResp.SearchResult> semanticSearchByGroup(String collectionName, String query, String denseFieldName, int topK, String groupByFieldName, Integer groupSize) {
        return semanticSearchByGroup(collectionName, query, denseFieldName, topK, null, groupByFieldName, groupSize);
    }

    public List<SearchResp.SearchResult> semanticSearchByGroup(String collectionName, String query, String denseFieldName, int topK, List<String> outputFields, String groupByFieldName, Integer groupSize) {
        return semanticSearchByGroup(collectionName, query, denseFieldName, topK, null, outputFields, groupByFieldName, groupSize);
    }

    /**
     * 执行语义搜索
     *
     * @param collectionName 目标集合名称（需预先创建）
     * @param query 自然语言查询文本（将自动转换为向量）
     * @param denseFieldName 稠密向量字段名称（需预先创建）
     * @param topK 每个查询请求返回的最相关结果数量
     * @param filter 结果过滤条件表达式（符合milvus语法）
     * @param outputFields 需要在结果中返回的字段列表（null表示只返回主键和相似度距离/分数）
     * @param groupByFieldName 分组字段名称（null表示不对分组）
     * @param groupSize 分组数量（null表示当分组时每组只返回1个结果）
     * @return 搜索结果列表
     */
    public List<SearchResp.SearchResult> semanticSearchByGroup(String collectionName, String query, String denseFieldName, int topK, String filter, List<String> outputFields, String groupByFieldName, Integer groupSize) {
        return semanticSearch(collectionName, query, denseFieldName, topK, filter, outputFields, null, groupByFieldName, groupSize);
    }

    /**
     * 执行语义搜索
     *
     * @param collectionName 目标集合名称（需预先创建）
     * @param query 自然语言查询文本（将自动转换为向量）
     * @param denseFieldName 稠密向量字段名称（需预先创建）
     * @param topK 每个查询请求返回的最相关结果数量
     * @param filter 结果过滤条件表达式（符合milvus语法）
     * @param outputFields 需要在结果中返回的字段列表（null表示只返回主键和相似度距离/分数）
     * @param minScore 结果最小相关性分数阈值（null表示不过滤）
     * @param groupByFieldName 分组字段名称（null表示不对分组）
     * @param groupSize 分组数量（null表示当分组时每组只返回1个结果）
     * @return 搜索结果列表
     */
    private List<SearchResp.SearchResult> semanticSearch(String collectionName, String query, String denseFieldName, int topK, String filter, List<String> outputFields, Float minScore, String groupByFieldName, Integer groupSize) {
        // 将自然语言查询转换为高维向量表示
        FloatVec queryVector = getFloatVec(query);

        // 构建基础搜索请求：设置集合、查询向量和结果数量
        SearchReq.SearchReqBuilder searchReqBuilder = SearchReq.builder()
                .collectionName(collectionName)
                .annsField(denseFieldName)
                .data(Collections.singletonList(queryVector))
                .limit(topK);

        // 初始化搜索参数容器（当前为空，保留扩展点）
        Map<String,Object> searchParams = new HashMap<>();

        // 整合所有参数构造完整请求并执行搜索
        return buildSearchReqAndSearch(filter, outputFields, minScore, groupByFieldName, groupSize, searchParams, searchReqBuilder);
    }

    // -------------------- 关键字/稀疏搜索 --------------------

    public List<SearchResp.SearchResult> keywordSearchByScore(String collectionName, String query, String sparseFieldName) {
        return keywordSearchByScore(collectionName, query, sparseFieldName, milvusProperties.getDefaultTopK());
    }

    public List<SearchResp.SearchResult> keywordSearchByScore(String collectionName, String query, String sparseFieldName, int topK) {
        return keywordSearchByScore(collectionName, query, sparseFieldName, topK, null);
    }

    public List<SearchResp.SearchResult> keywordSearchByScore(String collectionName, String query, String sparseFieldName, int topK, List<String> outputFields) {
        return keywordSearchByScore(collectionName, query, sparseFieldName, topK, null, outputFields);
    }

    public List<SearchResp.SearchResult> keywordSearchByScore(String collectionName, String query, String sparseFieldName, int topK, String filter, List<String> outputFields) {
        return keywordSearchByScore(collectionName, query, sparseFieldName, topK, filter, outputFields, milvusProperties.getDefaultMinScore());
    }

    public List<SearchResp.SearchResult> keywordSearchByScore(String collectionName, String query, String sparseFieldName, int topK, String filter, List<String> outputFields, Float minScore) {
        return keywordSearch(collectionName, query, sparseFieldName, topK, filter, outputFields, minScore, null, null);
    }

    public List<SearchResp.SearchResult> keywordSearchByGroup(String collectionName, String query, String sparseFieldName, String groupByFieldName, Integer groupSize) {
        return keywordSearchByGroup(collectionName, query, sparseFieldName, milvusProperties.getDefaultTopK(), groupByFieldName, groupSize);
    }

    public List<SearchResp.SearchResult> keywordSearchByGroup(String collectionName, String query, String sparseFieldName, int topK, String groupByFieldName, Integer groupSize) {
        return keywordSearchByGroup(collectionName, query, sparseFieldName, topK, null, groupByFieldName, groupSize);
    }

    public List<SearchResp.SearchResult> keywordSearchByGroup(String collectionName, String query, String sparseFieldName, int topK, List<String> outputFields, String groupByFieldName, Integer groupSize) {
        return keywordSearchByGroup(collectionName, query, sparseFieldName, topK, null, outputFields, groupByFieldName, groupSize);
    }

    /**
     * 执行基于关键字的稀疏向量搜索
     *
     * @param collectionName 目标集合名称
     * @param query 搜索关键词文本
     * @param sparseFieldName 稀疏向量字段名称
     * @param topK 返回每个查询的最相关结果数量
     * @param filter 过滤条件的字符串表示
     * @param outputFields 需要返回的字段列表（null表示只返回主键和相似度距离/分数）
     * @param groupByFieldName 分组字段名称（null表示不对分组）
     * @param groupSize 分组数量（null表示当分组时每组只返回1个结果）
     * @return 搜索结果列表
     */
    public List<SearchResp.SearchResult> keywordSearchByGroup(String collectionName, String query, String sparseFieldName, int topK, String filter, List<String> outputFields, String groupByFieldName, Integer groupSize) {
        return keywordSearch(collectionName, query, sparseFieldName, topK, filter, outputFields, null, groupByFieldName, groupSize);
    }

    /**
     * 执行基于关键字的稀疏向量搜索
     *
     * @param collectionName 目标集合名称
     * @param query 搜索关键词文本
     * @param sparseFieldName 稀疏向量字段名称
     * @param topK 返回每个查询的最相关结果数量
     * @param filter 过滤条件的字符串表示
     * @param outputFields 需要返回的字段列表（null表示只返回主键和相似度距离/分数）
     * @param minScore 结果过滤的最小相关性分数阈值（null表示不过滤）
     * @param groupByFieldName 分组字段名称（null表示不对分组）
     * @param groupSize 分组数量（null表示当分组时每组只返回1个结果）
     * @return 搜索结果列表
     */
    private List<SearchResp.SearchResult> keywordSearch(String collectionName, String query, String sparseFieldName, int topK, String filter, List<String> outputFields, Float minScore, String groupByFieldName, Integer groupSize) {
        // 构建查询文本的向量表示
        EmbeddedText textVector = new EmbeddedText(query);

        // 初始化搜索参数（包含稀疏向量搜索专用参数）
        Map<String,Object> searchParams = new HashMap<>();
        searchParams.put("drop_ratio_search", 0.2);  // 设置稀疏向量搜索的丢弃比例

        // 构建搜索请求基础参数
        SearchReq.SearchReqBuilder searchReqBuilder = SearchReq.builder()
                .collectionName(collectionName)
                .data(Collections.singletonList(textVector))  // 单查询请求包装
                .annsField(sparseFieldName)
                .limit(topK)
                .searchParams(searchParams);

        // 执行最终搜索请求构建和搜索操作，得到原始结果（BM25 原始分数）
        List<SearchResp.SearchResult> raw = buildSearchReqAndSearch(filter, outputFields, null, groupByFieldName, groupSize, searchParams, searchReqBuilder);

        if (raw == null || raw.isEmpty()) return raw;

        // 抽取原始分数（可能为 null -> 视为 0）
        List<Double> rawScores = raw.stream()
                .map(r -> r.getScore() == null ? 0.0 : r.getScore().doubleValue())
                .collect(Collectors.toList());

        // 做归一化处理
        List<Double> norm = normalize(rawScores);

        // 将归一化后的分数写回原对象的 score 字段，并按 minScore（若传入）过滤（这里的 minScore 语义为 0..1）
        List<SearchResp.SearchResult> out = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            SearchResp.SearchResult sr = raw.get(i);
            float newScore = norm.size() > i ? norm.get(i).floatValue() : 0.0f;
            sr.setScore(newScore);
            if (minScore == null || newScore >= minScore) {
                out.add(sr);
            }
        }

        return out;
    }

    // -------------------- 混合搜索 --------------------

    public List<SearchResp.SearchResult> hybridSearch(String collectionName, String query, String denseFieldName, String sparseFieldName) {
        return hybridSearch(collectionName, query, denseFieldName, sparseFieldName, milvusProperties.getDefaultTopK());
    }

    public List<SearchResp.SearchResult> hybridSearch(String collectionName, String query, String denseFieldName, String sparseFieldName, int topK) {
        return hybridSearch(collectionName, query, denseFieldName, sparseFieldName, topK, null);
    }

    public List<SearchResp.SearchResult> hybridSearch(String collectionName, String query, String denseFieldName, String sparseFieldName, int topK, List<String> outputFields) {
        return hybridSearch(collectionName, query, denseFieldName, sparseFieldName, topK, null, outputFields);
    }

    public List<SearchResp.SearchResult> hybridSearch(String collectionName, String query, String denseFieldName, String sparseFieldName, int topK, String filter, List<String> outputFields) {
        return hybridSearch(collectionName, query, denseFieldName, query, sparseFieldName, topK, filter, outputFields);
    }

    public List<SearchResp.SearchResult> hybridSearch(String collectionName, String denseQuery, String denseFieldName, String sparseQuery, String sparseFieldName, int topK, String filter, List<String> outputFields) {
        return hybridSearch(collectionName, denseQuery, denseFieldName, sparseQuery, sparseFieldName, topK, milvusProperties.getDefaultDenseWeight(), milvusProperties.getDefaultSparseWeight(), filter, outputFields, null);
    }

    /**
     * 混合搜索（稠密检索+稀疏检索结合）
     *
     * @param collectionName     要搜索的集合名称
     * @param denseQuery        稠密向量查询文本（将被转换为浮点型向量）
     * @param denseFieldName   稠密向量字段名称
     * @param sparseQuery      稀疏向量查询文本（将被转换为BM25向量）
     * @param sparseFieldName  稀疏向量字段名称
     * @param topK             每个子搜索返回的topK结果数量
     * @param denseWeight      稠密检索结果的权重
     * @param sparseWeight     稀疏检索结果的权重
     * @param filter           可选的过滤表达式（用于预过滤文档）
     * @param outputFields     可选的输出字段列表（null表示只返回主键和相似度距离/分数）
     * @param minScore         可选的最小相关性分数阈值（null表示不过滤）
     * @return                 搜索结果列表
     */
    public List<SearchResp.SearchResult> hybridSearch(String collectionName, String denseQuery, String denseFieldName, String sparseQuery, String sparseFieldName, int topK, float denseWeight, float sparseWeight, String filter, List<String> outputFields, Float minScore) {
        return hybridSearch(collectionName, denseQuery, denseFieldName, sparseQuery, sparseFieldName, topK, denseWeight, sparseWeight, filter, outputFields, minScore, null, null);
    }

    /**
     * 混合搜索（稠密检索+稀疏检索结合）
     *
     * @param collectionName     要搜索的集合名称
     * @param denseQuery        稠密向量查询文本（将被转换为浮点型向量）
     * @param denseFieldName   稠密向量字段名称
     * @param sparseQuery      稀疏向量查询文本（将被转换为BM25向量）
     * @param sparseFieldName  稀疏向量字段名称
     * @param topK             每个子搜索返回的topK结果数量
     * @param denseWeight      稠密检索结果的权重
     * @param sparseWeight     稀疏检索结果的权重
     * @param filter           可选的过滤表达式（用于预过滤文档）
     * @param outputFields     需要返回的字段列表（null表示只返回主键和相似度距离/分数）
     * @param minScore         最小相关性分数阈值（低于此分数将被过滤）, 此参数暂时无效
     * @param groupByFieldName 分组字段名称（null表示不对分组）, 此参数暂时无效
     * @param groupSize         分组数量（null表示当分组时每组只返回1个结果）, 此参数暂时无效
     * @return                 搜索结果列表
     */
    private List<SearchResp.SearchResult> hybridSearch(String collectionName, String denseQuery, String denseFieldName, String sparseQuery, String sparseFieldName, int topK, float denseWeight, float sparseWeight, String filter, List<String> outputFields, Float minScore, String groupByFieldName, Integer groupSize) {
        // 1) 分别构建 dense 与 sparse 的单路 SearchReq
        FloatVec denseVec = getFloatVec(denseQuery);
        EmbeddedText sparseVec = new EmbeddedText(sparseQuery);

        // dense builder
        SearchReq.SearchReqBuilder denseBuilder = SearchReq.builder()
                .collectionName(collectionName)
                .annsField(denseFieldName)
                .data(Collections.singletonList((BaseVector) denseVec))
                .limit(topK);
        // sparse builder
        Map<String,Object> sparseParams = new HashMap<>();
        sparseParams.put("drop_ratio_search", 0.2);
        SearchReq.SearchReqBuilder sparseBuilder = SearchReq.builder()
                .collectionName(collectionName)
                .annsField(sparseFieldName)
                .data(Collections.singletonList((BaseVector) sparseVec))
                .limit(topK)
                .searchParams(sparseParams);

        // 2) 应用可选 filter / outputFields（group/minScore 服务端对 BM25 无效，故不在这里处理）
        if (filter != null && !filter.isBlank()) {
            if (filter.length() > 32) {
                Map<String,Object> tmp = new HashMap<>();
                tmp.put("hints", "iterative_filter");
                denseBuilder.searchParams(tmp);
            }
            denseBuilder.filter(filter);
            sparseBuilder.filter(filter);
        }
        if (outputFields != null && !outputFields.isEmpty()) {
            denseBuilder.outputFields(outputFields);
            sparseBuilder.outputFields(outputFields);
        }

        // 3) 下发两路搜索（顺序调用，生产可改成并发）
        List<SearchResp.SearchResult> denseRaw = buildSearchReqAndSearch(filter, outputFields, null, null, null, new HashMap<>(), denseBuilder);
        List<SearchResp.SearchResult> sparseRaw = buildSearchReqAndSearch(filter, outputFields, null, null, null, sparseParams, sparseBuilder);

        if ((denseRaw == null || denseRaw.isEmpty()) && (sparseRaw == null || sparseRaw.isEmpty())) {
            return Collections.emptyList();
        }

        // 4) 抽取 id/object/entity/score 信息（用 idString = String.valueOf(id) 做 key）
        // dense
        Map<String, Object> denseIdToObj = new HashMap<>();
        Map<String, Map<String,Object>> denseIdToEntity = new HashMap<>();
        Map<String, Double> denseIdToRawScore = new LinkedHashMap<>(); // preserve order
        if (denseRaw != null) {
            for (SearchResp.SearchResult sr : denseRaw) {
                String idStr = String.valueOf(sr.getId());
                denseIdToObj.put(idStr, sr.getId());
                denseIdToEntity.put(idStr, sr.getEntity() == null ? Collections.emptyMap() : sr.getEntity());
                denseIdToRawScore.put(idStr, sr.getScore() == null ? 0.0 : sr.getScore().doubleValue());
            }
        }

        // sparse
        Map<String, Object> sparseIdToObj = new HashMap<>();
        Map<String, Map<String,Object>> sparseIdToEntity = new HashMap<>();
        Map<String, Double> sparseIdToRawScore = new LinkedHashMap<>();
        if (sparseRaw != null) {
            for (SearchResp.SearchResult sr : sparseRaw) {
                String idStr = String.valueOf(sr.getId());
                sparseIdToObj.put(idStr, sr.getId());
                sparseIdToEntity.put(idStr, sr.getEntity() == null ? Collections.emptyMap() : sr.getEntity());
                sparseIdToRawScore.put(idStr, sr.getScore() == null ? 0.0 : sr.getScore().doubleValue());
            }
        }

        // 5) 对全文检索的原始分数归一化处理（注意：若某一路为空则跳过）
        List<Double> denseRawScoresList = new ArrayList<>(denseIdToRawScore.values());
        List<Double> sparseRawScoresList = new ArrayList<>(sparseIdToRawScore.values());
        // List<Double> denseNormList = denseRawScoresList;
        List<Double> sparseNormList = normalize(sparseRawScoresList);

        // 把归一后的分数放回 map (按原 map 的 key 顺序)
        Map<String, Double> denseNormMap = new LinkedHashMap<>();
        {
            Iterator<String> it = denseIdToRawScore.keySet().iterator();
            int idx = 0;
            while (it.hasNext()) {
                String id = it.next();
                double v = (denseRawScoresList.size() > idx) ? denseRawScoresList.get(idx) : 0.0;
                denseNormMap.put(id, v);
                idx++;
            }
        }
        Map<String, Double> sparseNormMap = new LinkedHashMap<>();
        {
            Iterator<String> it = sparseIdToRawScore.keySet().iterator();
            int idx = 0;
            while (it.hasNext()) {
                String id = it.next();
                double v = (sparseNormList.size() > idx) ? sparseNormList.get(idx) : 0.0;
                sparseNormMap.put(id, v);
                idx++;
            }
        }

        // 6) 合并：union ids, 对未命中的一路赋 0 分，再按权重加权求 final score（0..1）
        Set<String> allIds = new LinkedHashSet<>();
        allIds.addAll(denseNormMap.keySet());
        allIds.addAll(sparseNormMap.keySet());

        double sumW = denseWeight + sparseWeight;
        if (sumW == 0) { denseWeight = 1; sparseWeight = 1; sumW = 2; }

        List<Map.Entry<String, Double>> mergedList = new ArrayList<>();
        for (String id : allIds) {
            double dn = denseNormMap.getOrDefault(id, 0.0);
            double sn = sparseNormMap.getOrDefault(id, 0.0);
            double finalScore = (denseWeight * dn + sparseWeight * sn) / sumW;
            mergedList.add(new AbstractMap.SimpleEntry<>(id, finalScore));
        }
        // 按 finalScore 降序
        mergedList.sort((a,b) -> Double.compare(b.getValue(), a.getValue()));

        // 7) 取 topK 并构造返回的 SearchResult 列表（优先使用 dense 的 entity 否则用 sparse）
        List<SearchResp.SearchResult> out = new ArrayList<>();
        int cnt = Math.min(topK, mergedList.size());
        for (int i = 0; i < cnt; i++) {
            String idStr = mergedList.get(i).getKey();
            double finalScore = mergedList.get(i).getValue();
            Object originalId = denseIdToObj.containsKey(idStr) ? denseIdToObj.get(idStr) : sparseIdToObj.get(idStr);
            Map<String,Object> entity = denseIdToEntity.containsKey(idStr) ? denseIdToEntity.get(idStr) : sparseIdToEntity.getOrDefault(idStr, Collections.emptyMap());
            // 如果 minScore 存在且 finalScore 小于阈值，则跳过
            if (minScore != null && finalScore < minScore) continue;
            // 构造 SearchResult（使用 builder）
            SearchResp.SearchResult sr = SearchResp.SearchResult.builder()
                    .entity(entity)
                    .score((float) finalScore)
                    .id(originalId)
                    .primaryKey("id")
                    .build();
            out.add(sr);
        }
        return out;
    }

    // -------------------- 其余方法 --------------------

    /**
     * 根据指定ID列表从Milvus集合中查询数据
     *
     * @param collectionName 要查询的集合名称（大小写敏感）
     * @param ids            需要查询的数据ID列表
     * @param outputFields   需要返回的字段名称列表
     * @return 包含查询结果的列表，每个结果对应一个ID的查询数据
     *         当ID不存在时会返回null元素，需调用方处理空值情况
     */
    public List<QueryResp.QueryResult> get(String collectionName, List<Object> ids, List<String> outputFields) {
        // 构建查询请求对象
        GetReq getReq = GetReq.builder()
                .collectionName(collectionName)
                .ids(ids)
                .outputFields(outputFields)
                .build();

        // 执行Milvus客户端查询并获取原始响应
        GetResp getResp = milvusClientV2.get(getReq);

        // 返回结构化后的查询结果列表
        return getResp.getGetResults();
    }

    /**
     * 根据指定的过滤条件从Milvus集合中查询数据
     *
     * @param collectionName 要查询的集合名称（大小写敏感）
     * @param filter         过滤条件
     * @param outputFields   需要返回的字段名称列表
     * @param limit           查询条数
     * @return 包含查询结果的列表
     */
    public List<QueryResp.QueryResult> query(String collectionName, String filter, List<String> outputFields, Integer limit) {
        // 构建查询请求对象
        QueryReq.QueryReqBuilder queryReqBuilder = QueryReq.builder()
                .collectionName(collectionName)
                .filter(filter)
                .outputFields(outputFields);
        if(limit!=null) queryReqBuilder.limit(limit);
        QueryReq queryReq = queryReqBuilder.build();

        // 执行Milvus客户端查询并获取原始响应
        QueryResp queryResp = milvusClientV2.query(queryReq);

        // 返回结构化后的查询结果列表
        return queryResp.getQueryResults();
    }

    // -------------------- 工具：按经验进行归一化 --------------------

    private List<Double> normalize(List<Double> scores) {
        if (scores == null || scores.isEmpty()) return Collections.emptyList();
        double min = 0.0;
        return scores.stream().map(s -> (s - min) / (THEORETICAL_MAX_SCORE - min)).collect(Collectors.toList());
    }
}
