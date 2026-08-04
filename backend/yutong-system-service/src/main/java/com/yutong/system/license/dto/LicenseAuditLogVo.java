package com.yutong.system.license.dto;

import java.time.OffsetDateTime;

/**
 * 授权审计日志 VO。设计来源: 70-商业授权与版本能力裁剪详设「授权运行数据模型 - sys_license_audit_log」。
 *
 * <p>GET /api/v1/license/audit-logs 端点返回的单条审计日志记录。
 * 字段对齐 {@link com.yutong.system.license.domain.SysLicenseAuditLog} 实体，
 * 但仅暴露管理端需要的字段（隐藏 id/tenantIdHash 等内部字段）。
 *
 * <p>GA2-L174 落地: 70 号文档「授权校验流程」管理端观察接口补齐。
 *
 * @param action      动作 LOAD/VERIFY/REFRESH/DENY/EXPIRE/QUOTA_EXCEEDED
 * @param licenseId   授权 ID（可空）
 * @param moduleCode  模块编码（可空）
 * @param quotaCode   额度编码（可空）
 * @param result      结果 SUCCESS/FAILURE/DENIED/WARN
 * @param errorCode   错误码（FAILURE/DENIED 时填写，可空）
 * @param userIdHash  哈希化用户标识
 * @param traceId     链路 ID
 * @param detailJson  详细信息 JSON（可空）
 * @param operatedTime 操作时间（业务时间）
 */
public record LicenseAuditLogVo(
        String action,
        String licenseId,
        String moduleCode,
        String quotaCode,
        String result,
        String errorCode,
        String userIdHash,
        String traceId,
        String detailJson,
        OffsetDateTime operatedTime
) {
}
