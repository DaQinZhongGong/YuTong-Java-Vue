package com.yutong.system.log.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** 操作日志。设计来源: 57-完整DDL清单 sys_operation_log
 *  GA2-09-1: operatedTime 由 LocalDateTime 修正为 OffsetDateTime，对齐 DDL 的 timestamptz 类型。 */
@Getter
@Setter
@TableName("sys_operation_log")
public class SysOperationLog extends BaseEntity {
    private String operationType;
    private String module;
    private String bizType;
    private String bizId;
    private String content;
    private String beforeJson;
    private String afterJson;
    private String result;
    private String errorCode;
    private String traceId;
    private String operatorId;
    private String operatorName;
    private String ip;
    private String userAgent;
    private OffsetDateTime operatedTime;
}
