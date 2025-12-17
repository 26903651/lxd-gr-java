package com.gdin.inspection.graphrag.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "gdin.ai.graph")
@Component
public class GraphProperties implements Serializable {
    private CollectionNames collectionNames = new CollectionNames();
    private Index index = new Index();

    private Leiden leiden = new Leiden();

    @Data
    public static class CollectionNames implements Serializable {
        private String entityCollectionName = "GRAPH_RAG_ENTITY";
        private String relationshipCollectionName = "GRAPH_RAG_RELATIONSHIP";
        private String communityCollectionName = "GRAPH_RAG_COMMUNITY";
        private String communityReportCollectionName = "GRAPH_RAG_COMMUNITY_REPORT";
        private String covariateCollectionName = "GRAPH_RAG_COVARIATE";
        private String contentCollectionName = "AP_KNOWLEDGE_CONTENT_DEV";
    }

    @Data
    public static class Index implements Serializable {
        private Standard standard = new Standard();

        @Data
        public static class Standard implements Serializable {
            // =============== extract_graph ================
            // 补抽轮数（0=不补抽）
            private Integer maxGleanings = 2;
            // 输出协议
            private String tupleDelimiter = "<|>";
            private String recordDelimiter = "##";
            private String completionDelimiter = "<|COMPLETE|>";
            // 覆盖抽取提示词（最好别乱动）
            private String extractionPrompt;
            // 实体类型
            private List<String> entityTypes = List.of("组织", "人员", "地理位置", "事件", "时间");
            // 摘要长度控制
            private Integer entitySummaryMaxWords = 200;
            private Integer relationshipSummaryMaxWords = 200;

            // =============== extract_covariates ================
            // 是否启用 claims/covariates 抽取
            private Boolean claimsEnabled = true;
            // 抽取的业务定义
            private String claimsDescription = "任何可能与信息发现相关的主张或事实";
            // 补抽轮数（0=不补抽）
            private Integer claimsMaxGleanings = 2;
            // 输出协议
            private String claimsTupleDelimiter = "<|>";
            private String claimsRecordDelimiter = "##";
            private String claimsCompletionDelimiter = "<|COMPLETE|>";
            // 实体类型
            private List<String> claimsEntityTypes = List.of("组织", "人员", "地理位置", "事件", "时间");
            // 覆盖抽取提示词（最好别乱动）
            private String claimsExtractionPrompt;

            // =============== create_communities ================
            // 聚类参数
            private Integer maxClusterSize = 50;
            private Boolean useLcc = true;
            private Integer clusterSeed = 42;

            // =============== create_community_reports ================
            // 社区报告长度
            private Integer maxReportLength = 1200;
        }
    }

    @Data
    public static class Leiden implements Serializable {
        private String baseUrl;
    }
}
