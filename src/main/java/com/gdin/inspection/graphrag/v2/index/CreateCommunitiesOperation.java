package com.gdin.inspection.graphrag.v2.index;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.gdin.inspection.graphrag.v2.index.cluster.GraphClusterClient;
import com.gdin.inspection.graphrag.v2.index.cluster.LeidenCluster;
import com.gdin.inspection.graphrag.v2.models.Community;
import com.gdin.inspection.graphrag.v2.models.Entity;
import com.gdin.inspection.graphrag.v2.models.Relationship;
import jakarta.annotation.Resource;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 对应 Python 的 create_communities(...)：
 *
 * 1. 用关系表构图并执行 Leiden 聚类（通过 GraphClusterClient 调用 Python）；
 * 2. 把聚类结果展开成 (community, level, parent, title) 表；
 * 3. 按社区聚合 entity_ids；
 * 4. 按 (community, level, parent) 聚合 relationship_ids / text_unit_ids；
 * 5. 生成最终 Community 列表，填充 id / humanReadableId / community / level / parent / children / title /
 *    entityIds / relationshipIds / textUnitIds / period / size / summary / metadata / createdAt。
 */
@Slf4j
@Component
public class CreateCommunitiesOperation {

    @Resource
    private GraphClusterClient graphClusterClient;

    /**
     * 主入口：对实体 + 关系做社区划分。
     *
     * @param entities      已完成 finalize 的实体列表
     * @param relationships 已完成 finalize 的关系列表
     * @param maxClusterSize 聚类配置，对应 Python config.cluster_graph.max_cluster_size
     * @param useLcc        是否只使用最大连通子图，对应 config.cluster_graph.use_lcc
     * @param seed          随机种子，可为 null
     */
    public List<Community> createCommunities(
            List<Entity> entities,
            List<Relationship> relationships,
            int maxClusterSize,
            boolean useLcc,
            Integer seed
    ) {
        if (CollectionUtil.isEmpty(entities)) throw new IllegalArgumentException("entities 不能为空");
        if (CollectionUtil.isEmpty(relationships))  throw new IllegalArgumentException("relationships 不能为空");

        // 1. 调用 Python 端的聚类（等价于 create_graph + cluster_graph）
        List<LeidenCluster> clusters = graphClusterClient.clusterGraph(
                relationships,
                maxClusterSize,
                useLcc,
                seed
        );

        if (CollectionUtil.isEmpty(clusters)) {
            log.warn("Leiden 聚类结果为空，返回空社区列表");
            return Collections.emptyList();
        }

        // 2. 展开聚类结果：(community, level, parent, title)
        List<CommunityNodeRecord> nodeRecords = new ArrayList<>();
        int maxLevel = 0;

        for (LeidenCluster cluster : clusters) {
            int level = cluster.getLevel();
            int communityId = cluster.getCommunityId();
            Integer parentId = cluster.getParentCommunityId();
            if (parentId == null) parentId = -1;
            maxLevel = Math.max(maxLevel, level);

            List<String> titles = cluster.getNodeTitles();
            if (titles == null) continue;
            for (String title : titles) {
                if (title == null) continue;
                nodeRecords.add(new CommunityNodeRecord(communityId, level, parentId, title));
            }
        }

        if (nodeRecords.isEmpty()) {
            log.warn("聚类结果中没有任何节点标题，返回空社区列表");
            return Collections.emptyList();
        }

        // 3. 构建 title -> entityIds 映射，对应 entities.merge(...).groupby("community").agg(entity_ids=("id", list))
        Map<String, List<String>> titleToEntityIds = new LinkedHashMap<>();
        for (Entity e : entities) {
            String title = e.getTitle();
            String id = e.getId();
            if (title == null || id == null) continue;
            titleToEntityIds
                    .computeIfAbsent(title, _k -> new ArrayList<>())
                    .add(id);
        }

        // 4. 社区 -> 实体 ID 集合，对应 entity_ids DataFrame
        Map<Integer, Set<String>> communityToEntityIds = new LinkedHashMap<>();
        for (CommunityNodeRecord rec : nodeRecords) {
            List<String> ids = titleToEntityIds.get(rec.getTitle());
            if (ids == null || ids.isEmpty()) continue;
            Set<String> set = communityToEntityIds.computeIfAbsent(rec.getCommunityId(), _k -> new LinkedHashSet<>());
            set.addAll(ids);
        }

        // 5. 按层级构建 title -> (community, parent) 映射，用于筛选同一社区内的关系
        Map<Integer, List<CommunityNodeRecord>> levelToRecords = nodeRecords.stream()
                .collect(Collectors.groupingBy(
                        CommunityNodeRecord::getLevel,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        Map<CommunityLevelParentKey, Aggregation> aggregationMap = new LinkedHashMap<>();

        for (int level = 0; level <= maxLevel; level++) {
            List<CommunityNodeRecord> recordsAtLevel = levelToRecords.get(level);
            if (CollectionUtil.isEmpty(recordsAtLevel)) continue;

            // title -> record（社区 + 父社区）
            Map<String, CommunityNodeRecord> titleToRecord = new LinkedHashMap<>();
            for (CommunityNodeRecord rec : recordsAtLevel) {
                titleToRecord.put(rec.getTitle(), rec);
            }

            // 遍历所有关系，找到源和目标都在同一社区的边
            for (Relationship rel : relationships) {
                String sourceTitle = rel.getSource();
                String targetTitle = rel.getTarget();
                if (sourceTitle == null || targetTitle == null) continue;

                CommunityNodeRecord srcRec = titleToRecord.get(sourceTitle);
                CommunityNodeRecord tgtRec = titleToRecord.get(targetTitle);
                if (srcRec == null || tgtRec == null) continue;

                if (srcRec.getCommunityId() != tgtRec.getCommunityId()) continue;

                int communityId = srcRec.getCommunityId();
                Integer parentId = srcRec.getParentId();
                CommunityLevelParentKey key = new CommunityLevelParentKey(communityId, level, parentId);

                Aggregation agg = aggregationMap.computeIfAbsent(
                        key,
                        _k -> new Aggregation(new LinkedHashSet<>(), new LinkedHashSet<>())
                );

                // 聚合 relationship_ids
                if (rel.getId() != null) agg.getRelationshipIds().add(rel.getId());

                // 聚合 text_unit_ids，对应 Python relationships["text_unit_ids"]
                List<String> textUnits = rel.getSourceTextUnitIds();
                if (CollectionUtil.isNotEmpty(textUnits)) {
                    for (String tu : textUnits) {
                        if (!StrUtil.isBlank(tu))  agg.getTextUnitIds().add(tu);
                    }
                }
            }
        }

        if (aggregationMap.isEmpty()) {
            log.warn("没有任何落在同一社区内的关系，返回空社区列表");
            return Collections.emptyList();
        }

        // 6. 汇总成「社区行」，对应 Python all_grouped.merge(entity_ids,...)
        List<CommunityRow> rows = new ArrayList<>();
        for (Map.Entry<CommunityLevelParentKey, Aggregation> entry : aggregationMap.entrySet()) {
            CommunityLevelParentKey key = entry.getKey();
            Aggregation agg = entry.getValue();

            int communityId = key.getCommunityId();
            int level = key.getLevel();
            Integer parent = key.getParentId();

            Set<String> entityIdSet = communityToEntityIds.getOrDefault(communityId, Collections.emptySet());
            List<String> entityIds = new ArrayList<>(entityIdSet);
            List<String> relationshipIds = new ArrayList<>(agg.getRelationshipIds());
            List<String> textUnitIds = new ArrayList<>(agg.getTextUnitIds());

            // 对齐 Python 的 sorted(set(...))
            Collections.sort(entityIds);
            Collections.sort(relationshipIds);
            Collections.sort(textUnitIds);

            rows.add(new CommunityRow(
                    communityId,
                    level,
                    parent,
                    entityIds,
                    relationshipIds,
                    textUnitIds
            ));
        }

        // 7. 计算 parent -> children 映射，对应 Python 的 parent_grouped + merge
        Map<Integer, LinkedHashSet<Integer>> parentToChildren = new LinkedHashMap<>();
        for (CommunityRow row : rows) {
            Integer parent = row.getParent();
            if (parent == null) continue;
            parentToChildren
                    .computeIfAbsent(parent, _k -> new LinkedHashSet<>())
                    .add(row.getCommunity());
        }

        // 8. 构造最终 Community 列表
        List<Community> result = new ArrayList<>();
        Instant now = Instant.now();
        String period = LocalDate.now(ZoneOffset.UTC).toString();

        for (CommunityRow row : rows) {
            int communityId = row.getCommunity();
            int level = row.getLevel();
            Integer parent = row.getParent();

            List<Integer> children = parentToChildren.getOrDefault(communityId, new LinkedHashSet<>())
                    .stream()
                    .toList(); // 保持出现顺序即可，Python 用的是 unique()

            Community community = Community.builder()
                    .id(UUID.randomUUID().toString())
                    .humanReadableId(communityId)
                    .community(communityId)
                    .level(level)
                    .parent(parent)
                    .children(children)
                    .title("社区 " + communityId)   // 完全对齐 Python，你之后要改成中文标题也可以
                    .entityIds(row.getEntityIds())
                    .relationshipIds(row.getRelationshipIds())
                    .textUnitIds(row.getTextUnitIds())
                    .period(period)
                    .size(row.getEntityIds().size())
                    .summary(null)        // 后续社区报告生成再填
                    .metadata(null)
                    .createdAt(now)
                    .build();

            result.add(community);
        }

        return result;
    }

    /**
     * 展开后的节点记录，对应 DataFrame 中的一行: (community, level, parent, title)
     */
    @Value
    private static class CommunityNodeRecord {
        int communityId;
        int level;
        Integer parentId;
        String title;
    }

    /**
     * 聚合 key: (community, level, parent)，对应 Python groupby(["community_x","level_x","parent_x"])
     */
    @Value
    private static class CommunityLevelParentKey {
        int communityId;
        int level;
        Integer parentId;
    }

    /**
     * 聚合容器：关系 ID 集合 + text_unit_ids 集合。
     */
    @Value
    private static class Aggregation {
        Set<String> relationshipIds;
        Set<String> textUnitIds;
    }

    /**
     * 中间「社区行」结构，对应 all_grouped + entity_ids join 后的一行。
     */
    @Value
    private static class CommunityRow {
        int community;
        int level;
        Integer parent;
        List<String> entityIds;
        List<String> relationshipIds;
        List<String> textUnitIds;
    }
}
