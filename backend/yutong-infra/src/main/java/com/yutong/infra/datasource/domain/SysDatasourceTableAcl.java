package com.yutong.infra.datasource.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 数据源表级 ACL。设计来源: 46-多数据源与数据集设计 sys_datasource_table_acl。
 * <p>
 * 元数据浏览 API 按表过滤: allowed_roles 内的角色或权限码可见, query_allowed=false 则仅元数据可见不可查询。
 */
@Getter
@Setter
@TableName("sys_datasource_table_acl")
public class SysDatasourceTableAcl extends BaseEntity {

    public static final String STATUS_ENABLED = "ENABLED";
    public static final String STATUS_DISABLED = "DISABLED";

    /** 关联 sys_datasource.id */
    private String datasourceId;

    private String tableName;

    /** 允许浏览/查询的角色或权限码 (jsonb 数组): ["role:admin", "permission:datasource:metadata:view"] */
    private String allowedRoles;

    /** 是否允许 SELECT 查询 (true=可查询, false=仅元数据可见不可查询) */
    private Boolean queryAllowed;

    /** 查询最大返回行数 (覆盖数据集默认 max_rows) */
    private Integer maxRows;

    /** ENABLED/DISABLED */
    private String status;
}
