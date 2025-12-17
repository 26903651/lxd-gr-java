package com.gdin.inspection.graphrag.v2.index.opertation;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.gdin.inspection.graphrag.v2.index.opertation.extract.ExtractClaimsExtractor;
import com.gdin.inspection.graphrag.v2.index.strategy.ExtractClaimsStrategy;
import com.gdin.inspection.graphrag.v2.models.Covariate;
import com.gdin.inspection.graphrag.v2.models.TextUnit;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对齐 Python: graphrag/index/operations/extract_covariates/extract_covariates.py
 */
@Slf4j
@Component
public class ExtractCovariatesOperation {

    @Resource
    private ExtractClaimsExtractor extractClaimsExtractor;

    public List<Covariate> extractCovariates(
            List<TextUnit> textUnits,
            String covariateType,
            List<String> entityTypesOrNames,
            ExtractClaimsStrategy strategy
    ) {
        if (CollectionUtil.isEmpty(textUnits)) return List.of();
        if (strategy == null || StrUtil.isBlank(strategy.getClaimDescription())) {
            throw new IllegalArgumentException("claimDescription 不能为空（对齐 Python claim_description required）");
        }

        String type = StrUtil.blankToDefault(covariateType, "claim");

        // 对齐 Python：resolved_entities_map 在整个 extraction 过程中共享（即便当前为空也要保留语义）
        Map<String, String> resolvedEntitiesMap = new ConcurrentHashMap<>();

        // Python 输入是 Iterable[str]；这里按 TextUnit 逐个 doc 处理
        List<Covariate> rows = new ArrayList<>();

        for (TextUnit tu : textUnits) {
            if (tu == null || StrUtil.isBlank(tu.getText())) continue;

            // Python: text_units["text_unit_id"] = text_units["id"]
            String textUnitId = tu.getId();

            ExtractClaimsExtractor.ClaimExtractorResult result = extractClaimsExtractor.extract(
                    List.of(tu.getText()),
                    entityTypesOrNames,
                    resolvedEntitiesMap,
                    strategy
            );

            List<Map<String, Object>> claimData = result.getOutput();
            if (CollectionUtil.isEmpty(claimData)) continue;

            for (Map<String, Object> item : claimData) {
                String subjectId = asString(item.get("subject_id"));
                String objectId = asString(item.get("object_id"));
                String claimType = asString(item.get("type"));
                String status = asString(item.get("status"));
                String description = asString(item.get("description"));
                String sourceText = asString(item.get("source_text"));
                String startRaw = asString(item.get("start_date"));
                String endRaw = asString(item.get("end_date"));

                Instant start = ExtractClaimsExtractor.parseInstantLoose(startRaw);
                Instant end = ExtractClaimsExtractor.parseInstantLoose(endRaw);

                Covariate cov = Covariate.builder()
                        // Python 最终会在 workflow 里 uuid4 覆盖 id；你这里直接 uuid 对齐最终效果
                        .id(UUID.randomUUID().toString())
                        .humanReadableId(null) // 最后统一按 index 赋值
                        .covariateType(type)
                        .type(claimType)
                        .description(description)
                        .subjectId(subjectId)
                        .objectId(objectId)
                        .status(status)
                        .startDate(start)
                        .endDate(end)
                        .sourceText(sourceText)
                        .textUnitId(textUnitId)
                        .recordId(null) // 对齐 Python：该字段允许为空（现有 claim_extractor 并未生成）
                        .build();

                rows.add(cov);
            }
        }

        // human_readable_id = index（对齐 Python: covariates.index）
        List<Covariate> finalized = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            Covariate c = rows.get(i);
            finalized.add(Covariate.builder()
                    .id(c.getId())
                    .humanReadableId(i)
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
                    .recordId(c.getRecordId())
                    .build());
        }

        log.info("extract_covariates done: textUnits={}, covariates={}", textUnits.size(), finalized.size());
        return finalized;
    }

    private String asString(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        if (s.isEmpty()) return null;
        if ("NONE".equalsIgnoreCase(s)) return null;
        return s;
    }
}
