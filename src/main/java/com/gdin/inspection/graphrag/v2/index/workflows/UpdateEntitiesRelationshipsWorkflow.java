package com.gdin.inspection.graphrag.v2.index.workflows;

import com.gdin.inspection.graphrag.v2.index.opertation.extract.DescriptionSummaryExtractor;
import com.gdin.inspection.graphrag.v2.index.update.IncrementalEntities;
import com.gdin.inspection.graphrag.v2.index.update.IncrementalRelationships;
import com.gdin.inspection.graphrag.v2.index.update.StringListJsonCodec;
import com.gdin.inspection.graphrag.v2.models.Entity;
import com.gdin.inspection.graphrag.v2.models.Relationship;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class UpdateEntitiesRelationshipsWorkflow {

    @Resource
    private DescriptionSummaryExtractor descriptionSummaryExtractor;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Result {
        private List<Entity> mergedEntities;
        private List<Relationship> mergedRelationships;
        private Map<String, String> entityIdMapping; // {delta.id -> old.id}
    }

    /**
     * 对齐 Python: update_entities_relationships.run_workflow + _update_entities_and_relationships
     *
     * 输入：oldEntities, deltaEntities, oldRelationships, deltaRelationships
     * 输出：mergedEntities/Relationships（已 summarize），以及 entityIdMapping
     */
    public Result run(
            List<Entity> oldEntities,
            List<Entity> deltaEntities,
            List<Relationship> oldRelationships,
            List<Relationship> deltaRelationships,
            Integer entitySummaryMaxWords,
            Integer relationshipSummaryMaxWords
    ) {
        entitySummaryMaxWords = entitySummaryMaxWords == null ? 150 : entitySummaryMaxWords;
        relationshipSummaryMaxWords = relationshipSummaryMaxWords == null ? 150 : relationshipSummaryMaxWords;

        // 1) merge entities（description 先是 list/json）
        IncrementalEntities.Result entRes = IncrementalEntities.groupAndResolveEntities(oldEntities, deltaEntities);
        List<Entity> mergedEntities = entRes.getMergedEntities();
        Map<String, String> entityIdMapping = entRes.getEntityIdMapping();

        // 2) merge relationships（description 先是 list/json）
        List<Relationship> mergedRelationships = IncrementalRelationships.updateAndMergeRelationships(oldRelationships, deltaRelationships);

        // 3) summarize entities：把 json(list[str]) 解出来喂给 LLM，写回摘要字符串
        List<Entity> summarizedEntities = new ArrayList<>(mergedEntities.size());
        for (Entity e : mergedEntities) {
            if (e == null) continue;
            List<String> descList = StringListJsonCodec.decode(e.getDescription());
            String summary = descriptionSummaryExtractor.summarize(
                    e.getTitle() == null ? (e.getId() == null ? "" : e.getId()) : e.getTitle(),
                    descList,
                    entitySummaryMaxWords
            );
            summarizedEntities.add(Entity.builder()
                    .id(e.getId())
                    .humanReadableId(e.getHumanReadableId())
                    .title(e.getTitle())
                    .type(e.getType())
                    .description(summary) // ✅ 最终表：摘要字符串
                    .textUnitIds(e.getTextUnitIds())
                    .frequency(e.getFrequency())
                    .degree(e.getDegree())
                    .x(e.getX())
                    .y(e.getY())
                    .build());
        }

        // 4) summarize relationships：同理
        List<Relationship> summarizedRelationships = new ArrayList<>(mergedRelationships.size());
        for (Relationship r : mergedRelationships) {
            if (r == null) continue;
            List<String> descList = StringListJsonCodec.decode(r.getDescription());
            String rid = (r.getSource() == null ? "" : r.getSource()) + "->" + (r.getTarget() == null ? "" : r.getTarget());
            String summary = descriptionSummaryExtractor.summarize(rid, descList, relationshipSummaryMaxWords);

            summarizedRelationships.add(Relationship.builder()
                    .id(r.getId())
                    .humanReadableId(r.getHumanReadableId())
                    .source(r.getSource())
                    .target(r.getTarget())
                    .description(summary) // ✅ 最终表：摘要字符串
                    .weight(r.getWeight())
                    .combinedDegree(r.getCombinedDegree())
                    .textUnitIds(r.getTextUnitIds())
                    .build());
        }

        log.info("增量合并并摘要完成: entities={}, relationships={}", summarizedEntities.size(), summarizedRelationships.size());
        return new Result(summarizedEntities, summarizedRelationships, entityIdMapping);
    }
}
