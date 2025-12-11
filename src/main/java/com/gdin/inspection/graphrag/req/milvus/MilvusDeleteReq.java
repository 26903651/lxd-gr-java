package com.gdin.inspection.graphrag.req.milvus;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@NoArgsConstructor
@SuperBuilder
@Data
@Schema(description = "Milvus删除实体数据请求")
public class MilvusDeleteReq {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "集合名称", example = "chat_memory")
    private String collectionName;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "文档id")
    private String documentId;

}
