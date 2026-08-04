package com.yutong.system.datasource.dto;

import lombok.Data;

/**
 * 列元数据 VO。GA2-46 v1.5。
 * <p>
 * 安全约束 (46 号文档 line 126): 不返回敏感列或密钥信息。
 * <ul>
 *   <li>sensitivity_level HIGH/CRITICAL 且当前用户未授权时, 该列不出现在结果中</li>
 *   <li>masking_strategy HIDE 时, 该列不出现在结果中</li>
 *   <li>敏感列的注释/默认值等元数据也不返回</li>
 * </ul>
 */
@Data
public class ColumnMetadataVO {

    /** 列名 */
    private String columnName;

    /** 列数据类型 (如 varchar, int4, timestamptz) */
    private String dataType;

    /** 是否可空 */
    private Boolean nullable;

    /** 列长度 (适用于 varchar/char) */
    private Integer columnSize;

    /** 默认值 (敏感列不返回) */
    private String columnDefault;

    /** 列序号 */
    private Integer ordinalPosition;

    /** 列注释 (敏感列不返回) */
    private String columnComment;

    /** 敏感级别 LOW/MEDIUM/HIGH/CRITICAL (来自 sys_datasource_column_acl, 仅管理员可见) */
    private String sensitivityLevel;

    /** 脱敏策略 MASK/HIDE/HASH/NONE (仅管理员可见) */
    private String maskingStrategy;
}
