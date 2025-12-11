package com.gdin.inspection.graphrag.v2.index;

import com.gdin.inspection.graphrag.v2.models.Entity;
import com.gdin.inspection.graphrag.v2.state.StateStore;

import java.util.List;

/**
 * FinalizeAndPersist: 在 finalize 产生最终实体/关系后，负责将 human_readable_id 绑定写入 StateStore。
 * 典型用法：在全量重建时先 clearHumanReadableIds，再遍历 finalized 列表逐条保存。
 */
public class FinalizeAndPersist {

    private final StateStore stateStore;

    public FinalizeAndPersist(StateStore stateStore) {
        this.stateStore = stateStore;
    }

    /**
     * 将 finalizedEntities 写入 stateStore（并在外部负责把 records 写入向量库或 DB）
     * @param tableName eg. "entities"
     * @param finalizedEntities 已包含 humanReadableId 与 id 的实体列表
     */
    public void persistFinalEntities(String tableName, List<Entity> finalizedEntities) {
        // 与 Python 全量 finalize 行为一致：先清空旧绑定（reset_index 场景）
        stateStore.clearHumanReadableIds(tableName);

        for (Entity e : finalizedEntities) {
            Integer hrid = e.getHumanReadableId();
            String sourceId = e.getId(); // UUID
            if (hrid == null) {
                throw new IllegalStateException("Entity missing humanReadableId: " + e);
            }
            if (sourceId == null || sourceId.isEmpty()) {
                throw new IllegalStateException("Entity missing id (UUID): " + e);
            }

            if (stateStore.existsHumanReadableId(tableName, hrid)) {
                if (!stateStore.isBelongsToSameSource(tableName, hrid, sourceId)) {
                    // 在全量重建下不应发生，因为我们已经 clear。这里做防卫检查。
                    throw new IllegalStateException("hrid conflict for " + hrid + " in " + tableName);
                }
                // 幂等：已属于相同 source，跳过或继续（这里选择跳过保存）
            } else {
                stateStore.saveHumanReadableId(tableName, hrid, sourceId);
            }
        }
    }
}

