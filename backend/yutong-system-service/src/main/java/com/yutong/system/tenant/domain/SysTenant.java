package com.yutong.system.tenant.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 租户注册表。设计来源: 业界同类实现 AI sys_tenant + ADR 0004 P2-F。
 *
 * <p>语义:
 * <ul>
 *   <li>平台级主数据: 记录每个租户的企业信息、绑定套餐、过期时间、账号配额;</li>
 *   <li>全局可见: 表在 {@code yutong.tenant.ignore-tables} 中豁免行级过滤
 *       (平台管理员看全量, 与 业界同类实现 超管模型一致), 访问由 {@code tenant:tenant:*} 权限码守卫;</li>
 *   <li>行自身 {@code tenant_id} = {@code tenantCode} (自描述, 满足全表 tenant_id 列约定);</li>
 *   <li>{@code tenantCode} 创建后不可改 (130+ 表用它做行级隔离键, 改码会撕裂数据);</li>
 *   <li>{@code default} 为平台内置租户: 禁止停用/删除;</li>
 *   <li>状态: NORMAL 正常 / DISABLED 停用; 删除仅允许 DISABLED (先停用后删除)。</li>
 * </ul>
 */
@Getter
@Setter
@TableName("sys_tenant")
public class SysTenant extends BaseEntity {

    public static final String STATUS_NORMAL = "NORMAL";
    public static final String STATUS_DISABLED = "DISABLED";

    /** 内置平台租户编码, 禁止停用/删除 */
    public static final String BUILTIN_DEFAULT = "default";

    /** 租户编码 (如 default/acme), 全局唯一, 创建后不可改, 亦是各业务表 tenant_id 的取值 */
    @TableField("tenant_code")
    private String tenantCode;

    /** 企业名称 */
    @TableField("company_name")
    private String companyName;

    /** 联系人 */
    @TableField("contact_user_name")
    private String contactUserName;

    /** 联系电话 */
    @TableField("contact_phone")
    private String contactPhone;

    /** 统一社会信用代码 */
    @TableField("license_number")
    private String licenseNumber;

    /** 地址 */
    private String address;

    /** 绑定域名 */
    private String domain;

    /** 企业简介 */
    private String intro;

    /** 绑定套餐 ID (sys_tenant_package.id), 可空 = 未订阅 */
    @TableField("package_id")
    private String packageId;

    /** 套餐过期时间, null = 不过期 */
    @TableField("expire_time")
    private LocalDateTime expireTime;

    /** 账号数量上限, -1 = 不限制 */
    @TableField("account_count")
    private Long accountCount;

    /** 状态: NORMAL/DISABLED */
    private String status;
}
