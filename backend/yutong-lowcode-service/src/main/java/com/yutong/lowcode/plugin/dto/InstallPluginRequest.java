package com.yutong.lowcode.plugin.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 安装插件请求
 */
@Getter
@Setter
public class InstallPluginRequest {

    /** 插件配置 JSON（运行时参数） */
    private String configJson;
}
