package com.gdin.inspection.graphrag.v2.state;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简单的内存实现，线程安全。仅用于测试/本地开发。
 */
public class InMemoryStateStore implements StateStore {

    // tableName -> (hrid -> sourceId)
    private final Map<String, ConcurrentHashMap<Integer, String>> store = new ConcurrentHashMap<>();

    @Override
    public boolean existsHumanReadableId(String tableName, Integer humanReadableId) {
        ConcurrentHashMap<Integer, String> table = store.get(tableName);
        return table != null && table.containsKey(humanReadableId);
    }

    @Override
    public boolean isBelongsToSameSource(String tableName, Integer humanReadableId, String sourceId) {
        ConcurrentHashMap<Integer, String> table = store.get(tableName);
        if (table == null) return false;
        String existing = table.get(humanReadableId);
        return existing != null && existing.equals(sourceId);
    }

    @Override
    public void saveHumanReadableId(String tableName, Integer humanReadableId, String sourceId) {
        store.computeIfAbsent(tableName, k -> new ConcurrentHashMap<>())
                .put(humanReadableId, sourceId);
    }

    @Override
    public void clearHumanReadableIds(String tableName) {
        store.remove(tableName);
    }

    @Override
    public Optional<Integer> getMaxHumanReadableId(String tableName) {
        ConcurrentHashMap<Integer, String> table = store.get(tableName);
        if (table == null || table.isEmpty()) return Optional.empty();
        return table.keySet().stream().max(Integer::compareTo);
    }
}

