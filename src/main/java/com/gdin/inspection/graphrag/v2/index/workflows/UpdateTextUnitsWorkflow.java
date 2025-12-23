package com.gdin.inspection.graphrag.v2.index.workflows;

import cn.hutool.core.collection.CollectionUtil;
import com.gdin.inspection.graphrag.v2.models.TextUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 对齐 Python：
 * graphrag/index/workflows/update_text_units.py
 * 目标：
 * 1) delta_text_units.entity_ids：按 entity_id_mapping 做替换（deltaEntityId -> oldEntityId）
 * 2) delta_text_units.human_readable_id：重排到 oldMax+1 开始
 * 3) merged_text_units = concat(old_text_units, delta_text_units)
 * 注意：
 * - 这里不做 finalize（create_final_text_units 已经做过 Java 侧的 finalize/writeback）
 * - 这里不处理 relationship_ids/covariate_ids：Python 只处理 entity_ids 映射，因为只有 entity 会发生 id 复用（title 冲突映射）
 */
@Slf4j
@Service
public class UpdateTextUnitsWorkflow {

    /**
     * 主入口：合并 old + delta textUnits（并在 delta 上应用 entity id mapping + hrid 重排）。
     *
     * @param oldTextUnits   main scope 的 final text_units（已经是 finalize 后的）
     * @param deltaTextUnits delta scope 的 final text_units（已经是 finalize 后的）
     * @param entityIdMapping {deltaEntityId -> oldEntityId}，只在 title 冲突时存在
     */
    public List<TextUnit> run(
            List<TextUnit> oldTextUnits,
            List<TextUnit> deltaTextUnits,
            Map<String, String> entityIdMapping
    ) {
        oldTextUnits = oldTextUnits == null ? Collections.emptyList() : oldTextUnits;
        deltaTextUnits = deltaTextUnits == null ? Collections.emptyList() : deltaTextUnits;
        entityIdMapping = entityIdMapping == null ? Collections.emptyMap() : entityIdMapping;

        if (oldTextUnits.isEmpty() && deltaTextUnits.isEmpty()) throw new IllegalStateException("text_units(main+delta) 都为空，拒绝继续");

        log.info(
                "开始更新切片：oldTextUnits={}, deltaTextUnits={}, mappingSize={}",
                oldTextUnits.size(),
                deltaTextUnits.size(),
                entityIdMapping.size()
        );

        // 1) delta: apply entity id mapping
        List<TextUnit> deltaMapped = applyEntityIdMapping(deltaTextUnits, entityIdMapping);

        // 2) delta: reassign human_readable_id
        int oldMaxHrid = oldTextUnits.stream()
                .filter(Objects::nonNull)
                .map(TextUnit::getHumanReadableId)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(-1);

        int start = oldMaxHrid + 1;
        List<TextUnit> deltaReHrid = reassignHumanReadableId(deltaMapped, start);

        // 3) concat old + delta
        List<TextUnit> merged = new ArrayList<>(oldTextUnits.size() + deltaReHrid.size());
        merged.addAll(oldTextUnits);
        merged.addAll(deltaReHrid);
        return merged;
    }

    /**
     * 对齐 Python:
     * delta_text_units["entity_ids"] = delta_text_units["entity_ids"].apply(lambda x: [mapping.get(i, i) for i in x] if x is not None else x)
     */
    private List<TextUnit> applyEntityIdMapping(List<TextUnit> deltaTextUnits, Map<String, String> entityIdMapping) {
        if (CollectionUtil.isEmpty(deltaTextUnits) || CollectionUtil.isEmpty(entityIdMapping)) {
            return deltaTextUnits == null ? Collections.emptyList() : deltaTextUnits;
        }

        List<TextUnit> out = new ArrayList<>(deltaTextUnits.size());
        for (TextUnit tu : deltaTextUnits) {
            if (tu == null) continue;

            List<String> entityIds = tu.getEntityIds();
            List<String> mapped = null;

            if (entityIds != null) {
                mapped = new ArrayList<>(entityIds.size());
                for (String id : entityIds) {
                    if (id == null) continue;
                    mapped.add(entityIdMapping.getOrDefault(id, id));
                }
            }

            out.add(TextUnit.builder()
                    .id(tu.getId())
                    .humanReadableId(tu.getHumanReadableId()) // 先不改，下一步统一重排
                    .text(tu.getText())
                    .nTokens(tu.getNTokens())
                    .documentIds(tu.getDocumentIds())
                    .entityIds(mapped)                         // 替换后的 entity_ids
                    .relationshipIds(tu.getRelationshipIds())
                    .covariateIds(tu.getCovariateIds())
                    .build());
        }
        return out;
    }

    /**
     * 对齐 Python:
     * initial_id = old_text_units["human_readable_id"].max() + 1
     * delta_text_units["human_readable_id"] = np.arange(initial_id, initial_id + len(delta_text_units))
     */
    private List<TextUnit> reassignHumanReadableId(List<TextUnit> deltaTextUnits, int start) {
        if (CollectionUtil.isEmpty(deltaTextUnits)) return Collections.emptyList();

        List<TextUnit> out = new ArrayList<>(deltaTextUnits.size());
        int idx = 0;
        for (TextUnit tu : deltaTextUnits) {
            if (tu == null) continue;

            out.add(TextUnit.builder()
                    .id(tu.getId())
                    .humanReadableId(start + (idx++))
                    .text(tu.getText())
                    .nTokens(tu.getNTokens())
                    .documentIds(tu.getDocumentIds())
                    .entityIds(tu.getEntityIds())
                    .relationshipIds(tu.getRelationshipIds())
                    .covariateIds(tu.getCovariateIds())
                    .build());
        }
        return out;
    }
}
