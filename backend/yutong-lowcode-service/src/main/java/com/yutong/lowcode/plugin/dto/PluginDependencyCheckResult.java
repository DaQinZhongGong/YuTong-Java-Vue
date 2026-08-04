package com.yutong.lowcode.plugin.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 插件依赖校验结果。
 */
@Getter
@Setter
public class PluginDependencyCheckResult {

    /** 是否通过校验 */
    private boolean passed;

    /** 缺失的依赖插件编码列表 */
    private List<String> missingDependencies;

    /** 平台版本是否兼容 */
    private boolean versionCompatible;

    /** 版本不兼容时的提示信息 */
    private String versionConflictMessage;
}
