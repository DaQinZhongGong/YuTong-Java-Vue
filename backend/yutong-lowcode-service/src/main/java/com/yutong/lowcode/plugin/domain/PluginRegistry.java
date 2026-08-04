package com.yutong.lowcode.plugin.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 插件注册表实体（45 号文档「插件与模板生态设计」E0）。
 * 保存内置插件元数据：编码/名称/版本/类型/状态/描述/入口类/标签等。
 * 类型: TEMPLATE / LOWCODE_COMPONENT
 * 状态: ACTIVE / INACTIVE / DEPRECATED
 */
@Getter
@Setter
@TableName("plugin_registry")
public class PluginRegistry extends BaseEntity {

    // ===== plugin_type 插件类型 =====
    public static final String TYPE_TEMPLATE = "TEMPLATE";
    public static final String TYPE_LOWCODE_COMPONENT = "LOWCODE_COMPONENT";

    // ===== status 生命周期 =====
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";
    public static final String STATUS_DEPRECATED = "DEPRECATED";

    /** 插件编码，租户内唯一 */
    private String pluginCode;

    private String pluginName;

    /** 插件版本，semver */
    private String pluginVersion;

    /** TEMPLATE / LOWCODE_COMPONENT */
    private String pluginType;

    /** ACTIVE / INACTIVE / DEPRECATED */
    private String status;

    /** 插件描述 */
    private String description;

    /** 入口类或组件路径 */
    private String entryClass;

    /** 图标 URL */
    private String iconUrl;

    /** 标签（逗号分隔） */
    private String tags;
}
