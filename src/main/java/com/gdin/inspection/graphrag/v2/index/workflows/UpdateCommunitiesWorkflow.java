package com.gdin.inspection.graphrag.v2.index.workflows;

import com.gdin.inspection.graphrag.v2.index.update.IncrementalCommunities;
import com.gdin.inspection.graphrag.v2.models.Community;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class UpdateCommunitiesWorkflow {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Result {
        private List<Community> mergedCommunities;
        private Map<Integer, Integer> communityIdMapping;
    }

    public Result run(List<Community> oldCommunities, List<Community> deltaCommunities) {
        IncrementalCommunities.Result r = IncrementalCommunities.updateAndMergeCommunities(oldCommunities, deltaCommunities);
        return new Result(r.getMergedCommunities(), r.getCommunityIdMapping());
    }
}
