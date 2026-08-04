package com.yutong.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 授权信息 DTO。{@link com.yutong.api.facade.LicenseService#current()} 的返回值。
 *
 * <p>设计来源: 70-商业授权与版本能力裁剪详设「License 文件结构」「授权校验接口」。
 *
 * <p>字段对齐 sys_license 表 + License 文件结构（licenseId/subject/edition/deploymentId/limits/modules/expireTime/signature）。
 * 适用于跨模块查询当前授权状态（如低代码/AI/报表模块判断是否启用）。
 *
 * <p>降级模式: 当 sys_license 表无记录时（第一版未实现 License Server），
 * {@link com.yutong.api.facade.LicenseService} 实现应返回 {@code fallbackCommunity=true} 的默认 Community 实例，
 * 保证基础能力（基础工程/样例业务/低代码基础）可用，仅商业模块被拦截。
 *
 * @param licenseId          授权 ID（如 LIC-2026-0001），降级模式为 null
 * @param edition            版本枚举 Community/Professional/Enterprise/Industry，降级为 Community
 * @param subject            授权主体（客户/公司名），降级为 "Community Default"
 * @param deploymentId       部署实例 ID，降级为 null
 * @param licenseSchemaVersion License schema 版本，降级为 "1.0.0"
 * @param expireTime         到期时间（含时区），降级为 null（视为永久）
 * @param status             状态 ACTIVE/EXPIRED/INVALID/REVOKED，降级为 ACTIVE
 * @param modules            授权模块列表（如 system/sample/lowcode/ai/report），降级为基础模块集
 * @param limitsJson         限制 JSON 字符串（如 {"tenantCount":10,"userCount":500,"aiMonthlyQuota":1000000}）
 * @param signature          服务端私钥签名，降级为 null
 * @param fallbackCommunity  true 表示降级模式（sys_license 表无记录），false 表示已加载正式 License
 */
public record LicenseInfo(
        String licenseId,
        String edition,
        String subject,
        String deploymentId,
        String licenseSchemaVersion,
        OffsetDateTime expireTime,
        String status,
        List<String> modules,
        String limitsJson,
        String signature,
        boolean fallbackCommunity
) {
}
