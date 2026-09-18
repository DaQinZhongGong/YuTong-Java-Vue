package com.yutong.system.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 租户保存请求 (创建/更新共用)。
 * 落点: 业界同类实现 SysTenantBo + ADR 0004 P2-F。
 *
 * <p>约束: tenantCode 仅创建时有效, 更新时忽略 (编码不可改, 见 SysTenantService)。
 */
@Data
public class SaveTenantRequest {

    private String id; // 编辑时填

    @NotBlank(message = "租户编码不能为空")
    @Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,31}$", message = "租户编码必须小写字母/数字开头, 只能含小写字母/数字/连字符, 2-32 字符")
    private String tenantCode;

    @NotBlank(message = "企业名称不能为空")
    private String companyName;

    private String contactUserName;

    private String contactPhone;

    private String licenseNumber;

    private String address;

    private String domain;

    private String intro;

    /** 绑定套餐 ID, 可空 */
    private String packageId;

    /** 套餐过期时间, null = 不过期 */
    private LocalDateTime expireTime;

    /** 账号数量上限, -1 = 不限制 */
    private Long accountCount;

    /** 状态: NORMAL/DISABLED (更新时可改, 创建统一 NORMAL) */
    private String status;
}
