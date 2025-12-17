package com.gdin.inspection.graphrag.v2.index.workflows;

import cn.hutool.core.collection.CollectionUtil;
import com.gdin.inspection.graphrag.v2.models.TextUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class LoadUpdateDocumentsWorkflow {

    @Resource
    private LoadInputDocumentsWorkflow loadInputDocumentsWorkflow;

    public List<TextUnit> run(List<String> updateDocumentIds) throws Exception {
        if (CollectionUtil.isEmpty(updateDocumentIds)) {
            log.warn("未发现增量文档(update_document_ids 为空)，停止增量 pipeline");
            return Collections.emptyList();
        }

        List<TextUnit> deltaTextUnits = loadInputDocumentsWorkflow.run(updateDocumentIds);

        if (CollectionUtil.isEmpty(deltaTextUnits)) {
            log.warn("增量文档切片为空，停止增量 pipeline");
            return Collections.emptyList();
        }

//        ctx.put("delta_text_units", deltaTextUnits);
        log.info("加载增量文档完成: updateDocs={}, deltaTextUnits={}", updateDocumentIds.size(), deltaTextUnits.size());

        return deltaTextUnits;
    }
}
