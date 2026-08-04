package com.yutong.lowcode.plugin.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 插件审计日志实体（45 号文档「插件与模板生态设计」）。
 * 记录插件操作审计轨迹：安装/卸载/配置等。
 * 结果: SUCCESS / FAILED
 */
@Getter
@Setter
@TableName("plugin_audit_log")
public class PluginAuditLog extends BaseEntity {

    // ===== result 操作结果 =====
    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_FAILED = "FAILED";

    /** 操作的插件编码 */
    private String pluginCode;

    /** 操作的插件版本 */
    private String pluginVersion;

    /** 操作人用户 ID */
    private String operatorId;

    /** 使用的权限码 */
    private String permissionCode;

    /** 业务类型（安装/卸载/配置等） */
    private String bizType;

    /** 业务 ID（安装记录 ID 等） */
    private String bizId;

    /** 链路追踪 ID */
    private String traceId;

    /** SUCCESS / FAILED */
    private String result;

    /** 错误码（失败时记录） */
    private String errorCode;
}
