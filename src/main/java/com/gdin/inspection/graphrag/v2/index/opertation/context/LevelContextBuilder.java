package com.gdin.inspection.graphrag.v2.index.opertation.context;

import com.gdin.inspection.graphrag.util.SpringBootUtil;
import com.gdin.inspection.graphrag.v2.util.TokenUtil;
import lombok.Value;

import java.util.*;
import java.util.stream.Collectors;

public final class LevelContextBuilder {
    private LevelContextBuilder() {}

    @Value
    public static class CommunityHierarchyRow {
        Integer community;      // parent community id
        Integer level;          // parent level
        Integer subCommunity;   // child community id
    }

    @Value
    public static class ReportRow {
        Integer community;
        Integer level;
        String fullContent;  // schemas.FULL_CONTENT
    }

    public static List<CommunityContextRow> buildLevelContext(
            List<ReportRow> reportsSoFar,
            List<CommunityHierarchyRow> hierarchy,
            List<CommunityContextRow> localContexts,
            int level,
            int maxContextTokens
    ) {
        if (localContexts == null) localContexts = List.of();

        List<CommunityContextRow> levelLocal = localContexts.stream()
                .filter(r -> Objects.equals(r.getLevel(), level))
                .toList();

        List<CommunityContextRow> valid = levelLocal.stream()
                .filter(r -> r.getContextExceedLimit() == null || !r.getContextExceedLimit())
                .toList();

        List<CommunityContextRow> invalid = levelLocal.stream()
                .filter(r -> r.getContextExceedLimit() != null && r.getContextExceedLimit())
                .toList();

        if (invalid.isEmpty()) return valid;

        // 没有任何 report：就按 Python 分支 trim local context
        if (reportsSoFar == null || reportsSoFar.isEmpty()) {
            List<CommunityContextRow> trimmed = invalid.stream()
                    .map(r -> trimLocal(r, maxContextTokens))
                    .toList();
            return union(valid, trimmed);
        }

        // antijoin_reports：如果某个 community 已经有 report，不再重复生成
        Set<Integer> alreadyReported = reportsSoFar.stream()
                .map(ReportRow::getCommunity)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<CommunityContextRow> needBuild = levelLocal.stream()
                .filter(r -> !alreadyReported.contains(r.getCommunity()))
                .toList();

        // 针对 invalid 尝试用 sub-community reports 替换
        Map<Integer, List<SubCommunityContext>> subContextsByParent = buildSubContextsByParent(
                reportsSoFar, hierarchy, localContexts, level + 1
        );

        List<CommunityContextRow> substituted = new ArrayList<>();
        List<CommunityContextRow> remaining = new ArrayList<>();

        TokenUtil tokenUtil = SpringBootUtil.getBean(TokenUtil.class);
        for (CommunityContextRow inv : invalid) {
            List<SubCommunityContext> subs = subContextsByParent.get(inv.getCommunity());
            if (subs == null || subs.isEmpty()) {
                remaining.add(inv);
                continue;
            }

            String ctx = MixedContextBuilder.buildMixedContext(subs, maxContextTokens);
            int size = tokenUtil.getTokenCount(ctx);

            // MixedContextBuilder 内部已经尽量收敛到 <= max，但保险起见再兜底一次
            if (size > maxContextTokens) {
                remaining.add(inv);
                continue;
            }

            substituted.add(CommunityContextRow.builder()
                    .community(inv.getCommunity())
                    .level(level)
                    .allContext(inv.getAllContext())
                    .contextString(ctx)
                    .contextSize(size)
                    .contextExceedLimit(false)
                    .build());
        }

        // remaining：按 Python 兜底 trim local
        List<CommunityContextRow> trimmedRemaining = remaining.stream()
                .map(r -> trimLocal(r, maxContextTokens))
                .toList();

        // 对齐 Python：valid + substituted + remaining
        return union(
                valid.stream().filter(r -> needBuild.stream().anyMatch(n -> Objects.equals(n.getCommunity(), r.getCommunity()))).toList(),
                substituted,
                trimmedRemaining
        );
    }

    private static CommunityContextRow trimLocal(CommunityContextRow r, int maxContextTokens) {
        String trimmed = SortContext.sortContext(r.getAllContext(), null, maxContextTokens);
        TokenUtil tokenUtil = SpringBootUtil.getBean(TokenUtil.class);
        int size = tokenUtil.getTokenCount(trimmed);
        return CommunityContextRow.builder()
                .community(r.getCommunity())
                .level(r.getLevel())
                .allContext(r.getAllContext())
                .contextString(trimmed)
                .contextSize(size)
                .contextExceedLimit(false)
                .build();
    }

    private static Map<Integer, List<SubCommunityContext>> buildSubContextsByParent(
            List<ReportRow> reports,
            List<CommunityHierarchyRow> hierarchy,
            List<CommunityContextRow> localContexts,
            int subLevel
    ) {
        // sub_context_df：subCommunity(level=subLevel) 的 local_context + report(full_content)
        Map<Integer, CommunityContextRow> subLocalByCommunity = localContexts.stream()
                .filter(r -> Objects.equals(r.getLevel(), subLevel))
                .collect(Collectors.toMap(
                        CommunityContextRow::getCommunity,
                        x -> x,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        Map<Integer, ReportRow> reportByCommunity = reports.stream()
                .collect(Collectors.toMap(
                        ReportRow::getCommunity,
                        x -> x,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        Map<Integer, List<SubCommunityContext>> byParent = new LinkedHashMap<>();

        for (CommunityHierarchyRow h : hierarchy) {
            if (!Objects.equals(h.getLevel(), subLevel - 1)) continue;
            Integer parent = h.getCommunity();
            Integer sub = h.getSubCommunity();
            if (parent == null || sub == null) continue;

            CommunityContextRow subLocal = subLocalByCommunity.get(sub);
            if (subLocal == null) continue;

            ReportRow rr = reportByCommunity.get(sub);
            String full = rr == null ? "" : (rr.getFullContent() == null ? "" : rr.getFullContent());

            SubCommunityContext sc = SubCommunityContext.builder()
                    .subCommunity(sub)
                    .allContext(subLocal.getAllContext())
                    .fullContent(full)
                    .contextSize(subLocal.getContextSize())
                    .build();

            byParent.computeIfAbsent(parent, _k -> new ArrayList<>()).add(sc);
        }

        return byParent;
    }

    @SafeVarargs
    private static List<CommunityContextRow> union(List<CommunityContextRow>... parts) {
        List<CommunityContextRow> r = new ArrayList<>();
        for (List<CommunityContextRow> p : parts) if (p != null) r.addAll(p);
        // pandas union 这里不强制去重，按 Python 的拼接语义即可
        return r;
    }
}
