package com.gdin.inspection.graphrag.v2.repository;

import cn.hutool.core.collection.CollectionUtil;
import com.gdin.inspection.graphrag.v2.models.TextUnit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 线程安全的内存实现，仅用于测试/本地开发。
 */
public class InMemoryTextUnitRepository implements TextUnitRepository {

    // documentId -> list of units
    private final ConcurrentHashMap<String, ArrayList<TextUnit>> store = new ConcurrentHashMap<>();

    @Override
    public void saveAll(List<TextUnit> units) {
        for (TextUnit tu : units) {
            List<String> documentIds = tu.getDocumentIds();
            if (CollectionUtil.isEmpty(documentIds)) {
                continue; // 或者直接跳过/抛异常，看你要求
            }
            String documentId = documentIds.get(0);
            store.compute(documentId, (k, v) -> {
                if (v == null) {
                    v = new ArrayList<>();
                }
                v.add(tu);
                return v;
            });
        }
    }

    @Override
    public List<TextUnit> listByDocumentId(String documentId) {
        ArrayList<TextUnit> list = store.get(documentId);
        if (list == null) return Collections.emptyList();
        return new ArrayList<>(list);
    }

    @Override
    public void clearAll() {
        store.clear();
    }
}
