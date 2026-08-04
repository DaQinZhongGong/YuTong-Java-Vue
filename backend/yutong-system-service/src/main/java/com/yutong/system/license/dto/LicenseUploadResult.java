package com.yutong.system.license.dto;

/**
 * License 上传结果 VO。设计来源: 70-商业授权与版本能力裁剪详设「授权校验流程 - 手动刷新」。
 *
 * <p>POST /api/v1/license/upload 端点返回值，包含新加载的 License 摘要信息。
 *
 * <p>GA2-L174 落地: 70 号文档「授权校验流程」4 时机之「手动刷新」补齐。
 *
 * @param licenseId    新加载的授权 ID
 * @param edition      版本枚举
 * @param subject      授权主体
 * @param expireTime   到期时间（ISO 格式字符串）
 * @param modules      授权模块列表
 * @param previousRevoked 是否撤销了前一个 ACTIVE License（true=旧 License 已改为 REVOKED）
 */
public record LicenseUploadResult(
        String licenseId,
        String edition,
        String subject,
        String expireTime,
        java.util.List<String> modules,
        boolean previousRevoked
) {
}
