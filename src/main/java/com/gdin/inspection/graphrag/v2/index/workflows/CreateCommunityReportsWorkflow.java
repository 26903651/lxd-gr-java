package com.gdin.inspection.graphrag.v2.index.workflows;

import cn.hutool.core.collection.CollectionUtil;
import com.gdin.inspection.graphrag.v2.index.opertation.SummarizeCommunitiesOperation;
import com.gdin.inspection.graphrag.v2.models.Community;
import com.gdin.inspection.graphrag.v2.models.CommunityReport;
import com.gdin.inspection.graphrag.v2.models.Entity;
import com.gdin.inspection.graphrag.v2.models.Relationship;
import com.gdin.inspection.graphrag.v2.models.TextUnit;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 等价于 Python 的 create_community_reports.create_community_reports：
 *
 * - 输入：最终的 entities / relationships / communities / text_units
 * - 内部：调用 SummarizeCommunitiesOperation 做社区级报告生成
 * - 输出：CommunityReport 列表（已经带 id / human_readable_id / period / size 等）
 */
@Slf4j
@Service
public class CreateCommunityReportsWorkflow {

    @Resource
    private SummarizeCommunitiesOperation summarizeCommunitiesOperation;

    /**
     * 生成所有社区的报告。
     *
     * @param communities      已完成 finalize 的社区列表（CreateCommunitiesOperation 的结果）
     * @param entities         已完成 finalize 的实体列表
     * @param relationships    已完成 finalize 的关系列表
     * @param textUnits        已完成 finalize 的 TextUnit 列表
     * @param maxReportLength  社区报告最大长度（对应 Python 中的 max_report_length）
     */
    public List<CommunityReport> run(
            List<Community> communities,
            List<Entity> entities,
            List<Relationship> relationships,
            List<TextUnit> textUnits,
            Integer maxReportLength
    ) {
        if (CollectionUtil.isEmpty(communities)) throw new IllegalStateException("communities 不能为空");
        if (CollectionUtil.isEmpty(entities)) throw new IllegalStateException("entities 不能为空");
        if (CollectionUtil.isEmpty(relationships)) throw new IllegalStateException("relationships 不能为空");
        if (CollectionUtil.isEmpty(textUnits)) throw new IllegalStateException("textUnits 不能为空");
        maxReportLength = maxReportLength == null ? 2500 : maxReportLength;

        log.info(
                "开始生成社区报告：communities={}, entities={}, relationships={}, textUnits={}",
                communities.size(),
                entities.size(),
                relationships.size(),
                textUnits.size()
        );

        // 这里直接委托给 SummarizeCommunitiesOperation，
        // 它内部会按照 Python summarize_communities 的逻辑：
        // - 按 level 从高到低生成报告
        // - 支持子社区报告向上传递
        return summarizeCommunitiesOperation.summarizeCommunities(
                communities,
                entities,
                relationships,
                textUnits,
                maxReportLength
        );
    }
}
