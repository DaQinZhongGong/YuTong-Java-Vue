package com.yutong.lowcode.plugin.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 插件市场展示 VO（45 号文档「插件与模板生态设计」）。
 * 聚合插件包元数据 + 当前租户安装状态。
 */
@Getter
@Setter
public class MarketPluginVO {

    private String id;

    private String pluginCode;

    private String pluginName;

    private String pluginVersion;

    private String description;

    private String author;

    private String licenseType;

    private String riskLevel;

    private Integer installCount;

    /** 最低平台版本兼容约束 */
    private String minPlatformVersion;

    /** 最高平台版本兼容约束 */
    private String maxPlatformVersion;

    /** 当前租户是否已安装 */
    private boolean installed;

    /** 安装记录状态（installed=true 时有效）：ACTIVE / DISABLED */
    private String installationStatus;

    /** 依赖声明 JSON */
    private String dependenciesJson;
}
