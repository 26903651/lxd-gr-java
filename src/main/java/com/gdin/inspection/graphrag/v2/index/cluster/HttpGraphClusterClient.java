package com.gdin.inspection.graphrag.v2.index.cluster;

import cn.hutool.core.collection.CollectionUtil;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gdin.inspection.graphrag.config.properties.GraphProperties;
import com.gdin.inspection.graphrag.v2.models.Relationship;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class HttpGraphClusterClient implements GraphClusterClient {

    @Resource
    private RestTemplate restTemplate;

    @Resource
    private GraphProperties graphProperties;

    @Override
    public List<LeidenCluster> clusterGraph(
            List<Relationship> relationships,
            int maxClusterSize,
            boolean useLargestComponent,
            Integer seed
    ) {
        if (relationships == null || relationships.isEmpty()) {
            return Collections.emptyList();
        }

        List<EdgePayload> edges = relationships.stream()
                .map(rel -> {
                    if (rel.getSource() == null || rel.getTarget() == null) return null;
                    Double w = rel.getWeight() != null ? rel.getWeight() : 1.0;
                    return new EdgePayload(rel.getSource(), rel.getTarget(), w);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (edges.isEmpty()) {
            return Collections.emptyList();
        }

        ClusterRequest request = new ClusterRequest(
                edges,
                maxClusterSize,
                useLargestComponent,
                seed
        );

        String url = graphProperties.getLeiden().getBaseUrl() + "/cluster";

        ClusterResponse response = restTemplate.postForObject(
                url,
                request,
                ClusterResponse.class
        );

        if (response == null || response.getClusters() == null) {
            return Collections.emptyList();
        }

        return response.getClusters().stream()
                .filter(Objects::nonNull)
                .map(item -> LeidenCluster.builder()
                        .level(item.getLevel())
                        .communityId(item.getCommunityId())
                        .parentCommunityId(item.getParentId())
                        .nodeTitles(item.getTitles())
                        .build()
                )
                .collect(Collectors.toList());
    }

    /**
     * 发送给 Python 的单条边结构。
     */
    @Value
    @AllArgsConstructor
    private static class EdgePayload {
        String source;
        String target;
        Double weight;
    }

    /**
     * 发送给 Python 的请求结构。
     */
    @Value
    @AllArgsConstructor
    private static class ClusterRequest {
        List<EdgePayload> relationships;

        @JsonProperty("max_cluster_size")
        int maxClusterSize;

        @JsonProperty("use_lcc")
        boolean useLcc;

        Integer seed;
    }

    /**
     * 从 Python 收到的单个聚类项。
     */
    @Data
    public static class ClusterItem {

        private int level;

        @JsonProperty("community_id")
        private int communityId;

        @JsonProperty("parent_id")
        private int parentId;

        private List<String> titles;
    }

    /**
     * 从 Python 收到的整体响应。
     */
    @Data
    public static class ClusterResponse {
        private List<ClusterItem> clusters;
    }
}
