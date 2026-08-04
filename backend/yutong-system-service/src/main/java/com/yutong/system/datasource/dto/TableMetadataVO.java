package com.yutong.system.datasource.dto;

import lombok.Data;

/**
 * 表元数据 VO。GA2-46 v1.5。
 * <p>
 * 元数据浏览 API (46 号文档 line 125): 按表/列 ACL 过滤, 不返回未授权表。
 */
@Data
public class TableMetadataVO {

    /** 表名 */
    private String tableName;

    /** 表 schema (如 public) */
    private String tableSchema;

    /** 表类型: BASE TABLE / VIEW */
    private String tableType;

    /** 表注释 */
    private String tableComment;

    /** 估算行数 (仅元数据参考, 非精确值) */
    private Long estimatedRows;

    /** 是否允许查询 (来自 sys_datasource_table_acl.query_allowed) */
    private Boolean queryAllowed;

    /** 查询最大返回行数 (来自 sys_datasource_table_acl.max_rows) */
    private Integer maxRows;
}
