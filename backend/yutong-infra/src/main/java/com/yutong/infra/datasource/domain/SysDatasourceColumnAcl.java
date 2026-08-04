package com.yutong.infra.datasource.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 数据源列级 ACL。设计来源: 46-多数据源与数据集设计 sys_datasource_column_acl。
 * <p>
 * 敏感列隐藏: sensitivity_level HIGH/CRITICAL 默认仅 admin 可见, masking_strategy MASK/HIDE/HASH/NONE。
 */
@Getter
@Setter
@TableName("sys_datasource_column_acl")
public class SysDatasourceColumnAcl extends BaseEntity {

    public static final String SENSITIVITY_LOW = "LOW";
    public static final String SENSITIVITY_MEDIUM = "MEDIUM";
    public static final String SENSITIVITY_HIGH = "HIGH";
    public static final String SENSITIVITY_CRITICAL = "CRITICAL";

    public static final String MASKING_MASK = "MASK";
    public static final String MASKING_HIDE = "HIDE";
    public static final String MASKING_HASH = "HASH";
    public static final String MASKING_NONE = "NONE";

    public static final String STATUS_ENABLED = "ENABLED";
    public static final String STATUS_DISABLED = "DISABLED";

    /** 关联 sys_datasource.id */
    private String datasourceId;

    private String tableName;

    private String columnName;

    /** LOW/MEDIUM/HIGH/CRITICAL */
    private String sensitivityLevel;

    /** 允许可见的角色或权限码 (jsonb 数组), 为空则所有人不可见 */
    private String allowedRoles;

    /** MASK(掩码)/HIDE(隐藏)/HASH(哈希)/NONE(不脱敏) */
    private String maskingStrategy;

    /** ENABLED/DISABLED */
    private String status;
}
