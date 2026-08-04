package com.yutong.auth.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 应急管理员实体。设计来源: 32-企业级权限与租户接入方案 auth_emergency_admin
 * 
 * <p>用于 IdP 故障时的紧急访问。生产环境必须准备最少两个受控应急管理员身份，
 * 凭据放入外部 Secret 管理，默认禁用或强审计，仅在 IdP 故障和明确审批后启用。
 * 
 * <p>状态常量:
 * <ul>
 *   <li>DISABLED - 禁用（默认）</li>
 *   <li>ENABLED - 已启用</li>
 *   <li>IN_USE - 使用中</li>
 * </ul>
 */
@Getter
@Setter
@TableName("auth_emergency_admin")
public class AuthEmergencyAdmin extends BaseEntity {

    /** 状态常量 */
    public static final String STATUS_DISABLED = "DISABLED";
    public static final String STATUS_ENABLED = "ENABLED";
    public static final String STATUS_IN_USE = "IN_USE";

    /** 管理员编码，租户内唯一 */
    private String adminCode;

    /** 管理员名称 */
    private String adminName;

    /** 加密后的密钥哈希 */
    private String secretHash;

    /** 状态：DISABLED/ENABLED/IN_USE */
    private String status;

    /** 最近使用时间 */
    private OffsetDateTime lastUsedAt;

    /** 已使用次数 */
    private Integer usageCount;

    /** 最大使用次数，默认 5 */
    private Integer maxUsageCount;

    /** 批准人 */
    private String approvedBy;

    /** 批准原因 */
    private String approvedReason;

    /** 批准时间 */
    private OffsetDateTime approvedAt;
}
