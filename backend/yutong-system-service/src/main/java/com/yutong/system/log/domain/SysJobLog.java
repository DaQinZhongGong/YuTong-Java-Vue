package com.yutong.system.log.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** 定时任务日志。设计来源: 57-完整DDL清单 sys_job_log
 *  start_time/end_time 列为 TIMESTAMPTZ，使用 OffsetDateTime 避免 PgResultSet 转换异常。 */
@Getter
@Setter
@TableName("sys_job_log")
public class SysJobLog extends BaseEntity {
    private String jobCode;
    private String jobName;
    private String bizType;
    private String bizId;
    private String triggerType;
    private String status;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private Long durationMs;
    private String errorMessage;
    private String traceId;
}
