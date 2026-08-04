package com.yutong.lowcode.plugin.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 插件包实体（45 号文档「插件与模板生态设计」）。
 * 保存插件元数据：编码/名称/版本/签名状态/风险等级/版本兼容性/安装次数等。
 * 状态机: UPLOADED → VERIFIED → REJECTED/DEPRECATED
 */
@Getter
@Setter
@TableName("plugin_package")
public class PluginPackage extends BaseEntity {

    // ===== signature_status 签名状态 =====
    public static final String SIGNATURE_UNSIGNED = "UNSIGNED";
    public static final String SIGNATURE_VALID = "VALID";
    public static final String SIGNATURE_INVALID = "INVALID";

    // ===== risk_level 风险等级 =====
    public static final String RISK_LOW = "LOW";
    public static final String RISK_MEDIUM = "MEDIUM";
    public static final String RISK_HIGH = "HIGH";

    // ===== status 生命周期 =====
    public static final String STATUS_UPLOADED = "UPLOADED";
    public static final String STATUS_VERIFIED = "VERIFIED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_DEPRECATED = "DEPRECATED";

    /** 插件编码，租户内唯一 */
    private String pluginCode;

    private String pluginName;

    /** 插件版本，semver */
    private String pluginVersion;

    /** 插件包哈希值（SHA-256） */
    private String packageHash;

    /** UNSIGNED / VALID / INVALID */
    private String signatureStatus;

    /** 授权类型（开源/商业/试用） */
    private String licenseType;

    /** LOW / MEDIUM / HIGH */
    private String riskLevel;

    /** 最低平台版本兼容约束 */
    private String minPlatformVersion;

    /** 最高平台版本兼容约束 */
    private String maxPlatformVersion;

    /** UPLOADED / VERIFIED / REJECTED / DEPRECATED */
    private String status;

    /** 安装次数统计 */
    private Integer installCount;

    /** 插件作者 */
    private String author;

    /** 插件描述 */
    private String description;

    /** 依赖声明 JSON（依赖插件编码列表） */
    private String dependenciesJson;
}
