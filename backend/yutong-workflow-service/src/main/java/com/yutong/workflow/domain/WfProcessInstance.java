package com.yutong.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 工作流流程实例扩展。设计来源: 41-工作流与BPMN引擎设计。
 *
 * <p>GA2-44 L1+L2 轻量自研引擎实现: 引擎实例 ID 与主键一致 (ULID)。
 * 状态机: RUNNING -> COMPLETED (正常结束) / TERMINATED (强制终止) / SUSPENDED (暂停)。
 *
 * <p>与样例业务衔接: biz_type + biz_id + biz_no 关联样例业务 (如 biz_request)。
 * 流程结束后通过 business_callback_url 回调业务状态 (设计已落地, 实际回调在 ServiceTaskExecutor 中执行)。
 */
@Getter
@Setter
@TableName("wf_process_instance")
public class WfProcessInstance extends BaseEntity {

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_TERMINATED = "TERMINATED";
    public static final String STATUS_SUSPENDED = "SUSPENDED";

    /** 引擎实例 ID (轻量引擎使用 ULID, 与主键一致) */
    private String engineInstanceId;

    private String processDefinitionId;
    private String processKey;

    /** 业务类型 + 业务主键 + 业务单号展示 */
    private String bizType;
    private String bizId;
    private String bizNo;

    /** 发起人 */
    private String starterId;

    /** RUNNING / COMPLETED / TERMINATED / SUSPENDED */
    private String instanceStatus;

    /** 当前节点名称摘要 (逗号分隔, 多任务并行时多个) */
    private String currentNodeNames;

    /** 流程变量快照 (JSON 字符串) */
    private String variables;

    /** 业务回调 URL (流程结束时回调) */
    private String businessCallbackUrl;

    /** 终止原因 (TERMINATED 时填写) */
    private String terminateReason;

    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
}
