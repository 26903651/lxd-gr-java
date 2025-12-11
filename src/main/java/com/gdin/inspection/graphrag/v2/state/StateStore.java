package com.gdin.inspection.graphrag.v2.state;

import java.util.Optional;

/**
 * StateStore: minimal contract for pipeline state and human_readable_id bookkeeping.
 * 请根据生产环境替换为持久化实现（SQLite/MySQL/Redis/etc）。
 */
public interface StateStore {

    /**
     * 检查给定表名与 human_readable_id 是否存在（已被占用）。
     * @param tableName 例如 "entities","relationships","text_units","community_reports" 等
     * @param humanReadableId 整数序号（python 中的 dataframe index）
     * @return true if exists
     */
    boolean existsHumanReadableId(String tableName, Integer humanReadableId);

    /**
     * 检查给定 human_readable_id 是否与同一 sourceId 绑定（用于幂等判断）
     * @param tableName 表名
     * @param humanReadableId 整数序号
     * @param sourceId 源 id（例如该条记录最终的 UUID 或外部 source 标识）
     * @return true if the stored binding equals sourceId
     */
    boolean isBelongsToSameSource(String tableName, Integer humanReadableId, String sourceId);

    /**
     * 保存 human_readable_id 与 sourceId 的绑定（写入最终表时调用）
     * 注意：实现方应保证原子性/幂等性。
     * @param tableName 表名
     * @param humanReadableId 整数序号
     * @param sourceId 对应的记录 id（UUID）
     */
    void saveHumanReadableId(String tableName, Integer humanReadableId, String sourceId);

    /**
     * 清空某个表的 human_readable_id 绑定（用于 full rebuild/reset index 情形）
     * Python finalize 通常会 reset_index -> 从 0 开始分配，某些场景需要先清空旧索引。
     * @param tableName 表名
     */
    void clearHumanReadableIds(String tableName);

    /**
     * 可选：返回某表当前已占用的最大 human_readable_id（若实现可用）
     * 用于增量场景或检查。若不可用，可返回 Optional.empty()
     */
    Optional<Integer> getMaxHumanReadableId(String tableName);
}