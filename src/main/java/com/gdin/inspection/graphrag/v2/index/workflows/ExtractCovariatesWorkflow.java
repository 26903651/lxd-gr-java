package com.gdin.inspection.graphrag.v2.index.workflows;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.gdin.inspection.graphrag.v2.index.opertation.ExtractCovariatesOperation;
import com.gdin.inspection.graphrag.v2.index.opertation.extract.ExtractClaimsExtractor;
import com.gdin.inspection.graphrag.v2.index.strategy.ExtractClaimsStrategy;
import com.gdin.inspection.graphrag.v2.models.Covariate;
import com.gdin.inspection.graphrag.v2.models.TextUnit;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 对齐 Python: graphrag/index/workflows/extract_covariates.py
 */
@Slf4j
@Service
public class ExtractCovariatesWorkflow {

    @Resource
    private ExtractCovariatesOperation op;

    public List<Covariate> run(
            Boolean extractClaimsEnabled,
            List<TextUnit> textUnits,

            // ==== 对齐 Python strategy_config ====
            String claimDescription,
            Integer maxGleanings,
            String tupleDelimiter,
            String recordDelimiter,
            String completionDelimiter,

            // ==== entity_types（python 允许 None -> DEFAULT_ENTITY_TYPES）====
            List<String> entityTypesOrNames,

            // ==== extraction_prompt（python strategy 可覆盖）====
            String extractionPrompt
    ) {
        boolean enabled = extractClaimsEnabled != null && extractClaimsEnabled;
        if (!enabled) {
            log.info("extract_covariates skipped: extract_claims_enabled=false");
            return List.of();
        }
        if (CollectionUtil.isEmpty(textUnits)) throw new IllegalStateException("textUnits 不能为空");
        if (StrUtil.isBlank(claimDescription)) throw new IllegalStateException("claimDescription 不能为空");

        // Python：entity_types is None -> DEFAULT_ENTITY_TYPES
        List<String> specs = CollectionUtil.isEmpty(entityTypesOrNames) ? ExtractClaimsExtractor.DEFAULT_ENTITY_TYPES_ZH : entityTypesOrNames;

        // 直接贴合 Python 的 strategy_config 字段与默认 delimiter
        ExtractClaimsStrategy strategy = ExtractClaimsStrategy.builder()
                .enabled(true)
                .claimDescription(claimDescription)
                .maxGleanings(maxGleanings)
                .tupleDelimiter(tupleDelimiter)
                .recordDelimiter(recordDelimiter)
                .completionDelimiter(completionDelimiter)
                .extractionPrompt(extractionPrompt)
                .build();

        return op.extractCovariates(textUnits, "claim", specs, strategy);
    }
}
