package com.gdin.inspection.graphrag.v2.index.opertation.context;

import com.gdin.inspection.graphrag.util.SpringBootUtil;
import com.gdin.inspection.graphrag.v2.index.opertation.util.CsvUtil;
import com.gdin.inspection.graphrag.v2.util.TokenUtil;

import java.util.*;

/**
 * 对齐 Python graphrag/index/operations/summarize_communities/graph_context/sort_context.py
 *
 * 默认（strictPythonBehavior=true）：
 * - 只在遍历 edges 的过程中逐步加入 nodes/claims/edges
 * - edges 为空时不会加入任何 node_details，因此可能返回空字符串（除非有 subCommunityReports）
 *
 * 可选增强（strictPythonBehavior=false）：
 * - edges 为空时，用 node_details 作为 Entities 做 fallback，并按 maxContextTokens 增量截断。
 */
public final class SortContext {

    private SortContext() {}

    // 对齐 schemas.py
    public static final String TITLE = "title";
    public static final String SHORT_ID = "human_readable_id";
    public static final String NODE_DETAILS = "node_details";
    public static final String EDGE_DETAILS = "edge_details";
    public static final String CLAIM_DETAILS = "claim_details";
    public static final String EDGE_SOURCE = "source";
    public static final String EDGE_TARGET = "target";
    public static final String EDGE_DEGREE = "combined_degree";
    public static final String FULL_CONTENT = "full_content";
    public static final String COMMUNITY_ID = "community";

    public static String sortContext(
            List<Map<String, Object>> localContext,
            List<Map<String, Object>> subCommunityReports,
            Integer maxContextTokens
    ) {
        if (localContext == null) localContext = List.of();

        // flatten edges
        List<Map<String, Object>> edges = new ArrayList<>();
        for (Map<String, Object> record : localContext) {
            Object ed = record.get(EDGE_DETAILS);
            if (ed instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) {
                        edges.add(castMap(m));
                    }
                }
            }
        }

        // node_details map: title -> node_details(dict)
        Map<String, Map<String, Object>> nodeDetailsByTitle = new LinkedHashMap<>();
        for (Map<String, Object> record : localContext) {
            Object titleObj = record.get(TITLE);
            String title = titleObj == null ? "" : String.valueOf(titleObj);
            Object nd = record.get(NODE_DETAILS);
            if (nd instanceof Map<?, ?> m) {
                Map<String, Object> node = castMap(m);
                // Python: schemas.SHORT_ID cast to int
                if (node.get(SHORT_ID) != null) node.put(SHORT_ID, toInt(node.get(SHORT_ID)));
                nodeDetailsByTitle.put(title, node);
            }
        }

        // claim_details: title -> list[dict]
        Map<String, List<Map<String, Object>>> claimDetailsByTitle = new LinkedHashMap<>();
        for (Map<String, Object> record : localContext) {
            Object titleObj = record.get(TITLE);
            String title = titleObj == null ? "" : String.valueOf(titleObj);

            Object cd = record.get(CLAIM_DETAILS);
            if (cd instanceof List<?> list) {
                List<Map<String, Object>> claims = new ArrayList<>();
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) {
                        Map<String, Object> c = castMap(m);
                        if (c.get(SHORT_ID) != null) {
                            c.put(SHORT_ID, toInt(c.get(SHORT_ID)));
                            claims.add(c);
                        }
                    }
                }
                if (!claims.isEmpty()) claimDetailsByTitle.put(title, claims);
            }
        }

        // sort edges by (-combined_degree, short_id asc)
        edges.sort((a, b) -> {
            double da = toDouble(a.get(EDGE_DEGREE));
            double db = toDouble(b.get(EDGE_DEGREE));
            int cmp = Double.compare(db, da);
            if (cmp != 0) return cmp;
            int ia = toInt(a.get(SHORT_ID));
            int ib = toInt(b.get(SHORT_ID));
            return Integer.compare(ia, ib);
        });

        // ======= 关键：edges 为空时的行为 =======
        // Python 严格模式：不走循环，最终只会输出 sub reports（如果有），否则空字符串。
        // 非严格模式：用 node_details 做 Entities fallback，避免空上下文。
        boolean strictPythonBehavior = false;
        if (edges.isEmpty() && !strictPythonBehavior) {
            return fallbackWhenNoEdges(nodeDetailsByTitle, claimDetailsByTitle, subCommunityReports, maxContextTokens);
        }

        Set<Integer> edgeIds = new HashSet<>();
        Set<Integer> nodeIds = new HashSet<>();
        Set<Integer> claimIds = new HashSet<>();

        List<Map<String, Object>> sortedEdges = new ArrayList<>();
        List<Map<String, Object>> sortedNodes = new ArrayList<>();
        List<Map<String, Object>> sortedClaims = new ArrayList<>();

        String contextString = "";

        for (Map<String, Object> edge : edges) {
            String source = String.valueOf(edge.get(EDGE_SOURCE));
            String target = String.valueOf(edge.get(EDGE_TARGET));

            // add nodes
            for (String nodeTitle : List.of(source, target)) {
                Map<String, Object> node = nodeDetailsByTitle.get(nodeTitle);
                if (node != null) {
                    int nid = toInt(node.get(SHORT_ID));
                    if (nodeIds.add(nid)) {
                        sortedNodes.add(node);
                    }
                }
            }

            // add claims
            for (String nodeTitle : List.of(source, target)) {
                List<Map<String, Object>> claims = claimDetailsByTitle.get(nodeTitle);
                if (claims != null) {
                    for (Map<String, Object> c : claims) {
                        int cid = toInt(c.get(SHORT_ID));
                        if (claimIds.add(cid)) {
                            sortedClaims.add(c);
                        }
                    }
                }
            }

            // add edge
            int eid = toInt(edge.get(SHORT_ID));
            if (edgeIds.add(eid)) {
                sortedEdges.add(edge);
            }

            String newContext = getContextString(sortedNodes, sortedEdges, sortedClaims, subCommunityReports);
            TokenUtil tokenUtil = SpringBootUtil.getBean(TokenUtil.class);
            if (maxContextTokens != null && tokenUtil.getTokenCount(newContext) > maxContextTokens) {
                break;
            }
            contextString = newContext;
        }

        if (!contextString.isEmpty()) return contextString;
        return getContextString(sortedNodes, sortedEdges, sortedClaims, subCommunityReports);
    }

    /**
     * edges 为空时的 fallback（非 strict 模式才会走）
     * - 用 node_details 作为 Entities
     * - （可选）claims 也可以输出（这里也一起处理了）
     * - 按 maxContextTokens 增量截断，避免超限
     */
    private static String fallbackWhenNoEdges(
            Map<String, Map<String, Object>> nodeDetailsByTitle,
            Map<String, List<Map<String, Object>>> claimDetailsByTitle,
            List<Map<String, Object>> subCommunityReports,
            Integer maxContextTokens
    ) {
        // 1) entities：按 degree desc、short_id asc 排序（更像“重要的先喂”）
        List<Map<String, Object>> entities = new ArrayList<>(nodeDetailsByTitle.values());
        entities.sort((a, b) -> {
            int da = toInt(a.get("degree"));
            int db = toInt(b.get("degree"));
            int cmp = Integer.compare(db, da);
            if (cmp != 0) return cmp;
            return Integer.compare(toInt(a.get(SHORT_ID)), toInt(b.get(SHORT_ID)));
        });

        // 2) claims：拉平成一个 list，并按 short_id asc（不强求）
        List<Map<String, Object>> claims = new ArrayList<>();
        for (List<Map<String, Object>> list : claimDetailsByTitle.values()) {
            if (list != null) claims.addAll(list);
        }
        claims.sort(Comparator.comparingInt(m -> toInt(m.get(SHORT_ID))));

        // 3) 如果不设 token 上限，直接输出全量
        if (maxContextTokens == null) {
            return getContextString(entities, List.of(), claims, subCommunityReports);
        }

        // 4) 有 token 上限：增量构建
        TokenUtil tokenUtil = SpringBootUtil.getBean(TokenUtil.class);

        List<Map<String, Object>> pickedEntities = new ArrayList<>();
        List<Map<String, Object>> pickedClaims = new ArrayList<>();

        String best = getContextString(pickedEntities, List.of(), pickedClaims, subCommunityReports);
        if (tokenUtil.getTokenCount(best) > maxContextTokens) {
            // 连 reports 都塞不下，那只能返回空（和 Python 风格一致：不硬塞）
            return "";
        }

        // 先塞 entities
        for (Map<String, Object> e : entities) {
            pickedEntities.add(e);
            String cur = getContextString(pickedEntities, List.of(), pickedClaims, subCommunityReports);
            if (tokenUtil.getTokenCount(cur) > maxContextTokens) {
                pickedEntities.remove(pickedEntities.size() - 1);
                break;
            }
            best = cur;
        }

        // 再塞 claims（如果你不想要 claims fallback，把这段删掉即可）
        for (Map<String, Object> c : claims) {
            pickedClaims.add(c);
            String cur = getContextString(pickedEntities, List.of(), pickedClaims, subCommunityReports);
            if (tokenUtil.getTokenCount(cur) > maxContextTokens) {
                pickedClaims.remove(pickedClaims.size() - 1);
                break;
            }
            best = cur;
        }

        return best;
    }

    private static String getContextString(
            List<Map<String, Object>> entities,
            List<Map<String, Object>> edges,
            List<Map<String, Object>> claims,
            List<Map<String, Object>> subCommunityReports
    ) {
        List<String> parts = new ArrayList<>();

        if (subCommunityReports != null && !subCommunityReports.isEmpty()) {
            String csv = CsvUtil.toCsv(subCommunityReports);
            if (!csv.isBlank()) {
                parts.add("----Reports-----\n" + csv);
            }
        }

        for (var item : List.of(
                Map.entry("Entities", entities),
                Map.entry("Claims", claims),
                Map.entry("Relationships", edges)
        )) {
            if (item.getValue() != null && !item.getValue().isEmpty()) {
                String csv = CsvUtil.toCsv(item.getValue());
                if (!csv.isBlank()) {
                    parts.add("-----" + item.getKey() + "-----\n" + csv);
                }
            }
        }

        return String.join("\n\n", parts);
    }

    private static Map<String, Object> castMap(Map<?, ?> m) {
        Map<String, Object> r = new LinkedHashMap<>();
        for (var e : m.entrySet()) r.put(String.valueOf(e.getKey()), e.getValue());
        return r;
    }

    private static int toInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return 0; }
    }

    private static double toDouble(Object v) {
        if (v == null) return 0d;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(v)); } catch (Exception e) { return 0d; }
    }
}
