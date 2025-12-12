package com.gdin.inspection.graphrag.v2.index;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdin.inspection.graphrag.v2.models.Community;
import com.gdin.inspection.graphrag.v2.models.CommunityReport;
import com.gdin.inspection.graphrag.v2.models.Entity;
import com.gdin.inspection.graphrag.v2.models.Relationship;
import com.gdin.inspection.graphrag.v2.models.TextUnit;
import com.gdin.inspection.graphrag.v2.util.JsonUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class SummarizeCommunitiesOperation {

    @Resource
    private CommunityReportsExtractor communityReportsExtractor;

    private final ObjectMapper objectMapper = JsonUtils.mapper();

    /**
     * Java 版等价于 Python 的 summarize_communities(...) + finalize_community_reports(...) 的组合：
     * - 按 level 从高到低生成社区报告（利用子社区报告作为额外上下文）；
     * - 生成的 CommunityReport 字段对齐 COMMUNITY_REPORTS_FINAL_COLUMNS。
     */
    public List<CommunityReport> summarizeCommunities(
            List<Community> communities,
            List<Entity> entities,
            List<Relationship> relationships,
            List<TextUnit> textUnits,
            int maxReportLength
    ) {
        if (communities == null || communities.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. 实体 / 关系 / TextUnit 索引（id -> 对象）
        Map<String, Entity> entityMap = entities == null
                ? Collections.emptyMap()
                : entities.stream()
                .filter(e -> e.getId() != null)
                .collect(Collectors.toMap(
                        Entity::getId,
                        e -> e,
                        (a, b) -> a
                ));

        Map<String, Relationship> relationshipMap = relationships == null
                ? Collections.emptyMap()
                : relationships.stream()
                .filter(r -> r.getId() != null)
                .collect(Collectors.toMap(
                        Relationship::getId,
                        r -> r,
                        (a, b) -> a
                ));

        Map<String, TextUnit> textUnitMap = textUnits == null
                ? Collections.emptyMap()
                : textUnits.stream()
                .filter(t -> t.getId() != null)
                .collect(Collectors.toMap(
                        TextUnit::getId,
                        t -> t,
                        (a, b) -> a
                ));

        // 2. 计算所有 level，按从大到小排序（Python: get_levels(..., reverse=True)）
        List<Integer> levels = communities.stream()
                .map(Community::getLevel)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();

        List<CommunityReport> allReports = new ArrayList<>();
        // 已生成的结构化报告，用于父社区上下文中引用子社区报告
        // key = communityId
        Map<Integer, CommunityReportsResult> structuredByCommunityId = new HashMap<>();

        for (Integer level : levels) {
            List<Community> communitiesAtLevel = communities.stream()
                    .filter(c -> Objects.equals(c.getLevel(), level))
                    .toList();

            log.info("开始生成 level={} 的社区报告，社区数量={}", level, communitiesAtLevel.size());

            for (Community community : communitiesAtLevel) {
                Integer communityId = community.getCommunity();
                if (communityId == null) continue;

                // 3. 构造该社区的上下文（实体 / 关系 / 文本单元 / 子社区报告）
                String context = buildCommunityContext(
                        community,
                        entityMap,
                        relationshipMap,
                        textUnitMap,
                        structuredByCommunityId
                );

                // 4. 调用 LLM 生成报告（等价于 Python CommunityReportsExtractor.__call__）
                CommunityReportsResult result = communityReportsExtractor.generateReport(context, maxReportLength);

                if (result == null || result.getStructuredOutput() == null) {
                    log.warn("社区 {} (level={}) 生成报告失败，跳过", communityId, level);
                    continue;
                }

                // 5. 对齐 Python finalize_community_reports：human_readable_id = community
                CommunityReport reportRow = mapToCommunityReport(
                        community,
                        result,
                        level
                );

                allReports.add(reportRow);
                structuredByCommunityId.put(communityId, result);
            }
        }

        return allReports;
    }

    /**
     * 构建单个社区的上下文字符串，整体结构等价于 Python 的 level_context_builder 产出：
     * - 实体列表 CSV
     * - 关系列表 CSV
     * - 文本单元列表 CSV
     * - 子社区报告 CSV
     */
    private String buildCommunityContext(
            Community community,
            Map<String, Entity> entityMap,
            Map<String, Relationship> relationshipMap,
            Map<String, TextUnit> textUnitMap,
            Map<Integer, CommunityReportsResult> structuredByCommunityId
    ) {
        StringBuilder sb = new StringBuilder();

        // 1. 实体列表：使用 description（已经是 summary），没有 summary 字段
        List<String> entityIds = Optional.ofNullable(community.getEntityIds()).orElse(List.of());
        if (!entityIds.isEmpty()) {
            sb.append("## 实体列表\n");
            sb.append("type,id,title,entity_type,description,text_unit_ids\n");
            for (String id : entityIds) {
                Entity e = entityMap.get(id);
                if (e == null) continue;

                String textUnitIds = Optional.ofNullable(e.getTextUnitIds())
                        .orElse(List.of())
                        .stream()
                        .collect(Collectors.joining("|"));

                sb.append("entity,")
                        .append(safeCsv(e.getId())).append(",")
                        .append(safeCsv(e.getTitle())).append(",")
                        .append(safeCsv(e.getType())).append(",")
                        .append(safeCsv(e.getDescription())).append(",")
                        .append(safeCsv(textUnitIds))
                        .append("\n");
            }
            sb.append("\n");
        }

        // 2. 关系列表：只有 description，没有 predicate / summary 字段
        List<String> relationshipIds = Optional.ofNullable(community.getRelationshipIds()).orElse(List.of());
        if (!relationshipIds.isEmpty()) {
            sb.append("## 关系列表\n");
            sb.append("type,id,source,target,description,text_unit_ids\n");
            for (String id : relationshipIds) {
                Relationship r = relationshipMap.get(id);
                if (r == null) continue;

                String textUnitIds = Optional.ofNullable(r.getTextUnitIds())
                        .orElse(List.of())
                        .stream()
                        .collect(Collectors.joining("|"));

                sb.append("relationship,")
                        .append(safeCsv(r.getId())).append(",")
                        .append(safeCsv(r.getSource())).append(",")
                        .append(safeCsv(r.getTarget())).append(",")
                        .append(safeCsv(r.getDescription())).append(",")
                        .append(safeCsv(textUnitIds))
                        .append("\n");
            }
            sb.append("\n");
        }

        // 3. 文本单元列表
        List<String> textUnitIds = Optional.ofNullable(community.getTextUnitIds()).orElse(List.of());
        if (!textUnitIds.isEmpty()) {
            sb.append("## 文本单元列表\n");
            sb.append("type,id,document_id,text\n");
            for (String id : textUnitIds) {
                TextUnit t = textUnitMap.get(id);
                if (t == null) continue;

                String docId = "";
                List<String> docIds = t.getDocumentIds();
                if (docIds != null && !docIds.isEmpty()) {
                    docId = docIds.get(0);
                }

                sb.append("text_unit,")
                        .append(safeCsv(t.getId())).append(",")
                        .append(safeCsv(docId)).append(",")
                        .append(safeCsv(t.getText()))
                        .append("\n");
            }
            sb.append("\n");
        }

        // 4. 子社区报告（children 是 List<Integer>，用已生成的 structuredByCommunityId 填充）
        List<Integer> children = Optional.ofNullable(community.getChildren()).orElse(List.of());
        List<Integer> existingChildIds = children.stream()
                .filter(structuredByCommunityId::containsKey)
                .toList();

        if (!existingChildIds.isEmpty()) {
            sb.append("## 子社区报告\n");
            sb.append("type,sub_community_id,title,summary\n");
            for (Integer childId : existingChildIds) {
                CommunityReportsResult childResult = structuredByCommunityId.get(childId);
                if (childResult == null || childResult.getStructuredOutput() == null) {
                    continue;
                }
                var sr = childResult.getStructuredOutput();
                sb.append("sub_community_report,")
                        .append(safeCsv(String.valueOf(childId))).append(",")
                        .append(safeCsv(sr.getTitle())).append(",")
                        .append(safeCsv(sr.getSummary()))
                        .append("\n");
            }
            sb.append("\n");
        }

        return sb.toString().trim();
    }

    private String safeCsv(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\n", " ")
                .replace("\r", " ")
                .replace(",", "，");
    }

    /**
     * 把 LLM 的结构化输出 + 社区公共字段，拼成最终的 CommunityReport 行。
     * 对齐 Python 的 COMMUNITY_REPORTS_FINAL_COLUMNS：
     * [id, human_readable_id, community, level, parent, children, title, summary,
     *  full_content, rank, rating_explanation, findings, full_content_json, period, size]
     */
    private CommunityReport mapToCommunityReport(
            Community community,
            CommunityReportsResult result,
            int level
    ) {
        var structured = result.getStructuredOutput();

        String findingsJson = null;
        try {
            findingsJson = objectMapper.writeValueAsString(
                    Optional.ofNullable(structured.getFindings()).orElse(List.of())
            );
        } catch (JsonProcessingException e) {
            log.warn("序列化 findings 失败，将 findings 置为空", e);
        }

        String fullContentJson = null;
        try {
            fullContentJson = objectMapper.writeValueAsString(structured);
        } catch (JsonProcessingException e) {
            log.warn("序列化 full_content_json 失败", e);
        }

        Integer communityId = community.getCommunity();

        return CommunityReport.builder()
                .id(IdUtil.getSnowflakeNextIdStr())
                // Python: human_readable_id = community
                .humanReadableId(communityId)
                .community(communityId)
                .level(level)
                .parent(community.getParent())
                .children(Optional.ofNullable(community.getChildren()).orElse(List.of()))
                .title(structured.getTitle())
                .summary(structured.getSummary())
                .fullContent(result.getOutput())
                .rank(structured.getRating())
                .ratingExplanation(structured.getRatingExplanation())
                .findings(findingsJson)
                .fullContentJson(fullContentJson)
                .period(community.getPeriod())
                .size(community.getSize())
                .build();
    }
}
