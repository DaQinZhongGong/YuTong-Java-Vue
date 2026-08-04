package com.yutong.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 任务驳回请求。
 * <p>驳回策略:
 * <ul>
 *   <li>当前为首个 UserTask: 实例直接 TERMINATED, 业务状态由 business_callback_url 回写为 REJECTED</li>
 *   <li>当前非首个 UserTask: 流程回退到上一 UserTask (重新生成 PENDING 任务), 实例保持 RUNNING</li>
 * </ul>
 */
@Data
public class RejectTaskRequest {

    @NotBlank
    private String opinion;

    /** 流程变量更新 (可选) */
    private Map<String, Object> variableUpdates;
}
