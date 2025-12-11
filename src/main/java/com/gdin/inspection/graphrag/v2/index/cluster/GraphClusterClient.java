package com.gdin.inspection.graphrag.v2.index.cluster;

import com.gdin.inspection.graphrag.v2.models.Relationship;

import java.util.List;

/**
 * 图聚类客户端接口，对应 Python 的:
 *   graph = create_graph(relationships, edge_attr=["weight"])
 *   clusters = cluster_graph(graph, max_cluster_size, use_lcc, seed=seed)
 *
 * 具体实现里可以通过 HTTP 把关系表发给 Python 服务，让它跑 networkx + Leiden，
 * 返回 (level, community, parent, [title_list])。
 */
public interface GraphClusterClient {

    List<LeidenCluster> clusterGraph(
            List<Relationship> relationships,
            int maxClusterSize,
            boolean useLargestComponent,
            Integer seed
    );
}
