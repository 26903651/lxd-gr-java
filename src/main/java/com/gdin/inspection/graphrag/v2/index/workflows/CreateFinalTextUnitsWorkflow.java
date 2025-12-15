package com.gdin.inspection.graphrag.v2.index.workflows;

import cn.hutool.core.collection.CollectionUtil;
import com.gdin.inspection.graphrag.v2.index.CreateFinalTextUnitsOperation;
import com.gdin.inspection.graphrag.v2.models.*;
import com.gdin.inspection.graphrag.v2.storage.KnowledgeSliceWriteBackService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class CreateFinalTextUnitsWorkflow {

    @Resource
    private CreateFinalTextUnitsOperation op;

    @Resource
    private KnowledgeSliceWriteBackService writeBackService;

    public List<TextUnit> run(
            List<TextUnit> textUnits,
            List<Entity> entities,
            List<Relationship> relationships,
            List<Covariate> covariates
    ) {
        if (CollectionUtil.isEmpty(textUnits)) throw new IllegalStateException("textUnits 不能为空");
        if (CollectionUtil.isEmpty(entities)) throw new IllegalStateException("entities 不能为空");
        if (CollectionUtil.isEmpty(relationships)) throw new IllegalStateException("relationships 不能为空");

        log.info(
                "开始生成最终文本单元：textUnits={}, entities={}, relationships={}, covariates={}",
                textUnits.size(),
                entities.size(),
                relationships.size(),
                covariates==null?0:covariates.size()
        );

        List<TextUnit> finalTextUnits = op.createFinalTextUnits(textUnits, entities, relationships, covariates);

        // 写回知识库切片
        writeBackService.writeBackToKnowledgeBase(finalTextUnits);

        return finalTextUnits;
    }
}
