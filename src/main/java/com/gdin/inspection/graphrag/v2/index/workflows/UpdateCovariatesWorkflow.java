package com.gdin.inspection.graphrag.v2.index.workflows;

import cn.hutool.core.collection.CollectionUtil;
import com.gdin.inspection.graphrag.v2.models.Covariate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 对齐 Python: graphrag/index/workflows/update_covariates.py
 * 逻辑：
 * - 如果 old_covariates 和 delta_covariates 都存在（Java 侧用 “非空” 近似表存在）
 * - delta_covariates.human_readable_id 从 old_max + 1 开始重排
 * - merged = old + delta_adjusted
 * - 返回 merged（由调用方决定写回 main scope）
 */
@Slf4j
@Service
public class UpdateCovariatesWorkflow {

    public List<Covariate> run(List<Covariate> oldCovariates, List<Covariate> deltaCovariates) {
        // Python: 两边都 has_table 才更新；否则啥也不做
        if (CollectionUtil.isEmpty(oldCovariates) && CollectionUtil.isEmpty(deltaCovariates)) {
            log.info("covariates(main+delta) 都为空，不用更新");
            return List.of();
        }

        int oldMax = oldCovariates.stream()
                .filter(Objects::nonNull)
                .map(Covariate::getHumanReadableId)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(-1);
        int initialId = oldMax + 1;

        List<Covariate> deltaAdjusted = new ArrayList<>(deltaCovariates.size());
        int i = 0;
        for (Covariate c : deltaCovariates) {
            if (c == null) continue;
            deltaAdjusted.add(copyWithHrid(c, initialId + (i++)));
        }

        List<Covariate> merged = new ArrayList<>(oldCovariates.size() + deltaAdjusted.size());
        merged.addAll(oldCovariates);
        merged.addAll(deltaAdjusted);

        // 可选：为了稳定性，按 hrid 排序（不影响 Python 语义，但能避免后续 max() 受乱序影响）
        merged.sort(Comparator.comparingInt(x -> x == null || x.getHumanReadableId() == null ? -1 : x.getHumanReadableId()));
        return merged;
    }

    private Covariate copyWithHrid(Covariate c, Integer hrid) {
        // 这里只做字段“原样拷贝 + 改 hrid”
        return Covariate.builder()
                .id(c.getId())
                .humanReadableId(hrid)
                .covariateType(c.getCovariateType())
                .type(c.getType())
                .description(c.getDescription())
                .subjectId(c.getSubjectId())
                .objectId(c.getObjectId())
                .status(c.getStatus())
                .startDate(c.getStartDate())
                .endDate(c.getEndDate())
                .sourceText(c.getSourceText())
                .textUnitId(c.getTextUnitId())
                .build();
    }
}
