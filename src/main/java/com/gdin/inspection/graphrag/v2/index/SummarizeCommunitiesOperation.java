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

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class SummarizeCommunitiesOperation {

    @Resource
    private CommunityReportsExtractor communityReportsExtractor;

    private final ObjectMapper objectMapper = JsonUtils.mapper();

    public List<CommunityReport> summarizeCommunities(
            List<Community> communities,
            List<Entity> entities,
            List<Relationship> relationships,
            List<TextUnit> textUnits,
            int maxReportLength
    ) {
        // 1. 实体 / 关系 / TextUnit 索引
        Map<String, Entity> entityMap = entities.stream()
                .collect(Collectors.toMap(
                        Entity::getId,
                        e -> e,
                        (a, b) -> a
                ));

        Map<String, Relationship> relationshipMap = relationships.stream()
                .collect(Collectors.toMap(
                        Relationship::getId,
                        r -> r,
                        (a, b) -> a
                ));

        Map<String, TextUnit> textUnitMap = textUnits.stream()
                .collect(Collectors.toMap(
                        TextUnit::getId,
                        t -> t,
                        (a, b) -> a
                ));

        // 2. 计算所有 level，按从大到小排序（自底向上）
        List<Integer> levels = communities.stream()
                .map(Community::getLevel)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();

        List<CommunityReport> allReports = new ArrayList<>();
        // key = community (Integer)
        Map<Integer, CommunityReportsResult> structuredByCommunityId = new HashMap<>();

        int humanReadableCounter = 1;

        for (Integer level : levels) {
            List<Community> communitiesAtLevel = communities.stream()
                    .filter(c -> Objects.equals(c.getLevel(), level))
                    .toList();

            log.info("开始生成 level={} 的社区报告，社区数量={}", level, communitiesAtLevel.size());

            for (Community community : communitiesAtLevel) {
                Integer communityId = community.getCommunity();
                if (communityId == null) continue;

                // 3. 为该社区构造上下文
                String context = buildCommunityContext(
                        community,
                        entityMap,
                        relationshipMap,
                        textUnitMap,
                        structuredByCommunityId
                );

                // 4. 调用 LLM 生成报告
                CommunityReportsResult result = communityReportsExtractor.generateReport(context, maxReportLength);

                if (result.getStructuredOutput() == null) {
                    log.warn("社区 {} (level={}) 生成报告失败，跳过", communityId, level);
                    continue;
                }

                // 5. 映射成 CommunityReport
                CommunityReport reportRow = mapToCommunityReport(
                        community,
                        result,
                        level,
                        humanReadableCounter++
                );

                allReports.add(reportRow);
                structuredByCommunityId.put(communityId, result);
            }
        }

        return allReports;
    }

    private String buildCommunityContext(
            Community community,
            Map<String, Entity> entityMap,
            Map<String, Relationship> relationshipMap,
            Map<String, TextUnit> textUnitMap,
            Map<Integer, CommunityReportsResult> structuredByCommunityId
    ) {
        StringBuilder sb = new StringBuilder();

        // 1. 实体
        List<String> entityIds = Optional.ofNullable(community.getEntityIds()).orElse(List.of());
        if (!entityIds.isEmpty()) {
            sb.append("## 实体列表\n");
            sb.append("type,id,title,entity_type,summary,source_text_unit_ids\n");
            for (String id : entityIds) {
                Entity e = entityMap.get(id);
                if (e == null) continue;

                String sourceTextUnitIds = Optional.ofNullable(e.getSourceTextUnitIds())
                        .orElse(List.of())
                        .stream()
                        .collect(Collectors.joining("|"));

                sb.append("entity,")
                        .append(safeCsv(e.getId())).append(",")
                        .append(safeCsv(e.getTitle())).append(",")
                        .append(safeCsv(e.getType())).append(",")
                        .append(safeCsv(e.getSummary())).append(",")
                        .append(safeCsv(sourceTextUnitIds))
                        .append("\n");
            }
            sb.append("\n");
        }

        // 2. 关系
        List<String> relationshipIds = Optional.ofNullable(community.getRelationshipIds()).orElse(List.of());
        if (!relationshipIds.isEmpty()) {
            sb.append("## 关系列表\n");
            sb.append("type,id,source,target,predicate,summary,source_text_unit_ids\n");
            for (String id : relationshipIds) {
                Relationship r = relationshipMap.get(id);
                if (r == null) continue;

                String sourceTextUnitIds = Optional.ofNullable(r.getSourceTextUnitIds())
                        .orElse(List.of())
                        .stream()
                        .collect(Collectors.joining("|"));

                sb.append("relationship,")
                        .append(safeCsv(r.getId())).append(",")
                        .append(safeCsv(r.getSource())).append(",")
                        .append(safeCsv(r.getTarget())).append(",")
                        .append(safeCsv(r.getPredicate())).append(",")
                        .append(safeCsv(r.getSummary())).append(",")
                        .append(safeCsv(sourceTextUnitIds))
                        .append("\n");
            }
            sb.append("\n");
        }

        // 3. TextUnit
        List<String> textUnitIds = Optional.ofNullable(community.getTextUnitIds()).orElse(List.of());
        if (!textUnitIds.isEmpty()) {
            sb.append("## 文本单元列表\n");
            sb.append("type,id,document_id,text\n");
            for (String id : textUnitIds) {
                TextUnit t = textUnitMap.get(id);
                if (t == null) continue;

                sb.append("text_unit,")
                        .append(safeCsv(t.getId())).append(",")
                        .append(safeCsv(t.getDocumentIds().get(0))).append(",")
                        .append(safeCsv(t.getText()))
                        .append("\n");
            }
            sb.append("\n");
        }

        // 4. 子社区报告（注意这里 children 是 List<Integer>）
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
        return value.replace("\n", " ")
                .replace("\r", " ")
                .replace(",", "，");
    }

    private CommunityReport mapToCommunityReport(
            Community community,
            CommunityReportsResult result,
            int level,
            int humanReadableId
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

        return CommunityReport.builder()
                .id(IdUtil.getSnowflakeNextIdStr())
                .humanReadableId(humanReadableId)
                .community(community.getCommunity()) // Integer
                .level(level)
                .parent(community.getParent())
                .children(Optional.ofNullable(community.getChildren()).orElse(List.of()))
                .title(structured.getTitle())
                .summary(structured.getSummary())
                .fullContent(result.getOutput())
                .rating(structured.getRating())
                .ratingExplanation(structured.getRatingExplanation())
                .findings(findingsJson)
                .fullContentJson(fullContentJson)
                .period(community.getPeriod())
                .size(community.getSize())
                .generatedAt(Instant.now())
                .sourceEntityIds(Optional.ofNullable(community.getEntityIds()).orElse(List.of()))
                .sourceTextUnitIds(Optional.ofNullable(community.getTextUnitIds()).orElse(List.of()))
                .metadata(community.getMetadata())
                .build();
    }
}
