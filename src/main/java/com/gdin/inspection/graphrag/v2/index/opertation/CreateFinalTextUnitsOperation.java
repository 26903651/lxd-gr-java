package com.gdin.inspection.graphrag.v2.index.opertation;

import cn.hutool.core.collection.CollectionUtil;
import com.gdin.inspection.graphrag.v2.models.Covariate;
import com.gdin.inspection.graphrag.v2.models.Entity;
import com.gdin.inspection.graphrag.v2.models.Relationship;
import com.gdin.inspection.graphrag.v2.models.TextUnit;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class CreateFinalTextUnitsOperation {

    public List<TextUnit> createFinalTextUnits(
            List<TextUnit> textUnits,
            List<Entity> finalEntities,
            List<Relationship> finalRelationships,
            List<Covariate> finalCovariates // 允许为 null
    ) {
        if (CollectionUtil.isEmpty(textUnits)) throw new IllegalArgumentException("textUnits 不能为空");
        if (CollectionUtil.isEmpty(finalEntities)) throw new IllegalArgumentException("finalEntities 不能为空");
        if (CollectionUtil.isEmpty(finalRelationships)) throw new IllegalArgumentException("finalRelationships 不能为空");

        // === _entities(df): text_unit_id -> entity_ids(unique) ===
        Map<String, LinkedHashSet<String>> tuToEntityIds = new LinkedHashMap<>();
        for (Entity e : finalEntities) {
            if (e == null || e.getId() == null) continue;
            List<String> tuIds = e.getTextUnitIds();
            if (CollectionUtil.isEmpty(tuIds)) continue;
            for (String tuId : tuIds) {
                if (tuId == null) continue;
                tuToEntityIds.computeIfAbsent(tuId, _k -> new LinkedHashSet<>()).add(e.getId());
            }
        }

        // === _relationships(df): text_unit_id -> relationship_ids(unique) ===
        Map<String, LinkedHashSet<String>> tuToRelationshipIds = new LinkedHashMap<>();
        for (Relationship r : finalRelationships) {
            if (r == null || r.getId() == null) continue;
            List<String> tuIds = r.getTextUnitIds();
            if (CollectionUtil.isEmpty(tuIds)) continue;
            for (String tuId : tuIds) {
                if (tuId == null) continue;
                tuToRelationshipIds.computeIfAbsent(tuId, _k -> new LinkedHashSet<>()).add(r.getId());
            }
        }

        // === _covariates(df): text_unit_id -> covariate_ids(unique) ===
        Map<String, LinkedHashSet<String>> tuToCovariateIds = new LinkedHashMap<>();
        if (finalCovariates != null) {
            for (Covariate c : finalCovariates) {
                if (c == null || c.getId() == null) continue;
                String tuId = c.getTextUnitId();
                if (tuId == null) continue;
                tuToCovariateIds.computeIfAbsent(tuId, _k -> new LinkedHashSet<>()).add(c.getId());
            }
        }

        // === selected["human_readable_id"] = selected.index ===
        List<TextUnit> finalized = new ArrayList<>(textUnits.size());
        for (int i = 0; i < textUnits.size(); i++) {
            TextUnit tu = textUnits.get(i);
            if (tu == null) continue;

            String id = tu.getId();
            if (id == null || id.isBlank()) {
                throw new IllegalStateException("TextUnit.id 不能为空（需要作为 join key）");
            }

            List<String> entityIds = new ArrayList<>(tuToEntityIds.getOrDefault(id, new LinkedHashSet<>()));
            List<String> relationshipIds = new ArrayList<>(tuToRelationshipIds.getOrDefault(id, new LinkedHashSet<>()));
            List<String> covariateIds = new ArrayList<>(tuToCovariateIds.getOrDefault(id, new LinkedHashSet<>()));

            finalized.add(TextUnit.builder()
                    .id(id)
                    .humanReadableId(i) // 对齐 Python：index
                    .text(tu.getText())
                    .nTokens(tu.getNTokens())
                    .documentIds(tu.getDocumentIds())
                    .entityIds(entityIds)
                    .relationshipIds(relationshipIds)
                    .covariateIds(covariateIds)
                    .build());
        }

        return finalized;
    }
}
