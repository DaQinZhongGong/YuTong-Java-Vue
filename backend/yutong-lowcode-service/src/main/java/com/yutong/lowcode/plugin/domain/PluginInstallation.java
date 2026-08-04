package com.yutong.lowcode.plugin.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 插件安装记录实体（45 号文档「插件与模板生态设计」）。
 * 保存租户插件安装状态、配置、安装/卸载时间。
 * 状态: ACTIVE / INACTIVE
 */
@Getter
@Setter
@TableName("plugin_installation")
public class PluginInstallation extends BaseEntity {

    // ===== status 状态 =====
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

    /** 关联的插件包 ID */
    private String pluginId;

    /** 安装人用户 ID */
    private String installedBy;

    /** 安装时间 */
    private OffsetDateTime installedTime;

    /** 卸载时间 */
    private OffsetDateTime uninstalledTime;

    /** ACTIVE / INACTIVE */
    private String status;

    /** 插件配置 JSON（运行时参数） */
    private String configJson;
}
