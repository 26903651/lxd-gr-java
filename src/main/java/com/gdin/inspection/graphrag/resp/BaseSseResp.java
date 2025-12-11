package com.gdin.inspection.graphrag.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;

@NoArgsConstructor
@SuperBuilder
@Data
@Schema(description = "基础的SSE响应体")
public class BaseSseResp implements Serializable {
    public static final String STATE_BEGIN = "BEGIN";
    public static final String STATE_ASSISTANT_GEN = "ASSISTANT_GEN";
    public static final String STATE_PROCESSING = "PROCESSING";
    public static final String STATE_HEARTBEAT = "HEARTBEAT";
    public static final String STATE_END = "END";
    public static final String STATE_ERROR = "ERROR";

    @Schema(description = "对应id")
    private String id;

    @Schema(description = "状态", example = "BEGIN | ASSISTANT_GEN | PROCESSING | HEARTBEAT | END | ERROR")
    private String state;

    @Schema(description = "响应文本内容", example = "正在处理中...")
    private String text;

    @Schema(description = "响应数据", example = "{\"aaa\"=\"123\"}")
    private String data;
}
