package com.gdin.inspection.graphrag.v2.index.opertation.context;

import com.gdin.inspection.graphrag.util.SpringBootUtil;
import com.gdin.inspection.graphrag.v2.util.TokenUtil;
import lombok.Value;

import java.util.*;
import java.util.stream.Collectors;

public final class LocalContextBuilder {
    private LocalContextBuilder() {}

    @Value
    public static class NodeRec {
        Integer community;
        Integer level;
        Integer degree;
        Integer humanReadableId;
        String title;
        String description;
    }

    @Value
    public static class EdgeRec {
        Integer humanReadableId;
        String source;
        String target;
        String description;
        Double combinedDegree;
    }

    @Value
    public static class ClaimRec {
        Integer humanReadableId;
        String subjectId;     // schemas.CLAIM_SUBJECT == "subject_id"
        String type;
        String status;
        String description;
    }

    public static List<CommunityContextRow> buildLocalContexts(
            List<NodeRec> nodes,
            List<EdgeRec> edges,
            List<ClaimRec> claimsOrNull,
            int maxContextTokens
    ) {
        if (nodes == null) nodes = List.of();
        if (edges == null) edges = List.of();

        // levels：建议按 level 降序，和后续 bottom-up 保持一致
        List<Integer> levels = nodes.stream()
                .map(NodeRec::getLevel)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();

        List<CommunityContextRow> result = new ArrayList<>();

        for (Integer level : levels) {
            List<NodeRec> levelNodes = nodes.stream()
                    .filter(n -> Objects.equals(n.getLevel(), level))
                    .toList();

            Set<String> nodesSet = levelNodes.stream()
                    .map(NodeRec::getTitle)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            List<EdgeRec> levelEdges = edges.stream()
                    .filter(e -> nodesSet.contains(e.getSource()) && nodesSet.contains(e.getTarget()))
                    .toList();

            List<ClaimRec> levelClaims = claimsOrNull == null ? List.of()
                    : claimsOrNull.stream()
                    .filter(c -> nodesSet.contains(c.getSubjectId()))
                    .toList();

            // source_edges: groupby(source).first
            Map<String, Map<String, Object>> firstEdgeBySource = new LinkedHashMap<>();
            for (EdgeRec e : levelEdges) {
                if (!firstEdgeBySource.containsKey(e.getSource())) {
                    firstEdgeBySource.put(e.getSource(), toEdgeDetails(e));
                }
            }

            // target_edges: groupby(target).first
            Map<String, Map<String, Object>> firstEdgeByTarget = new LinkedHashMap<>();
            for (EdgeRec e : levelEdges) {
                if (!firstEdgeByTarget.containsKey(e.getTarget())) {
                    firstEdgeByTarget.put(e.getTarget(), toEdgeDetails(e));
                }
            }

            // claim_details: title -> list[claim_details]
            Map<String, List<Map<String, Object>>> claimDetailsByTitle = new LinkedHashMap<>();
            if (claimsOrNull != null) {
                for (ClaimRec c : levelClaims) {
                    claimDetailsByTitle
                            .computeIfAbsent(c.getSubjectId(), _k -> new ArrayList<>())
                            .add(toClaimDetails(c));
                }
            }

            // merged_node_df + ALL_CONTEXT
            // Python 最终每个 node 的 EDGE_DETAILS 是 list(x.dropna())，但 x 只有一条 dict
            // 所以这里也严格只给最多 1 条 edge_details dict
            List<Map<String, Object>> nodeAllContextRows = new ArrayList<>();
            for (NodeRec n : levelNodes) {
                Map<String, Object> nodeDetails = toNodeDetails(n);

                Map<String, Object> edgePick = firstEdgeBySource.getOrDefault(n.getTitle(), null);
                if (edgePick == null) edgePick = firstEdgeByTarget.getOrDefault(n.getTitle(), null);

                List<Map<String, Object>> edgeDetailsList = edgePick == null ? List.of() : List.of(edgePick);

                Map<String, Object> all = new LinkedHashMap<>();
                all.put(SortContext.TITLE, n.getTitle());
                all.put(NODE_DEGREE, n.getDegree());
                all.put(SortContext.NODE_DETAILS, nodeDetails);
                all.put(SortContext.EDGE_DETAILS, edgeDetailsList);

                if (claimsOrNull != null) {
                    // Python merge 后 CLAIM_DETAILS 可能是 NaN 或 list；这里用 list 或不放
                    List<Map<String, Object>> cds = claimDetailsByTitle.get(n.getTitle());
                    if (cds != null) all.put(SortContext.CLAIM_DETAILS, cds);
                }

                nodeAllContextRows.add(all);
            }

            // group by community -> list[all_context]
            Map<Integer, List<Map<String, Object>>> byCommunity = new LinkedHashMap<>();
            for (int i = 0; i < levelNodes.size(); i++) {
                NodeRec n = levelNodes.get(i);
                Integer cid = n.getCommunity();
                if (cid == null) continue;
                byCommunity.computeIfAbsent(cid, _k -> new ArrayList<>()).add(nodeAllContextRows.get(i));
            }

            TokenUtil tokenUtil = SpringBootUtil.getBean(TokenUtil.class);
            for (Map.Entry<Integer, List<Map<String, Object>>> e : byCommunity.entrySet()) {
                Integer cid = e.getKey();
                List<Map<String, Object>> allContext = e.getValue();

                String contextString = SortContext.sortContext(allContext, null, maxContextTokens);
                int size = tokenUtil.getTokenCount(contextString);
                boolean exceed = size > maxContextTokens;

                result.add(CommunityContextRow.builder()
                        .community(cid)
                        .level(level)
                        .allContext(allContext)
                        .contextString(contextString)
                        .contextSize(size)
                        .contextExceedLimit(exceed)
                        .build());
            }
        }

        return result;
    }

    // ====== details builders（对齐 _prep_nodes/_prep_edges/_prep_claims）======

    private static Map<String, Object> toNodeDetails(NodeRec n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(SortContext.SHORT_ID, n.getHumanReadableId());
        m.put(SortContext.TITLE, n.getTitle());
        m.put(DESCRIPTION, (n.getDescription() == null || n.getDescription().isBlank()) ? "No Description" : n.getDescription());
        m.put(NODE_DEGREE, n.getDegree());
        return m;
    }

    private static Map<String, Object> toEdgeDetails(EdgeRec e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(SortContext.SHORT_ID, e.getHumanReadableId());
        m.put(SortContext.EDGE_SOURCE, e.getSource());
        m.put(SortContext.EDGE_TARGET, e.getTarget());
        m.put(DESCRIPTION, (e.getDescription() == null || e.getDescription().isBlank()) ? "No Description" : e.getDescription());
        m.put(SortContext.EDGE_DEGREE, e.getCombinedDegree());
        return m;
    }

    private static Map<String, Object> toClaimDetails(ClaimRec c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(SortContext.SHORT_ID, c.getHumanReadableId());
        m.put(CLAIM_SUBJECT, c.getSubjectId());
        m.put(TYPE, c.getType());
        m.put(CLAIM_STATUS, c.getStatus());
        m.put(DESCRIPTION, (c.getDescription() == null || c.getDescription().isBlank()) ? "No Description" : c.getDescription());
        return m;
    }

    // schemas 常量（和 Python schemas.py 一致的字段名）
    private static final String DESCRIPTION = "description";
    private static final String NODE_DEGREE = "degree";
    private static final String CLAIM_SUBJECT = "subject_id";
    private static final String CLAIM_STATUS = "status";
    private static final String TYPE = "type";
}
