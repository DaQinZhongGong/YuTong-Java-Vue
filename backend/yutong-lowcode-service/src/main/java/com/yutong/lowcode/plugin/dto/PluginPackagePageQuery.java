package com.yutong.lowcode.plugin.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 插件包分页查询条件。
 * 支持 keyword/status/riskLevel 过滤
 */
@Getter
@Setter
public class PluginPackagePageQuery {

    private int pageNo = 1;

    private int pageSize = 20;

    /** 模糊匹配 plugin_code 或 plugin_name */
    private String keyword;

    /** UPLOADED / VERIFIED / REJECTED / DEPRECATED */
    private String status;

    /** LOW / MEDIUM / HIGH */
    private String riskLevel;
}
