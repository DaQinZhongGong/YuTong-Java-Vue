package com.yutong.system.license.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * 商业授权与模块开关配置。设计来源: 70-商业授权与版本能力裁剪详设「模块开关」。
 *
 * <p>对应 application.yml:
 * <pre>{@code
 * yutong:
 *   modules:
 *     lowcode: true
 *     ai: true
 *     workflow: false
 *     report: true
 *     datasource: true
 *     plugin: false
 * }</pre>
 *
 * <p>规则（70 号文档）: 模块开关只影响菜单、路由、接口访问和初始化数据，
 * 不允许在业务核心代码中散落 if/else。本配置由 {@link com.yutong.api.facade.LicenseService#hasModule}
 * 在拦截器/Starter/FeatureFlag 层读取，业务核心代码不直接依赖。
 *
 * <p>GA2-L170 落地: 关闭 DEV-L170-004 偏差（yutong.modules 模块开关未配置）。
 */
@ConfigurationProperties(prefix = "yutong")
public class LicenseProperties {

    /**
     * 模块开关映射。key 为模块编码（lowcode/ai/report/workflow/datasource/plugin），
     * value 为是否启用。未配置的模块默认 true（保证基础能力可用，符合 70 号文档 Community 默认行为）。
     */
    private Map<String, Boolean> modules = new HashMap<>();

    public Map<String, Boolean> getModules() {
        return modules;
    }

    public void setModules(Map<String, Boolean> modules) {
        this.modules = modules;
    }

    /**
     * 判断模块开关是否开启。未配置默认 true（Community 基础能力默认可用）。
     *
     * @param moduleCode 模块编码
     * @return true 表示开关开启（或未配置）；false 表示显式关闭
     */
    public boolean isModuleEnabled(String moduleCode) {
        if (moduleCode == null || moduleCode.isBlank()) {
            return false;
        }
        return modules.getOrDefault(moduleCode, Boolean.TRUE);
    }
}
