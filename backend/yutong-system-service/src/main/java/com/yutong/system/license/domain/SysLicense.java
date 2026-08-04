package com.yutong.system.license.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 授权主表实体。设计来源: 70-商业授权与版本能力裁剪详设「授权运行数据模型」、V024__init_license_tables.sql
 *
 * <p>保存 License 文件解析后的关键字段，支持离线授权和审计。
 * 第一版不实现 License Server，本表通过管理员手动上传 License 文件后写入。
 *
 * <p>GA2-L170 落地: 关闭 DEV-L170-002 偏差（3 张授权数据表未落地）。
 * 与 {@link SysLicenseUsage}、{@link SysLicenseAuditLog} 共同构成授权运行时数据模型。
 */
@Getter
@Setter
@TableName("sys_license")
public class SysLicense extends BaseEntity {

    /** 授权 ID（业务唯一键，如 LIC-2026-0001），来自 License 文件 licenseId 字段 */
    private String licenseId;

    /** 版本枚举 Community/Professional/Enterprise/Industry */
    private String edition;

    /** 授权主体（客户/公司名） */
    private String subject;

    /** 部署实例 ID，绑定机器或集群 */
    private String deploymentId;

    /** License schema 版本，支持大版本升级兼容 */
    private String licenseSchemaVersion;

    /** 租户数、用户数、AI 月额度等限制（JSON 字符串，原 jsonb 字段） */
    private String limitsJson;

    /** 授权模块列表（JSON 数组字符串，原 jsonb 字段） */
    private String modulesJson;

    /** 到期时间（含时区） */
    private OffsetDateTime expireTime;

    /** 服务端私钥签名，运行时只用公钥验证 */
    private String signature;

    /** 状态 ACTIVE/EXPIRED/INVALID/REVOKED */
    private String status;

    /** 最近校验时间，用于篡改检测和定时校验 */
    private OffsetDateTime lastVerifiedTime;
}
