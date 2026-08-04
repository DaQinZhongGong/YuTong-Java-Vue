package com.yutong.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 终止流程实例请求。
 * <p>终止后: 实例 TERMINATED, 所有 PENDING 任务 CANCELLED, 通过 business_callback_url 回调业务状态。
 */
@Data
public class TerminateInstanceRequest {

    @NotBlank
    private String reason;
}
