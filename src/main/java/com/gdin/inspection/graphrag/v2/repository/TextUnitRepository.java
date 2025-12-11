package com.gdin.inspection.graphrag.v2.repository;

import com.gdin.inspection.graphrag.v2.models.TextUnit;

import java.util.List;

/**
 * TextUnit 持久化契约。可实现为数据库/文件/内存等。
 */
public interface TextUnitRepository {
    /**
     * 批量保存 TextUnit（实现应保证线程安全或由调用方保证）
     * @param textUnits 待保存的文本单元
     */
    void saveAll(List<TextUnit> textUnits);

    /**
     * 根据 documentId 查询 TextUnits
     * @param documentId 文档 id
     * @return 对应的 TextUnit 列表（若无则返回空列表）
     */
    List<TextUnit> listByDocumentId(String documentId);

    /**
     * 清空所有（测试或全量重建场景）
     */
    void clearAll();
}
