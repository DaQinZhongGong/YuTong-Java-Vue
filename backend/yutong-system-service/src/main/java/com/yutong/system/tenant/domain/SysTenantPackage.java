package com.yutong.system.tenant.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 租户套餐。设计来源: platform-ai sys_tenant_package + 70-商业授权与版本能力裁剪
 * <p>
 * 用于 SaaS 多租户场景: 不同套餐绑不同菜单/功能/资源配额。
 * 状态: DRAFT → ACTIVE → ARCHIVED
 * 计费: 按年/月,priceCnyPerPeriod + periodMonths
 */
@Getter
@Setter
@TableName("sys_tenant_package")
public class SysTenantPackage extends BaseEntity {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    /** 套餐编码 (租户内唯一, 如 "BASIC" / "PRO" / "ENTERPRISE") */
    @TableField("package_code")
    private String packageCode;

    /** 套餐名称 */
    @TableField("package_name")
    private String packageName;

    /** 描述 */
    private String description;

    /** 单价 (元/期) */
    @TableField("price_cny_per_period")
    private BigDecimal priceCnyPerPeriod;

    /** 计费周期 (月), 如 1 / 3 / 12 */
    @TableField("period_months")
    private Integer periodMonths;

    /** 状态: DRAFT/ACTIVE/ARCHIVED */
    private String status;

    /** 包含的菜单 ID 列表 (JSON 数组), 用于购买时绑定到租户 */
    @TableField("menu_ids_json")
    private String menuIdsJson;

    /** 资源配额 JSON: {aiMonthlyTokens: 100000, storageGb: 10, ...} */
    @TableField("quota_json")
    private String quotaJson;

    /** 排序 (升序) */
    @TableField("sort_no")
    private Integer sortNo;

    /** 上线时间 (null = 草稿) */
    @TableField("published_at")
    private LocalDateTime publishedAt;
}
