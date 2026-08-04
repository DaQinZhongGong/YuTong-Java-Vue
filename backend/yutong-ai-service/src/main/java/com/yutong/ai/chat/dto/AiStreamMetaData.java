package com.yutong.ai.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * SSE 流式 meta 事件数据。设计来源: contracts/openapi/openapi.yaml AiStreamMetaData (line 2200-2205)
 * <p>
 * 由服务端在流开始时推送一次，告知客户端本次回复使用的模型和场景。
 * 客户端收到 meta 后即可锁定 messageId（在事件信封中携带），后续 delta/citation/done 复用同一 messageId。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiStreamMetaData {

    /** 模型编码（如 mock-model、qwen2.5-72b 等） */
    private String modelCode;

    /** 场景（PLATFORM_QA / FIELD_SUGGEST / PAGE_GENERATE / SQL_EXPLAIN / OPS_DIAGNOSE） */
    private String scenario;
}
