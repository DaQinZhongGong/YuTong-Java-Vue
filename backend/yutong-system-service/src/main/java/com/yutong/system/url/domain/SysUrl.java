package com.yutong.system.url.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * URL 白名单实体。设计来源: V056 sys_url + 业界同类实现 SysUrl。
 */
@Getter
@Setter
@TableName("sys_url")
public class SysUrl extends BaseEntity {

    /** URL 模式 (Ant 风格) */
    private String urlPattern;

    /** 类型: PUBLIC / ANONYMOUS / PERMIT_ALL */
    private String urlType;

    /** 描述 */
    private String description;

    /** 状态: ENABLED / DISABLED */
    private String status;

    public static final String TYPE_PUBLIC = "PUBLIC";
    public static final String TYPE_ANONYMOUS = "ANONYMOUS";
    public static final String TYPE_PERMIT_ALL = "PERMIT_ALL";

    public static final String STATUS_ENABLED = "ENABLED";
    public static final String STATUS_DISABLED = "DISABLED";
}
