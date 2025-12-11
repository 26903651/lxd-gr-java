package com.gdin.inspection.graphrag.req.milvus;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@NoArgsConstructor
@SuperBuilder
@Data
@Schema(description = "Milvus查询实体请求")
public class MilvusQueryReq {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "集合名称", example = "chat_memory")
    private String collectionName;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "过滤条件", example = "metadata[\"type\"] == \"USER\"")
    private String filter;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "输出字段")
    private List<String> outputFields;

    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "查询条数", example = "10")
    private Integer limit;
}
