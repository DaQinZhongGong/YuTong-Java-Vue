package com.yutong.system.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 租户套餐保存请求
 * 落点: 70-商业授权与版本能力裁剪「租户套餐」
 */
@Data
public class SaveTenantPackageRequest {

    private String id; // 编辑时填

    @NotBlank(message = "套餐编码不能为空")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,49}$", message = "套餐编码必须以大写字母开头, 只能含大写字母/数字/下划线, 2-50 字符")
    private String packageCode;

    @NotBlank(message = "套餐名称不能为空")
    private String packageName;

    private String description;

    /** 单价 元/期 (>= 0) */
    private BigDecimal priceCnyPerPeriod;

    /** 计费周期 月 (1 / 3 / 12 / 24) */
    private Integer periodMonths;

    /** 状态: DRAFT/ACTIVE/ARCHIVED */
    private String status;

    /** 包含菜单 ID 列表 (JSON 数组) */
    private String menuIdsJson;

    /** 资源配额 JSON */
    private String quotaJson;

    /** 排序 (升序, 数字越小越靠前) */
    private Integer sortNo;
}
