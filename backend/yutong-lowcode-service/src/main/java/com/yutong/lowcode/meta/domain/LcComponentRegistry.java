package com.yutong.lowcode.meta.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 低代码组件协议元数据注册表。设计来源: 36-低代码高级能力设计、57-完整DDL清单 lc_component_registry
 * 保存组件分类/平台/兼容性/props_schema/event_schema 等元数据，设计器组件库渲染依据。
 * 状态: DRAFT → PUBLISHED → DISABLED
 */
@Getter
@Setter
@TableName("lc_component_registry")
public class LcComponentRegistry extends BaseEntity {

    // ===== platform 平台 =====
    public static final String PLATFORM_WEB = "WEB";
    public static final String PLATFORM_MOBILE = "MOBILE";
    public static final String PLATFORM_BOTH = "BOTH";

    // ===== category 分类 =====
    public static final String CATEGORY_INPUT = "INPUT";
    public static final String CATEGORY_DISPLAY = "DISPLAY";
    public static final String CATEGORY_CONTAINER = "CONTAINER";
    public static final String CATEGORY_TABLE = "TABLE";
    public static final String CATEGORY_BUSINESS = "BUSINESS";
    public static final String CATEGORY_MOBILE = "MOBILE";
    public static final String CATEGORY_CHART = "CHART";

    // ===== compatibility_grade 兼容性等级 =====
    public static final String GRADE_STABLE = "STABLE";
    public static final String GRADE_EXPERIMENTAL = "EXPERIMENTAL";
    public static final String GRADE_INTERNAL = "INTERNAL";

    // ===== status 生命周期 =====
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_DISABLED = "DISABLED";

    /** 组件编码，租户内唯一，设计器引用键 */
    private String componentCode;

    private String componentName;

    /** 组件类型，对齐 lc_component.component_type */
    private String componentType;

    private String displayName;

    /** WEB / MOBILE / BOTH */
    private String platform;

    /** INPUT / DISPLAY / CONTAINER / TABLE / BUSINESS / MOBILE / CHART */
    private String category;

    /** STABLE / EXPERIMENTAL / INTERNAL */
    private String compatibilityGrade;

    /** 组件属性 JSON Schema，属性面板渲染依据 */
    private String propsSchema;

    /** 组件事件契约 JSON */
    private String eventSchema;

    /** 数据绑定契约 JSON */
    private String dataBinding;

    /** 是否支持权限码绑定 */
    private Boolean permissionSupport;

    /** 是否支持校验规则 */
    private Boolean validationSupport;

    /** 默认权限码（permission_support=true 时生效） */
    private String permissionCode;

    /** 组件协议版本，semver */
    private String componentVersion;

    private String minPlatformVersion;

    private String maxPlatformVersion;

    private String description;

    private String icon;

    /** DRAFT / PUBLISHED / DISABLED */
    private String status;

    /** 是否已废弃 */
    private Boolean deprecated;

    private String deprecatedMessage;

    private Integer sortNo;
}
