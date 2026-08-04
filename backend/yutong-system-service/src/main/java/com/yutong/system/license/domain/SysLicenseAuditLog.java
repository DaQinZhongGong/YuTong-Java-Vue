package com.yutong.system.license.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 授权审计日志实体（不可变审计表）。设计来源: 70-商业授权与版本能力裁剪详设「授权运行数据模型」、
 * V024__init_license_tables.sql
 *
 * <p>记录所有直连 API 拒绝、授权校验、额度超限等事件。
 *
 * <p><b>重要:</b> 本表为不可变审计表，DDL 中<b>没有</b> {@code deleted}/{@code version} 字段，
 * 因此本类<b>不继承</b> {@link com.yutong.infra.persistence.BaseEntity}，
 * 手动声明 id/tenantId/createdTime 字段，避免 MyBatis-Plus 乐观锁/逻辑删除策略尝试映射不存在的列。
 *
 * <p>GA2-L170 落地: 关闭 DEV-L170-002 偏差。
 */
@Getter
@Setter
@TableName("sys_license_audit_log")
public class SysLicenseAuditLog {

    /** 主键 ULID，由 service 显式生成（与 BaseEntity.@TableId(INPUT) 等价） */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 租户 ID（明文，便于租户内审计查询） */
    private String tenantId;

    /** 动作 LOAD/VERIFY/REFRESH/DENY/EXPIRE/QUOTA_EXCEEDED */
    private String action;

    /** 授权 ID（可空） */
    private String licenseId;

    /** 模块编码（可空），如 lowcode/ai/report/workflow/datasource/plugin */
    private String moduleCode;

    /** 额度编码（可空） */
    private String quotaCode;

    /** 结果 SUCCESS/FAILURE/DENIED/WARN */
    private String result;

    /** 错误码（FAILURE/DENIED 时填写，可空） */
    private String errorCode;

    /** 哈希化用户标识，避免审计泄露客户信息 */
    private String userIdHash;

    /** 哈希化租户标识 */
    private String tenantIdHash;

    /** 链路 ID，关联 sys_operation_log */
    private String traceId;

    /** 详细信息 JSON（可空） */
    private String detailJson;

    /** 操作时间（业务时间，默认 now()） */
    private OffsetDateTime operatedTime;

    /** 创建时间（落库时间） */
    private OffsetDateTime createdTime;
}
