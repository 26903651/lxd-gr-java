package com.gdin.inspection.graphrag.v2.index.workflows;

import cn.hutool.core.collection.CollectionUtil;
import com.gdin.inspection.graphrag.v2.index.opertation.CreateCommunityReportsOperation;
import com.gdin.inspection.graphrag.v2.index.strategy.CommunityReportsStrategy;
import com.gdin.inspection.graphrag.v2.models.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class CreateCommunityReportsWorkflow {

    @Resource
    private CreateCommunityReportsOperation createCommunityReportsOperation;

    public List<CommunityReport> run(
            List<Entity> entities,
            List<Relationship> relationships,
            List<Community> communities,
            List<Covariate> covariates,
            Integer maxContextTokens,
            Integer maxReportLength,
            Integer concurrentRequests
    ) throws Exception {
        if (CollectionUtil.isEmpty(entities)) throw new IllegalStateException("entities 不能为空");
        if (CollectionUtil.isEmpty(communities)) throw new IllegalStateException("communities 不能为空");

        maxContextTokens = maxContextTokens == null ? 8000 : maxContextTokens;
        maxReportLength = maxReportLength == null ? 2000 : maxReportLength;
        concurrentRequests = concurrentRequests == null ? 5 : concurrentRequests;

        log.info(
                "开始生成社区报告：entities={}, relationships={}, communities={}, covariates={}, maxContextTokens={}, maxReportLength={}, concurrentRequests={}",
                entities.size(),
                CollectionUtil.isEmpty(relationships) ? 0 : relationships.size(),
                communities.size(),
                CollectionUtil.isEmpty(covariates) ? 0 : covariates.size(),
                maxContextTokens,
                maxReportLength,
                concurrentRequests
        );

        CommunityReportsStrategy strategy = CommunityReportsStrategy.builder()
                .maxContextTokens(maxContextTokens)
                .maxReportLength(maxReportLength)
                .concurrentRequests(concurrentRequests)
                .build();

        List<CommunityReport> reports = createCommunityReportsOperation.createCommunityReports(
                entities,
                relationships,
                communities,
                covariates,
                strategy
        );

        log.info("community reports done, count={}", reports.size());
        return reports;
    }
}
