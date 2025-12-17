package com.gdin.inspection.graphrag.v2.index.opertation;

import cn.hutool.core.collection.CollectionUtil;
import com.gdin.inspection.graphrag.v2.index.opertation.extract.ExtractClaimsExtractor;
import com.gdin.inspection.graphrag.v2.models.Covariate;
import com.gdin.inspection.graphrag.v2.models.TextUnit;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
            String covariateType,            // Python 传 "claim"
            List<String> entityTypesOrNames,  // Python entity_types 默认 DEFAULT_ENTITY_TYPES
            String claimDescription,          // strategy_config["claim_description"]
            String extractionPrompt,          // strategy_config.get("extraction_prompt") —— 你要完全对齐的话，未来可支持覆盖 prompt
            Integer maxGleanings,             // strategy_config["max_gleanings"]
            String tupleDelimiter,
            String recordDelimiter,
            String completionDelimiter
    ) {
        if (CollectionUtil.isEmpty(textUnits)) return List.of();
        String type = (covariateType == null) ? "claim" : covariateType;

        // 这里对齐 Python：extraction_prompt 可由 strategy 覆盖。
        // 你现在给的 prompt 文件是固定 EXTRACT_CLAIMS_PROMPT；如果你要“完全对齐 strategy 可覆盖”，
        // 需要你把 resolved_strategy 里 extraction_prompt 的来源告诉我（你那边 Java config 怎么表达）。
        // 目前先按 Python 默认：使用 prompts 文件。
        List<Covariate> rows = new ArrayList<>();

        for (TextUnit tu : textUnits) {
            if (tu == null) continue;

            // Python：text_units["text_unit_id"]=text_units["id"]
            String textUnitId = tu.getId();

            List<ExtractClaimsExtractor.ClaimRecord> claims = extractClaimsExtractor.extract(
                    tu.getText(),
                    entityTypesOrNames,
                    claimDescription,
                    tupleDelimiter,
                    recordDelimiter,
                    completionDelimiter,
                    maxGleanings
            );

            for (ExtractClaimsExtractor.ClaimRecord c : claims) {
                Covariate cov = Covariate.builder()
                        .id(UUID.randomUUID().toString())    // uuid4
                        .humanReadableId(null)               // 先空，最后统一按 index 写
                        .covariateType(type)
                        .type(c.getType())
                        .description(c.getDescription())
                        .subjectId(c.getSubjectId())
                        .objectId(c.getObjectId())
                        .status(c.getStatus())
                        .startDate(c.getStartDate())
                        .endDate(c.getEndDate())
                        .sourceText(c.getSourceText())
                        .textUnitId(textUnitId)
                        .build();
                rows.add(cov);
            }
        }

        // human_readable_id = index
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
                    .build());
        }

        log.info("extract_covariates done: textUnits={}, covariates={}", textUnits.size(), finalized.size());
        return finalized;
    }
}
