package com.yutong.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 启动流程实例请求。设计来源: 41 号文档 API 契约 POST /api/v1/workflow/instances/start。
 * <p>idempotencyKey + version 通过 Header 传递 (Idempotency-Key / If-Match), 不在 body 中。
 */
@Data
public class StartProcessRequest {

    @NotBlank
    private String processKey;

    @NotBlank
    private String bizType;

    @NotBlank
    private String bizId;

    /** 业务单号展示 (可选, 用于任务列表展示) */
    private String bizNo;

    /** 流程变量 (启动时传入, 后续任务办理时更新) */
    private Map<String, Object> variables;

    /** 业务回调 URL (流程结束时回调业务状态, 可选) */
    private String businessCallbackUrl;
}
