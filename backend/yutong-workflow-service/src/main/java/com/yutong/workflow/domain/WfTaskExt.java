package com.yutong.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 工作流用户任务扩展。设计来源: 41-工作流与BPMN引擎设计。
 *
 * <p>GA2-44 L1+L2 轻量自研引擎实现: 引擎任务 ID 与主键一致 (ULID)。
 * 状态机:
 * <ul>
 *   <li>PENDING -> COMPLETED (办理通过)</li>
 *   <li>PENDING -> REJECTED (驳回, 实例回到上一节点或终止)</li>
 *   <li>PENDING -> DELEGATED (委派, 任务回到原办理人, 委派人办理后回到原办理人)</li>
 *   <li>PENDING -> TRANSFERRED (转办, 任务转到新人, 原办理人不再参与)</li>
 *   <li>PENDING -> CANCELLED (实例终止时, 待办任务全部取消)</li>
 * </ul>
 *
 * <p>assignee_id 与 actual_handler_id 区分:
 * <ul>
 *   <li>assignee_id: 任务指派的办理人 (委派场景下仍是原办理人)</li>
 *   <li>actual_handler_id: 实际办理人 (委派场景下是被委派人)</li>
 * </ul>
 */
@Getter
@Setter
@TableName("wf_task_ext")
public class WfTaskExt extends BaseEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_DELEGATED = "DELEGATED";
    public static final String STATUS_TRANSFERRED = "TRANSFERRED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    public static final String TASK_TYPE_APPROVAL = "APPROVAL";
    public static final String TASK_TYPE_NOTIFICATION = "NOTIFICATION";
    public static final String TASK_TYPE_FILL_FORM = "FILL_FORM";

    public static final String DELEGATE_TYPE_DELEGATE = "DELEGATE";
    public static final String DELEGATE_TYPE_TRANSFER = "TRANSFER";

    /** 引擎任务 ID (轻量引擎使用 ULID, 与主键一致) */
    private String engineTaskId;

    private String instanceId;
    private String processKey;
    private String bizType;
    private String bizId;
    private String bizNo;

    /** BPMN 节点 ID (XML 中的 task id) */
    private String nodeId;
    private String taskName;

    /** 办理人 (单人或多人逗号分隔) */
    private String assigneeId;
    /** 候选组 (角色 code 或部门 code) */
    private String candidateGroup;

    /** PENDING / COMPLETED / REJECTED / DELEGATED / TRANSFERRED / CANCELLED */
    private String taskStatus;
    /** 任务类型 APPROVAL/NOTIFICATION/FILL_FORM */
    private String taskType;

    /** 截止时间 (SLA) */
    private OffsetDateTime dueTime;

    /** 办理表单快照 (JSON 字符串) */
    private String formDataJson;
    /** 办理意见 */
    private String opinion;

    /** 委派/转办标记 */
    private String delegateType;
    /** 委派/转办目标人 */
    private String delegateToUserId;

    /** 任务到达该节点的时间 */
    private OffsetDateTime createTime;
    /** 任务办理时间 */
    private OffsetDateTime completeTime;
    /** 实际办理人 (委派场景下与 assigneeId 不同) */
    private String actualHandlerId;
}
