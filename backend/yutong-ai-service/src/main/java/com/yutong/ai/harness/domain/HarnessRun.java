package com.yutong.ai.harness.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.ai.harness.enums.RunStatus;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * Coding Harness 运行实例。表: ai_harness_run（V059）。
 */
@Getter
@Setter
@TableName("ai_harness_run")
public class HarnessRun extends BaseEntity {

    public static final String STATUS_QUEUED = RunStatus.QUEUED.name();
    public static final String STATUS_RUNNING = RunStatus.RUNNING.name();
    public static final String STATUS_WAITING_FOR_APPROVAL = RunStatus.WAITING_FOR_APPROVAL.name();
    public static final String STATUS_WAITING_FOR_INPUT = RunStatus.WAITING_FOR_INPUT.name();
    public static final String STATUS_COMPLETED = RunStatus.COMPLETED.name();
    public static final String STATUS_FAILED = RunStatus.FAILED.name();
    public static final String STATUS_CANCELLED = RunStatus.CANCELLED.name();

    private String sessionId;
    private String userId;
    private String status;
    private String requirement;
    private String permissionMode;
    /** 创建 Run 时的会话权限 revision 快照，审批 claim 必须一致 */
    private Long permissionRevision;
    private String budgetJson;
    private String usageJson;
    private String planJson;
    private Integer iteration;
    private Integer toolCallCount;
    private Boolean cancelRequested;
    private String idempotencyKey;
    private String errorMessage;
    private Long revision;

    public RunStatus statusEnum() {
        return status == null ? null : RunStatus.valueOf(status);
    }
}
