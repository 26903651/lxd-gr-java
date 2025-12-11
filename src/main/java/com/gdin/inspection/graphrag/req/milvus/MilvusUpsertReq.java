package com.gdin.inspection.graphrag.req.milvus;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@NoArgsConstructor
@SuperBuilder
@Data
@Schema(description = "Milvus操作实体数据请求")
public class MilvusUpsertReq {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "集合名称", example = "chat_memory")
    private String collectionName;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "id", example = "1")
    private Long id;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "更新或插入的字段集合")
    private Map<String, Object> valueMap;
}
