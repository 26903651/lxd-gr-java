package com.gdin.inspection.graphrag.req.milvus;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@NoArgsConstructor
@SuperBuilder
@Data
@Schema(description = "Milvus混合查询请求")
public class MilvusHybridSearchReq {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "集合名称", example = "chat_memory")
    private String collectionName;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "查询问题", example = "童话故事")
    private String query;

    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "查询关键字", example = "童话")
    private List<String> keywords;

    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "查询条数", example = "10")
    private Integer topK;

    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "过滤条件", example = "metadata[\"type\"] == \"USER\"")
    private String filter;

    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "输出字段")
    private List<String> outputFields;

    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "最小分值")
    private Float minScore;

    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "语义匹配权重")
    private Float denseWeight;

    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "关键字匹配权重")
    private Float sparseWeight;
}
