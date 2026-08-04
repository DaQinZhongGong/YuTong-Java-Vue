package com.yutong.lowcode.meta.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 低代码字段定义。设计来源: 14-低代码平台设计、57-完整DDL清单 lc_field
 * 约束: primary_flag=true 时只允许 field_code=id, data_type=STRING, db_column=varchar(32)
 */
@Getter
@Setter
@TableName("lc_field")
public class LcField extends BaseEntity {

    public static final String TYPE_STRING = "STRING";
    public static final String TYPE_DECIMAL = "DECIMAL";
    public static final String TYPE_DATE = "DATE";
    public static final String TYPE_DICT = "DICT";
    public static final String TYPE_FILE = "FILE";
    public static final String TYPE_JSON = "JSON";

    private String entityId;

    /** 字段编码，JSON 使用 camelCase */
    private String fieldCode;

    private String fieldName;

    /** 多语言字段名 jsonb */
    private String fieldNameI18n;

    /** 数据库列名 snake_case */
    private String dbColumn;

    /** STRING/DECIMAL/DATE/DICT/FILE/JSON */
    private String dataType;

    private Integer lengthValue;

    private Integer precisionValue;

    private Integer scaleValue;

    private Boolean nullable;

    private String defaultValue;

    private String dictType;

    private Boolean primaryFlag;

    private Boolean uniqueFlag;

    private Boolean indexFlag;

    private Integer sortNo;

    /** 元模型迁移兼容旧字段名 */
    private String oldFieldCode;
}
