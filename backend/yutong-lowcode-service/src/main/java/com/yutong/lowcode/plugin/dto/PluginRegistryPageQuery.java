package com.yutong.lowcode.plugin.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 插件注册表分页查询条件。
 * 支持 keyword/pluginType/status 过滤
 */
@Getter
@Setter
public class PluginRegistryPageQuery {

    private int pageNo = 1;

    private int pageSize = 20;

    /** 模糊匹配 plugin_code 或 plugin_name */
    private String keyword;

    /** TEMPLATE / LOWCODE_COMPONENT */
    private String pluginType;

    /** ACTIVE / INACTIVE / DEPRECATED */
    private String status;
}
