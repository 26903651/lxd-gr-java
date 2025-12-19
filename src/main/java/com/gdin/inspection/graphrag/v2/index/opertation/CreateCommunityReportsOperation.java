package com.gdin.inspection.graphrag.v2.index.opertation;

import cn.hutool.core.collection.CollectionUtil;
import com.gdin.inspection.graphrag.v2.index.opertation.context.CommunityContextRow;
import com.gdin.inspection.graphrag.v2.index.opertation.context.LevelContextBuilder;
import com.gdin.inspection.graphrag.v2.index.opertation.context.LocalContextBuilder;
import com.gdin.inspection.graphrag.v2.index.strategy.CommunityReportsStrategy;
import com.gdin.inspection.graphrag.v2.models.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class CreateCommunityReportsOperation {

    private final SummarizeCommunitiesOperation summarizeOp;
    private final FinalizeCommunityReportsOperation finalizeOp;

    /**
     * 对齐 Python create_community_reports（编排层）：
     * - explode_communities
     * - _prep_nodes/_prep_edges/_prep_claims
     * - build_local_context
     * - summarize_communities（strictPythonMode 决定对齐 bug 行为 or 修复）
     * - finalize_community_reports
     *
     * 备注：默认值你已找到，这里先按你要求“写死/由调用方传入”即可。
     */
    public List<CommunityReport> createCommunityReports(
            List<Entity> entities,
            List<Relationship> relationships,
            List<Community> communities,
            List<Covariate> claimsOrNull,
            CommunityReportsStrategy strategy
    ) {
        if (CollectionUtil.isEmpty(entities)) throw new IllegalArgumentException("entities 不能为空");
        if (CollectionUtil.isEmpty(communities)) throw new IllegalArgumentException("communities 不能为空");
        if (relationships == null) relationships = List.of();

        // 1) explode_communities + _prep_nodes（Java：直接产出 LocalContextBuilder.NodeRec）
        List<LocalContextBuilder.NodeRec> nodes = explodeCommunities(communities, entities);

        // 2) _prep_edges
        List<LocalContextBuilder.EdgeRec> edges = relationships.stream()
                .filter(Objects::nonNull)
                .map(r -> new LocalContextBuilder.EdgeRec(
                        r.getHumanReadableId(),
                        r.getSource(),
                        r.getTarget(),
                        r.getDescription(),
                        r.getCombinedDegree()
                ))
                .toList();

        // 3) _prep_claims（可选）
        List<LocalContextBuilder.ClaimRec> claims = null;
        if (claimsOrNull != null) {
            claims = claimsOrNull.stream()
                    .filter(Objects::nonNull)
                    .map(c -> new LocalContextBuilder.ClaimRec(
                            c.getHumanReadableId(),
                            c.getSubjectId(),
                            c.getType(),
                            c.getStatus(),
                            c.getDescription()
                    ))
                    .toList();
        }

        // 4) build_local_context
        List<CommunityContextRow> localContexts = LocalContextBuilder.buildLocalContexts(
                nodes, edges, claims, strategy.getMaxContextTokens()
        );

        // 5) community_hierarchy = communities.explode(children)
        List<LevelContextBuilder.CommunityHierarchyRow> hierarchy = buildHierarchy(communities);

        // 6) summarize_communities（严格对齐 or 修复）
        List<FinalizeCommunityReportsOperation.RawReportRow> rawRows = summarizeOp.summarize(
                localContexts,
                hierarchy,
                strategy
        );

        // 7) finalize_community_reports（补齐 parent/children/period/size + 生成 uuid + human_readable_id=community）
        return finalizeOp.finalizeReports(rawRows, communities);
    }

    private List<LocalContextBuilder.NodeRec> explodeCommunities(List<Community> communities, List<Entity> entities) {
        Map<String, Entity> entityById = entities.stream()
                .filter(Objects::nonNull)
                .filter(e -> e.getId() != null)
                .collect(Collectors.toMap(Entity::getId, e -> e, (a, b) -> a, LinkedHashMap::new));

        List<LocalContextBuilder.NodeRec> nodes = new ArrayList<>();

        for (Community c : communities) {
            if (c == null) continue;

            Integer communityId = c.getCommunity();
            Integer level = c.getLevel();
            List<String> entityIds = c.getEntityIds();

            if (communityId == null || level == null || entityIds == null) continue;

            for (String eid : entityIds) {
                Entity e = entityById.get(eid);
                if (e == null) continue;

                String desc = (e.getDescription() == null || e.getDescription().isBlank())
                        ? "No Description"
                        : e.getDescription();

                nodes.add(new LocalContextBuilder.NodeRec(
                        communityId,
                        level,
                        e.getDegree(),
                        e.getHumanReadableId(),
                        e.getTitle(),
                        desc
                ));
            }
        }

        // Python explode_communities: return nodes.loc[nodes.loc[:, COMMUNITY_ID] != -1]
        return nodes.stream()
                .filter(n -> n.getCommunity() != null && n.getCommunity() != -1)
                .toList();
    }

    private List<LevelContextBuilder.CommunityHierarchyRow> buildHierarchy(List<Community> communities) {
        List<LevelContextBuilder.CommunityHierarchyRow> rows = new ArrayList<>();
        for (Community c : communities) {
            if (c == null) continue;

            Integer communityId = c.getCommunity();
            Integer level = c.getLevel();
            List<Integer> children = c.getChildren();

            if (communityId == null || level == null || children == null) continue;

            for (Integer child : children) {
                if (child == null) continue;
                rows.add(new LevelContextBuilder.CommunityHierarchyRow(communityId, level, child));
            }
        }
        return rows;
    }
}
