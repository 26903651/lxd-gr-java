package com.gdin.inspection.graphrag.v2.index.opertation;

import cn.hutool.core.collection.CollectionUtil;
import com.gdin.inspection.graphrag.util.IOUtil;
import com.gdin.inspection.graphrag.v2.index.opertation.context.CommunityContextRow;
import com.gdin.inspection.graphrag.v2.index.opertation.context.LevelContextBuilder;
import com.gdin.inspection.graphrag.v2.index.opertation.extract.CommunityReportsExtractor;
import com.gdin.inspection.graphrag.v2.index.strategy.CommunityReportsStrategy;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class SummarizeCommunitiesOperation {

    @Resource
    private CommunityReportsExtractor extractor;

    /**
     * 对齐 Python 的 derive_from_rows 并发语义：同一 level 内并发生成。
     *
     * strictPythonMode=true：
     *   严格模拟 Python 当前 summarize_communities 的“bug 行为”
     *   ——即 buildLevelContext 时 reportsSoFar 始终视为“空”，导致永远不会使用子社区 report 做替换。
     *
     * strictPythonMode=false：
     *   修复版 —— 每一层都用最新 reportsSoFar 再 buildLevelContext，允许用子社区 report 替换父社区超长 context。
     */
    public List<FinalizeCommunityReportsOperation.RawReportRow> summarize(
            List<CommunityContextRow> localContexts,
            List<LevelContextBuilder.CommunityHierarchyRow> hierarchy,
            CommunityReportsStrategy strategy
    ) {

        if (CollectionUtil.isEmpty(localContexts)) return List.of();

        // 这里用“倒序”(从大 level 到小 level)更贴近 GraphRAG 常见 bottom-up
        // strict 模式下因为 reports 永远为空，顺序不会影响替换，但仍建议保持一致。
        List<Integer> levels = localContexts.stream()
                .map(CommunityContextRow::getLevel)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();

        List<LevelContextBuilder.ReportRow> reportsSoFar = new ArrayList<>();
        List<FinalizeCommunityReportsOperation.RawReportRow> rawRows = new ArrayList<>();

        boolean strictPythonMode = false;   // 这里python似乎实现是有bug的, 留个开关, 控制是否严格对齐, 默认当前为修复
        for (Integer level : levels) {
            List<LevelContextBuilder.ReportRow> reportsView =
                    strictPythonMode ? List.of() : new ArrayList<>(reportsSoFar);

            List<CommunityContextRow> levelContext = LevelContextBuilder.buildLevelContext(
                    reportsView,
                    hierarchy,
                    localContexts,
                    level,
                    strategy.getMaxContextTokens()
            );

            if (levelContext.isEmpty()) continue;

            log.info("summarize communities: level={}, size={}, strictPythonMode={}",
                    level, levelContext.size(), strictPythonMode);

            List<FinalizeCommunityReportsOperation.RawReportRow> levelRows =
                    generateLevelReports(levelContext, level, strategy);

            rawRows.addAll(levelRows);

            if (!strictPythonMode) {
                for (FinalizeCommunityReportsOperation.RawReportRow r : levelRows) {
                    reportsSoFar.add(new LevelContextBuilder.ReportRow(
                            r.getCommunity(),
                            r.getLevel(),
                            r.getFullContent() == null ? "" : r.getFullContent()
                    ));
                }
            }
        }

        return rawRows;
    }

    private List<FinalizeCommunityReportsOperation.RawReportRow> generateLevelReports(
            List<CommunityContextRow> levelContext,
            int level,
            CommunityReportsStrategy strategy
    ) {
        int threads = Math.max(1, strategy.getConcurrentRequests());
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        try {
            List<CompletableFuture<FinalizeCommunityReportsOperation.RawReportRow>> futures = new ArrayList<>();

            for (CommunityContextRow row : levelContext) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        Integer communityIdObj = row.getCommunity();
                        if (communityIdObj == null) return null;

                        int communityId = communityIdObj;
                        String ctx = row.getContextString() == null ? "" : row.getContextString();

                        // ✅ maxReportLength 在这里真正传入
                        CommunityReportsResult r = extractor.generate(ctx, strategy.getMaxReportLength());
                        if (r == null || r.getStructuredOutput() == null) return null;

                        CommunityReportResponse s = r.getStructuredOutput();

                        String findingsJson = IOUtil.jsonSerializeWithNoType(s.getFindings());
                        String fullContentJson = IOUtil.jsonSerializeWithNoType(s, true);

                        return new FinalizeCommunityReportsOperation.RawReportRow(
                                communityId,
                                level,
                                s.getTitle(),
                                s.getSummary(),
                                r.getOutput(),
                                s.getRating(),
                                s.getRatingExplanation(),
                                findingsJson,
                                fullContentJson
                        );
                    } catch (Exception e) {
                        // 对齐 Python：单条失败当 None，不影响其他条
                        log.warn("community report failed: level={}, community={}", level, row.getCommunity(), e);
                        return null;
                    }
                }, pool));
            }

            List<FinalizeCommunityReportsOperation.RawReportRow> out = new ArrayList<>();
            for (CompletableFuture<FinalizeCommunityReportsOperation.RawReportRow> f : futures) {
                FinalizeCommunityReportsOperation.RawReportRow r = f.join();
                if (r != null) out.add(r);
            }
            return out;

        } finally {
            pool.shutdown();
        }
    }
}
