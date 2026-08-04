package com.yutong.sample.extsync.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 同步任务定义。设计来源: 35-样例业务矩阵扩展设计 P2 外部接口同步。
 *
 * <p>描述一条从外部系统拉取数据并写入本地的同步规则。
 * <p>同步模式: FULL (全量) / INCREMENTAL (增量, 基于 last_sync_time 水位线)。
 * <p>状态: ACTIVE / DISABLED。
 */
@Getter
@Setter
@TableName("ext_sync_task")
public class ExtSyncTask extends BaseEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

    public static final String METHOD_GET = "GET";
    public static final String METHOD_POST = "POST";

    public static final String MODE_FULL = "FULL";
    public static final String MODE_INCREMENTAL = "INCREMENTAL";

    /** 任务编码 (租户内唯一) */
    private String taskCode;
    /** 任务名称 */
    private String taskName;
    /** 关联外部系统 ID */
    private String systemId;
    /** 描述 */
    private String description;
    /** 源 API 路径 (相对 endpoint) */
    private String sourceApi;
    /** HTTP 方法: GET / POST */
    private String httpMethod;
    /** 请求体模板 (POST 时使用, 支持 ${yesterday}/${today} 占位符) */
    private String requestTemplate;
    /** 业务键字段名 (用于幂等写入) */
    private String businessKeyField;
    /** 同步模式: FULL / INCREMENTAL */
    private String syncMode;
    /** 目标表 (预留) */
    private String targetTable;
    /** 定时表达式 (cron, 留空表示仅手动触发) */
    private String cronExpression;
    /** 上次同步时间 (INCREMENTAL 水位线) */
    private OffsetDateTime lastSyncTime;
    /** 状态: ACTIVE / DISABLED */
    private String status;
}
