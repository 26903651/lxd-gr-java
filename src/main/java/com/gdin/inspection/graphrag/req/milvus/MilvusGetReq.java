package com.gdin.inspection.graphrag.req.milvus;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@NoArgsConstructor
@SuperBuilder
@Data
@Schema(description = "Milvus获取实体请求")
public class MilvusGetReq {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "集合名称", example = "chat_memory")
    private String collectionName;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "id列表", example = "1,2,3,4")
    private List<Object> ids;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "输出字段")
    private List<String> outputFields;
}
