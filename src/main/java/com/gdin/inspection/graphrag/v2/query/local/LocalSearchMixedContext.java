package com.gdin.inspection.graphrag.v2.query.local;

import com.gdin.inspection.graphrag.v2.models.CommunityReport;
import com.gdin.inspection.graphrag.v2.models.Entity;
import com.gdin.inspection.graphrag.v2.models.Relationship;
import com.gdin.inspection.graphrag.v2.models.TextUnit;
import com.gdin.inspection.graphrag.v2.query.ContextBuilderResult;
import com.gdin.inspection.graphrag.v2.util.TokenUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Java 版本的 LocalSearchMixedContext。
 *
 * 设计思路参考 Python 版 graphrag.query.structured_search.local_search.mixed_context.LocalSearchMixedContext：
 * - 上游已经完成 query -> selectedEntities 的语义映射（map_query_to_entities）
 * - 这里负责在一个总 token 预算内，按照「社区摘要 / 实体+关系 / 原文 text units」的比例，
 *   构建最终要喂给 LLM 的混合上下文，并同时返回结构化的 contextRecords 方便调试。
 */
@Slf4j
@Component
public class LocalSearchMixedContext {

    @Resource
    private TokenUtil tokenUtil;

    /**
     * 构建本地搜索用的混合上下文。
     *
     * @param selectedEntities  本轮 local search 选中的实体（按相关度排好序）
     * @param communityReports  所有社区报告（已经是 final_community_reports）
     * @param relationships     所有关系（final_relationships）
     * @param textUnits         所有文本单元（final_text_units）
     * @param maxContextTokens  上下文总 token 预算（例如 5000 / 8000）
     * @param communityRatio    预算给社区摘要的比例 0~1（对应 Python 的 community_prop）
     * @param graphRatio        预算给实体+关系表的比例 0~1
     */
    public ContextBuilderResult buildMixedContext(
            List<Entity> selectedEntities,
            List<CommunityReport> communityReports,
            List<Relationship> relationships,
            List<TextUnit> textUnits,
            int maxContextTokens,
            double communityRatio,
            double graphRatio
    ) {
        if (selectedEntities == null) selectedEntities = Collections.emptyList();
        if (communityReports == null) communityReports = Collections.emptyList();
        if (relationships == null) relationships = Collections.emptyList();
        if (textUnits == null) textUnits = Collections.emptyList();

        // -------- 1. 按比例分配 token 预算（社区 / 图 / 原文） --------
        int communityBudget = (int) Math.floor(maxContextTokens * communityRatio);
        int graphBudget = (int) Math.floor(maxContextTokens * graphRatio);
        int sourceBudget = maxContextTokens - communityBudget - graphBudget;
        if (sourceBudget < 0) {
            // 极端情况：比例设置不合理，退化为三份平均
            int avg = maxContextTokens / 3;
            communityBudget = graphBudget = sourceBudget = avg;
        }

        StringBuilder finalText = new StringBuilder();
        Map<String, List<Map<String, Object>>> finalRecords = new LinkedHashMap<>();

        // -------- 2. 社区上下文（对应 _build_community_context） --------
        CommunityContextResult communityResult = buildCommunityContext(
                selectedEntities,
                communityReports,
                communityBudget
        );
        if (!communityResult.contextText.isEmpty()) {
            finalText.append("【社区概览】\n");
            finalText.append(communityResult.contextText).append("\n\n");
        }
        finalRecords.put("communities", communityResult.records);

        // -------- 3. 实体 + 关系上下文（对应 _build_local_context） --------
        GraphContextResult graphResult = buildGraphContext(
                selectedEntities,
                relationships,
                graphBudget
        );
        if (!graphResult.contextText.isEmpty()) {
            finalText.append("【实体与关系】\n");
            finalText.append(graphResult.contextText).append("\n\n");
        }
        finalRecords.put("entities", graphResult.entityRecords);
        finalRecords.put("relationships", graphResult.relationshipRecords);

        // -------- 4. 原文 text unit 证据（对应 _build_text_unit_context） --------
        SourceContextResult sourceResult = buildSourceContext(
                selectedEntities,
                relationships,
                textUnits,
                sourceBudget
        );
        if (!sourceResult.contextText.isEmpty()) {
            finalText.append("【原文证据】\n");
            finalText.append(sourceResult.contextText).append("\n");
        }
        finalRecords.put("text_units", sourceResult.records);

        return ContextBuilderResult.builder()
                .contextText(finalText.toString().trim())
                .contextRecords(finalRecords)
                .build();
    }

    // ============================================================
    //  社区上下文：对齐 Python build_community_context 的语义
    // ============================================================

    private CommunityContextResult buildCommunityContext(
            List<Entity> selectedEntities,
            List<CommunityReport> communityReports,
            int tokenBudget
    ) {
        if (tokenBudget <= 0 || communityReports.isEmpty() || selectedEntities.isEmpty()) {
            return new CommunityContextResult("", Collections.emptyList());
        }

        Set<String> selectedEntityIds = selectedEntities.stream()
                .map(Entity::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 计算每个社区与当前查询实体的重叠实体数，类似 Python 中按 source_entity_ids 重叠排序
        List<CommunityScore> scored = new ArrayList<>();
        for (CommunityReport report : communityReports) {
            List<String> sourceEntityIds = report.getSourceEntityIds();
            if (sourceEntityIds == null || sourceEntityIds.isEmpty()) {
                continue;
            }
            long overlap = sourceEntityIds.stream()
                    .filter(selectedEntityIds::contains)
                    .count();
            if (overlap <= 0) {
                continue;
            }
            scored.add(new CommunityScore(report, (int) overlap));
        }

        if (scored.isEmpty()) {
            return new CommunityContextResult("", Collections.emptyList());
        }

        // 按重叠实体数降序，其次按 rating / size 等做次级排序
        scored.sort(Comparator
                .comparingInt((CommunityScore cs) -> cs.overlap).reversed()
                .thenComparingDouble(cs -> Optional.ofNullable(cs.report.getRating()).orElse(0.0)).reversed()
                .thenComparingInt(cs -> Optional.ofNullable(cs.report.getSize()).orElse(0)));

        StringBuilder sb = new StringBuilder();
        List<Map<String, Object>> records = new ArrayList<>();
        int usedTokens = 0;

        int idx = 1;
        for (CommunityScore cs : scored) {
            CommunityReport r = cs.report;

            String title = nullToEmpty(r.getTitle());
            String shortSummary = nullToEmpty(r.getSummary());
            String fullSummary = nullToEmpty(r.getFullContent());
            // 优先用 summary，summary 不够就补 full_content 的前若干字符
            String combinedSummary = shortSummary.isEmpty() ? fullSummary : shortSummary;
            combinedSummary = truncate(combinedSummary, 800);

            StringBuilder one = new StringBuilder();
            one.append("社区 #").append(idx).append("\n");
            one.append("社区编号：").append(r.getCommunity())
                    .append("  层级：").append(r.getLevel()).append("\n");
            one.append("标题：").append(title).append("\n");
            one.append("涉及选中实体数：").append(cs.overlap).append("\n");
            if (r.getRating() != null) {
                one.append("重要性评分：").append(r.getRating());
                if (r.getRatingExplanation() != null && !r.getRatingExplanation().isEmpty()) {
                    one.append("（").append(truncate(r.getRatingExplanation(), 200)).append("）");
                }
                one.append("\n");
            }
            one.append("摘要：").append(combinedSummary).append("\n\n");

            int tokens = safeCountTokens(one.toString());
            if (usedTokens + tokens > tokenBudget) {
                break;
            }
            usedTokens += tokens;
            sb.append(one);

            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("id", r.getId());
            rec.put("human_readable_id", r.getHumanReadableId());
            rec.put("community", r.getCommunity());
            rec.put("level", r.getLevel());
            rec.put("title", title);
            rec.put("summary", combinedSummary);
            rec.put("overlap_selected_entities", cs.overlap);
            rec.put("rating", r.getRating());
            rec.put("rating_explanation", r.getRatingExplanation());
            rec.put("size", r.getSize());
            rec.put("source_entity_ids", r.getSourceEntityIds());
            rec.put("source_text_unit_ids", r.getSourceTextUnitIds());
            records.add(rec);

            idx++;
        }

        return new CommunityContextResult(sb.toString().trim(), records);
    }

    // ============================================================
    //  实体 + 关系统计上下文：对齐 Python _build_local_context 的语义
    // ============================================================

    private GraphContextResult buildGraphContext(
            List<Entity> selectedEntities,
            List<Relationship> relationships,
            int tokenBudget
    ) {
        if (tokenBudget <= 0 || selectedEntities.isEmpty()) {
            return new GraphContextResult("", Collections.emptyList(), Collections.emptyList());
        }

        // 预构建 id -> Entity / Relationship 以及实体标题映射，方便展示
        Map<String, Entity> entityById = selectedEntities.stream()
                .filter(e -> e.getId() != null)
                .collect(Collectors.toMap(
                        Entity::getId,
                        e -> e,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        Map<String, Relationship> relById = relationships == null
                ? Collections.emptyMap()
                : relationships.stream()
                .filter(r -> r.getId() != null)
                .collect(Collectors.toMap(
                        Relationship::getId,
                        r -> r,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        Set<String> selectedEntityIds = entityById.keySet();

        // 挑出与选中实体直接相连的关系
        List<Relationship> relatedRels = relationships == null
                ? Collections.emptyList()
                : relationships.stream()
                .filter(r -> selectedEntityIds.contains(r.getSource())
                        || selectedEntityIds.contains(r.getTarget()))
                .collect(Collectors.toList());

        // 简单按照 weight 从大到小排序，缺失 weight 视为 1.0
        relatedRels = relatedRels.stream()
                .sorted(Comparator.comparingDouble(
                        (Relationship r) -> Optional.ofNullable(r.getWeight()).orElse(1.0)
                ).reversed())
                .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();
        List<Map<String, Object>> entityRecords = new ArrayList<>();
        List<Map<String, Object>> relationshipRecords = new ArrayList<>();

        // 将 tokenBudget 粗略一分为二：一半给实体，一半给关系
        int entityBudget = tokenBudget / 2;
        int relBudget = tokenBudget - entityBudget;

        // ---------- 实体部分 ----------
        int usedEntityTokens = 0;
        int entIdx = 1;
        for (Entity e : selectedEntities) {
            StringBuilder one = new StringBuilder();
            one.append("实体 #").append(entIdx).append("\n");
            one.append("名称：").append(nullToEmpty(e.getTitle())).append("\n");
            if (e.getType() != null) {
                one.append("类型：").append(e.getType()).append("\n");
            }
            if (e.getSummary() != null && !e.getSummary().isEmpty()) {
                one.append("摘要：").append(truncate(e.getSummary(), 400)).append("\n");
            } else if (e.getDescriptionList() != null && !e.getDescriptionList().isEmpty()) {
                one.append("描述：")
                        .append(truncate(String.join("；", e.getDescriptionList()), 400))
                        .append("\n");
            }
            if (e.getAliases() != null && !e.getAliases().isEmpty()) {
                one.append("别名：").append(String.join("，", e.getAliases())).append("\n");
            }
            one.append("\n");

            int tokens = safeCountTokens(one.toString());
            if (usedEntityTokens + tokens > entityBudget) {
                break;
            }
            usedEntityTokens += tokens;
            sb.append(one);

            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("id", e.getId());
            rec.put("human_readable_id", e.getHumanReadableId());
            rec.put("title", e.getTitle());
            rec.put("type", e.getType());
            rec.put("summary", e.getSummary());
            rec.put("aliases", e.getAliases());
            rec.put("text_unit_ids", e.getTextUnitIds());
            entityRecords.add(rec);

            entIdx++;
        }

        // ---------- 关系部分 ----------
        int usedRelTokens = 0;
        int relIdx = 1;
        for (Relationship r : relatedRels) {
            Entity source = entityById.get(r.getSource());
            Entity target = entityById.get(r.getTarget());

            String sourceName = source != null ? nullToEmpty(source.getTitle())
                    : nullToEmpty(r.getSource());
            String targetName = target != null ? nullToEmpty(target.getTitle())
                    : nullToEmpty(r.getTarget());

            StringBuilder one = new StringBuilder();
            one.append("关系 #").append(relIdx).append("\n");
            one.append("三元组：")
                    .append(sourceName)
                    .append(" -[")
                    .append(nullToEmpty(r.getPredicate()))
                    .append("]-> ")
                    .append(targetName)
                    .append("\n");
            if (r.getWeight() != null) {
                one.append("权重：").append(r.getWeight()).append("\n");
            }
            if (r.getSummary() != null && !r.getSummary().isEmpty()) {
                one.append("摘要：").append(truncate(r.getSummary(), 400)).append("\n");
            } else if (r.getDescriptionList() != null && !r.getDescriptionList().isEmpty()) {
                one.append("描述：")
                        .append(truncate(String.join("；", r.getDescriptionList()), 400))
                        .append("\n");
            }
            one.append("\n");

            int tokens = safeCountTokens(one.toString());
            if (usedRelTokens + tokens > relBudget) {
                break;
            }
            usedRelTokens += tokens;
            sb.append(one);

            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("id", r.getId());
            rec.put("human_readable_id", r.getHumanReadableId());
            rec.put("source", r.getSource());
            rec.put("target", r.getTarget());
            rec.put("predicate", r.getPredicate());
            rec.put("summary", r.getSummary());
            rec.put("weight", r.getWeight());
            rec.put("text_unit_ids", r.getTextUnitIds());
            relationshipRecords.add(rec);

            relIdx++;
        }

        return new GraphContextResult(sb.toString().trim(), entityRecords, relationshipRecords);
    }

    // ============================================================
    //  原文 TextUnit 上下文：对齐 Python _build_text_unit_context 的语义
    // ============================================================

    private SourceContextResult buildSourceContext(
            List<Entity> selectedEntities,
            List<Relationship> relationships,
            List<TextUnit> textUnits,
            int tokenBudget
    ) {
        if (tokenBudget <= 0 || textUnits == null || textUnits.isEmpty()) {
            return new SourceContextResult("", Collections.emptyList());
        }

        Set<String> selectedEntityIds = selectedEntities == null
                ? Collections.emptySet()
                : selectedEntities.stream()
                .map(Entity::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 计算每个 text unit 对应的关系数量，尽量对齐 Python 中 count_relationships 的逻辑
        List<TextUnitScore> scored = new ArrayList<>();
        for (TextUnit tu : textUnits) {
            int relCount = countRelationshipsForTextUnit(tu, relationships);
            // 如果 text unit 中根本没有任何选中实体/关系，价值很低，可以直接过滤掉
            boolean hasSelectedEntity = tu.getEntityIds() != null
                    && tu.getEntityIds().stream().anyMatch(selectedEntityIds::contains);
            if (!hasSelectedEntity && relCount == 0) {
                continue;
            }
            scored.add(new TextUnitScore(tu, relCount));
        }

        if (scored.isEmpty()) {
            return new SourceContextResult("", Collections.emptyList());
        }

        // 先按关系数量降序，再按 text unit 自身的 human_readable_id 升序
        scored.sort(Comparator
                .comparingInt((TextUnitScore s) -> s.relationshipCount).reversed()
                .thenComparingInt(s ->
                        Optional.ofNullable(s.textUnit.getHumanReadableId()).orElse(0)
                ));

        StringBuilder sb = new StringBuilder();
        List<Map<String, Object>> records = new ArrayList<>();
        int usedTokens = 0;

        int idx = 1;
        for (TextUnitScore s : scored) {
            TextUnit tu = s.textUnit;
            String text = nullToEmpty(tu.getText());
            text = truncate(text, 800);

            StringBuilder one = new StringBuilder();
            one.append("文本单元 #").append(idx).append("\n");
            one.append("原文片段：").append(text).append("\n");
            one.append("关联关系数量：").append(s.relationshipCount).append("\n\n");

            int tokens = safeCountTokens(one.toString());
            if (usedTokens + tokens > tokenBudget) {
                break;
            }
            usedTokens += tokens;
            sb.append(one);

            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("id", tu.getId());
            rec.put("human_readable_id", tu.getHumanReadableId());
            rec.put("text", text);
            rec.put("relationship_count", s.relationshipCount);
            rec.put("entity_ids", tu.getEntityIds());
            rec.put("relationship_ids", tu.getRelationshipIds());
            rec.put("document_ids", tu.getDocumentIds());
            records.add(rec);

            idx++;
        }

        return new SourceContextResult(sb.toString().trim(), records);
    }

    /**
     * 对齐 Python 的 count_relationships 思路：
     * - 如果 textUnit 自己带 relationship_ids，就直接用它和关系表做交集。
     * - 否则，就遍历所有关系，数一下 textUnit.id 是否出现在 relationship.source_text_unit_ids 里。
     */
    private int countRelationshipsForTextUnit(TextUnit textUnit, List<Relationship> relationships) {
        if (relationships == null || relationships.isEmpty() || textUnit == null) {
            return 0;
        }

        // 优先使用 textUnit 自身记录的 relationship_ids
        List<String> relIds = textUnit.getRelationshipIds();
        if (relIds != null && !relIds.isEmpty()) {
            Set<String> relIdSet = new HashSet<>(relIds);
            int count = 0;
            for (Relationship r : relationships) {
                if (r.getId() != null && relIdSet.contains(r.getId())) {
                    count++;
                }
            }
            return count;
        }

        // 回退方案：检查关系的 source_text_unit_ids
        String textUnitId = textUnit.getId();
        if (textUnitId == null) {
            return 0;
        }
        int count = 0;
        for (Relationship r : relationships) {
            List<String> srcTuIds = r.getTextUnitIds();
            if (srcTuIds == null || srcTuIds.isEmpty()) {
                continue;
            }
            for (String id : srcTuIds) {
                if (textUnitId.equals(id)) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    // ============================================================
    //  内部结果和评分结构体
    // ============================================================

    private static class CommunityContextResult {
        final String contextText;
        final List<Map<String, Object>> records;

        CommunityContextResult(String contextText, List<Map<String, Object>> records) {
            this.contextText = contextText;
            this.records = records;
        }
    }

    private static class CommunityScore {
        final CommunityReport report;
        final int overlap;

        CommunityScore(CommunityReport report, int overlap) {
            this.report = report;
            this.overlap = overlap;
        }
    }

    private static class GraphContextResult {
        final String contextText;
        final List<Map<String, Object>> entityRecords;
        final List<Map<String, Object>> relationshipRecords;

        GraphContextResult(
                String contextText,
                List<Map<String, Object>> entityRecords,
                List<Map<String, Object>> relationshipRecords
        ) {
            this.contextText = contextText;
            this.entityRecords = entityRecords;
            this.relationshipRecords = relationshipRecords;
        }
    }

    private static class SourceContextResult {
        final String contextText;
        final List<Map<String, Object>> records;

        SourceContextResult(String contextText, List<Map<String, Object>> records) {
            this.contextText = contextText;
            this.records = records;
        }
    }

    private static class TextUnitScore {
        final TextUnit textUnit;
        final int relationshipCount;

        TextUnitScore(TextUnit textUnit, int relationshipCount) {
            this.textUnit = textUnit;
            this.relationshipCount = relationshipCount;
        }
    }

    // ============================================================
    //  小工具方法
    // ============================================================

    private int safeCountTokens(String text) {
        try {
            return tokenUtil.getTokenCount(text);
        } catch (IOException e) {
            log.warn("计算 token 数量失败，退化为按字符长度估算", e);
            return text != null ? text.length() / 2 + 1 : 0;
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null || s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, maxLen) + "……";
    }
}
