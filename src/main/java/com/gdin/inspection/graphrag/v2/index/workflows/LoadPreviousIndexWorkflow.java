package com.gdin.inspection.graphrag.v2.index.workflows;

import com.gdin.inspection.graphrag.v2.models.*;
import com.gdin.inspection.graphrag.v2.storage.GraphRagIndexStorage;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class LoadPreviousIndexWorkflow {

    @Resource
    private GraphRagIndexStorage storage;

    public Result run(int scope) {
        return run(scope, true, true, true, true, true, true);
    }

    public Result run(int scope, boolean textUnits, boolean entities, boolean relationships, boolean communities, boolean communityReports, boolean covariates) {
        log.info("开始加载旧索引");

        List<TextUnit> oldTextUnits = textUnits ? storage.loadTextUnits(scope) : null;
        List<Entity> oldEntities = entities ? storage.loadEntities(scope) : null;
        List<Relationship> oldRelationships = relationships ? storage.loadRelationships(scope) : null;
        List<Community> oldCommunities = communities ? storage.loadCommunities(scope) : null;
        List<CommunityReport> oldReports = communityReports ? storage.loadCommunityReports(scope) : null;
        List<Covariate> oldCovariates = covariates ? storage.loadCovariates(scope) : null;

        log.info(
                "已加载旧索引: textUnits={}, entities={}, relationships={}, communities={}, reports={}, covariates={}",
                oldTextUnits == null ? 0 : oldTextUnits.size(),
                oldEntities == null ? 0 : oldEntities.size(),
                oldRelationships == null ? 0 : oldRelationships.size(),
                oldCommunities == null ? 0 : oldCommunities.size(),
                oldReports == null ? 0 : oldReports.size(),
                oldCovariates == null ? 0 : oldCovariates.size()
        );

        return new Result(oldTextUnits, oldEntities, oldRelationships, oldCommunities, oldReports, oldCovariates);
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Result {
        private List<TextUnit> textUnits;
        private List<Entity> entities;
        private List<Relationship> relationships;
        private List<Community> communities;
        private List<CommunityReport> communityReports;
        private List<Covariate> covariates;
    }
}
