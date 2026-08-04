package com.yutong.sample.extsync.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建/更新同步任务请求。设计来源: 35-样例业务矩阵扩展设计 P2 外部接口同步。
 */
@Data
public class SaveExtSyncTaskRequest {

    @NotBlank
    @Size(max = 64)
    private String taskCode;

    @NotBlank
    @Size(max = 128)
    private String taskName;

    @NotBlank
    private String systemId;

    @Size(max = 512)
    private String description;

    @NotBlank
    @Size(max = 256)
    private String sourceApi;

    /** HTTP 方法: GET / POST, 默认 GET */
    private String httpMethod;

    /** 请求体模板 (POST 时使用) */
    private String requestTemplate;

    /** 业务键字段名, 默认 id */
    private String businessKeyField;

    /** 同步模式: FULL / INCREMENTAL, 默认 FULL */
    private String syncMode;

    /** 目标表 (预留) */
    private String targetTable;

    /** 定时表达式 (cron) */
    private String cronExpression;

    /** 状态: ACTIVE / DISABLED */
    private String status;
}
