package com.yutong.workflow.dto;

import lombok.Data;

import java.util.Map;

/**
 * 任务办理通过请求。
 * <p>校验: 任务必须 PENDING 状态 + 当前用户是 assigneeId 或在 candidateGroup 中。
 * <p>办理后: 任务 COMPLETED, 流程前进到下一节点 (UserTask/ExclusiveGateway/EndEvent)。
 */
@Data
public class CompleteTaskRequest {

    /** 办理意见 (审批通过说明) */
    private String opinion;

    /** 办理表单数据 (JSON 键值对, 可选) */
    private Map<String, Object> formData;

    /** 流程变量更新 (可选, 后续节点表达式可用) */
    private Map<String, Object> variableUpdates;
}
