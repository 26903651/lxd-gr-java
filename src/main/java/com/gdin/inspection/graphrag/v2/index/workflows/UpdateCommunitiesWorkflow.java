package com.gdin.inspection.graphrag.v2.index.workflows;

import cn.hutool.core.collection.CollectionUtil;
import com.gdin.inspection.graphrag.v2.models.Community;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 对齐 Python：
 * - graphrag/index/workflows/update_communities.py
 * - graphrag/index/update/communities.py::_update_and_merge_communities
 * 输入：old(main) communities + delta(delta) communities
 * 输出：
 *  1) merged communities（已做 community id 偏移、parent 偏移、title 重命名、hrid=community）
 *  2) community_id_mapping（用于后续 update_community_reports / update_community_reports_text 等步骤）
 */
@Slf4j
@Service
public class UpdateCommunitiesWorkflow {

    public Result run(List<Community> oldCommunities, List<Community> deltaCommunities) {
        if (CollectionUtil.isEmpty(oldCommunities) && CollectionUtil.isEmpty(deltaCommunities)) throw new IllegalStateException("communities(main+delta) 都为空，拒绝继续");

        oldCommunities = oldCommunities == null ? Collections.emptyList() : oldCommunities;
        deltaCommunities = deltaCommunities == null ? Collections.emptyList() : deltaCommunities;

        log.info("开始更新社区：old={}, delta={}", oldCommunities.size(), deltaCommunities.size());

        return updateAndMergeCommunities(oldCommunities, deltaCommunities);
    }

    /**
     * 严格对齐 Python: _update_and_merge_communities(old_communities, delta_communities)
     */
    private Result updateAndMergeCommunities(List<Community> oldCommunities, List<Community> deltaCommunities) {

        // 1) old_max_community_id = max(old.community.fillna(0).astype(int))
        int oldMaxCommunityId = 0;
        boolean hasAnyOld = false;
        for (Community c : oldCommunities) {
            if (c == null) continue;
            Integer comm = safeToIntOrNull(c.getCommunity());
            if (comm == null) continue;
            hasAnyOld = true;
            if (comm > oldMaxCommunityId) oldMaxCommunityId = comm;
        }
        if (!hasAnyOld) oldMaxCommunityId = 0;

        // 2) community_id_mapping = { v -> v + oldMax + 1 for v in delta.community.dropna().astype(int) }
        //    plus {-1:-1}
        Map<Integer, Integer> mapping = new LinkedHashMap<>();
        for (Community c : deltaCommunities) {
            if (c == null) continue;
            Integer v = safeToIntOrNull(c.getCommunity());
            if (v == null) continue;
            // Python dict comprehension：如果 v 重复，后写覆盖前写（结果相同），这里也覆盖即可
            mapping.put(v, v + oldMaxCommunityId + 1);
        }
        mapping.put(-1, -1);

        // 3) delta.community / delta.parent apply mapping（astype(int) 的语义）
        List<Community> deltaRemapped = new ArrayList<>(deltaCommunities.size());
        for (Community c : deltaCommunities) {
            if (c == null) continue;

            Integer oldCommunity = safeToIntOrNull(c.getCommunity());
            Integer oldParent = safeToIntOrNull(c.getParent());

            Integer newCommunity = oldCommunity == null ? null : mapping.getOrDefault(oldCommunity, oldCommunity);
            Integer newParent = oldParent == null ? null : mapping.getOrDefault(oldParent, oldParent);

            deltaRemapped.add(copyCommunityWithMappedIds(c, newCommunity, newParent));
        }

        // 4) old_communities["community"] astype(int) —— Java 这里等价为确保为 Integer（你本来就是 Integer）
        // 5) merged = concat(old, deltaRemapped)
        List<Community> merged = new ArrayList<>(oldCommunities.size() + deltaRemapped.size());
        merged.addAll(oldCommunities);
        merged.addAll(deltaRemapped);

        // 6) Rename title: "Community " + community
        // 7) human_readable_id = community
        // 8) select COMMUNITIES_FINAL_COLUMNS —— Java 通过 builder 输出“规范字段集合”
        List<Community> finalMerged = new ArrayList<>(merged.size());
        for (Community c : merged) {
            if (c == null) continue;

            Integer communityId = safeToIntOrNull(c.getCommunity()); // Python: merged["community"].astype(str/int)
            String title = "Community " + (communityId == null ? "null" : communityId);

            // human_readable_id = community （Python 这里是 int）
            Integer hrid = communityId;

            finalMerged.add(Community.builder()
                    .id(c.getId())
                    .humanReadableId(hrid)
                    .community(communityId)
                    .level(c.getLevel())
                    .parent(safeToIntOrNull(c.getParent()))
                    .children(c.getChildren())
                    .title(title)
                    .entityIds(c.getEntityIds())
                    .relationshipIds(c.getRelationshipIds())
                    .textUnitIds(c.getTextUnitIds())
                    .period(c.getPeriod())  // Python：如果没列就补 None，这里自然允许 null
                    .size(c.getSize())      // 同上
                    .build());
        }

        // 9) mapping 输出：Python 返回 dict，且 keys/values 是 int，并包含 -1:-1
        //    你后续 workflow 如果需要 String->String，也可以在 pipeline ctx 那层做转换；我建议保持 Integer->Integer 以贴合 Python。
        Map<Integer, Integer> communityIdMapping = mapping;

        return new Result(finalMerged, communityIdMapping);
    }

    private Community copyCommunityWithMappedIds(Community c, Integer newCommunity, Integer newParent) {
        return Community.builder()
                .id(c.getId())
                .humanReadableId(c.getHumanReadableId()) // Python 还没重置 hrid，最后统一用 community 覆盖；这里保留原值即可
                .community(newCommunity)
                .level(c.getLevel())
                .parent(newParent)
                .children(c.getChildren())
                .title(c.getTitle())
                .entityIds(c.getEntityIds())
                .relationshipIds(c.getRelationshipIds())
                .textUnitIds(c.getTextUnitIds())
                .period(c.getPeriod())
                .size(c.getSize())
                .build();
    }

    private Integer safeToIntOrNull(Object v) {
        if (v == null) return null;
        if (v instanceof Integer i) return i;
        if (v instanceof Long l) return Math.toIntExact(l);
        if (v instanceof Short s) return (int) s;
        if (v instanceof Byte b) return (int) b;
        if (v instanceof Double d) return d.isNaN() ? null : (int) Math.floor(d); // 尽量贴近 astype(int)
        if (v instanceof Float f) return f.isNaN() ? null : (int) Math.floor(f);
        if (v instanceof String s) {
            String t = s.trim();
            if (t.isEmpty()) return null;
            return Integer.parseInt(t);
        }
        // 兜底：直接 toString parse
        return Integer.parseInt(String.valueOf(v).trim());
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Result {
        private List<Community> mergedCommunities;
        private Map<Integer, Integer> communityIdMapping;
    }
}
