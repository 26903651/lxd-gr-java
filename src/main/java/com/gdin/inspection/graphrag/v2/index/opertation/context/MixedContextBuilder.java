package com.gdin.inspection.graphrag.v2.index.opertation.context;

import cn.hutool.core.collection.CollectionUtil;
import com.gdin.inspection.graphrag.util.SpringBootUtil;
import com.gdin.inspection.graphrag.v2.util.CsvUtil;
import com.gdin.inspection.graphrag.v2.util.TokenUtil;

import java.util.*;

public final class MixedContextBuilder {
    private MixedContextBuilder() {}

    /**
     * 对齐 Python build_mixed_context：
     * - 优先用子社区 report 替换大的 local_context，直到 token <= max
     * - 如果怎么替换都超，退化成只拼 reports CSV，能放多少放多少
     */
    public static String buildMixedContext(
            List<SubCommunityContext> contexts,
            int maxContextTokens
    ) {
        if (CollectionUtil.isEmpty(contexts)) return "";
        TokenUtil tokenUtil = SpringBootUtil.getBean(TokenUtil.class);

        List<SubCommunityContext> sorted = new ArrayList<>(contexts);
        sorted.sort((a, b) -> Integer.compare(
                b.getContextSize() == null ? 0 : b.getContextSize(),
                a.getContextSize() == null ? 0 : a.getContextSize()
        ));

        List<Map<String, Object>> substituteReports = new ArrayList<>();
        List<Map<String, Object>> finalLocalContexts = new ArrayList<>();
        boolean exceeded = true;
        String contextString = "";

        for (int idx = 0; idx < sorted.size(); idx++) {
            SubCommunityContext sc = sorted.get(idx);

            if (exceeded) {
                if (sc.getFullContent() != null && !sc.getFullContent().isBlank()) {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put(SortContext.COMMUNITY_ID, sc.getSubCommunity());
                    r.put(SortContext.FULL_CONTENT, sc.getFullContent());
                    substituteReports.add(r);
                } else {
                    // 该子社区没有 report，用它的 local_context
                    if (sc.getAllContext() != null) finalLocalContexts.addAll(sc.getAllContext());
                    continue;
                }

                // 剩余子社区仍用 local_context
                List<Map<String, Object>> remainingLocal = new ArrayList<>();
                for (int rid = idx + 1; rid < sorted.size(); rid++) {
                    if (sorted.get(rid).getAllContext() != null) {
                        remainingLocal.addAll(sorted.get(rid).getAllContext());
                    }
                }

                String newContext = SortContext.sortContext(
                        remainingLocalPlus(remainingLocal, finalLocalContexts),
                        substituteReports,
                        maxContextTokens
                );

                if (tokenUtil.getTokenCount(newContext) <= maxContextTokens) {
                    exceeded = false;
                    contextString = newContext;
                    break;
                }
            }
        }

        if (exceeded) {
            // 退化：只拼 reports CSV，能放多少放多少
            substituteReports = new ArrayList<>();
            for (SubCommunityContext sc : sorted) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put(SortContext.COMMUNITY_ID, sc.getSubCommunity());
                r.put(SortContext.FULL_CONTENT, sc.getFullContent());
                substituteReports.add(r);

                String csv = CsvUtil.toCsv(substituteReports);
                if (tokenUtil.getTokenCount(csv) > maxContextTokens) break;
                contextString = csv;
            }
        }

        return contextString;
    }

    private static List<Map<String, Object>> remainingLocalPlus(
            List<Map<String, Object>> a,
            List<Map<String, Object>> b
    ) {
        List<Map<String, Object>> r = new ArrayList<>(a);
        r.addAll(b);
        return r;
    }
}
