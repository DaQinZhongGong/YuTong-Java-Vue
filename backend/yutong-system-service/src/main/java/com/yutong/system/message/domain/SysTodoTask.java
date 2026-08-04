package com.yutong.system.message.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** 轻量待办任务。设计来源: 57-完整DDL清单 sys_todo_task */
@Getter
@Setter
@TableName("sys_todo_task")
public class SysTodoTask extends BaseEntity {
    /** 待办类型: APPROVAL/HANDLE */
    private String todoType;
    /** 业务类型(biz_request等) */
    private String bizType;
    /** 业务ID */
    private String bizId;
    /** 标题 */
    private String title;
    /** 办理人ID */
    private String assigneeId;
    /** 待办状态: PENDING/DONE/CANCELLED */
    private String todoStatus;
    /** 优先级: NORMAL/HIGH/URGENT */
    private String priority;
    /** 到期时间 */
    private OffsetDateTime dueTime;
    /** 完成时间 */
    private OffsetDateTime completedTime;
    /** 来源事件ID */
    private String sourceEventId;
}
