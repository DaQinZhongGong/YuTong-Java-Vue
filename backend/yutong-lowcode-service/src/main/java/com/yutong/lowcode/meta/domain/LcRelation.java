package com.yutong.lowcode.meta.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 低代码实体关系。设计来源: 14-低代码平台设计、57-完整DDL清单 lc_relation
 * relation_type: ONE_TO_ONE / ONE_TO_MANY / MANY_TO_ONE
 */
@Getter
@Setter
@TableName("lc_relation")
public class LcRelation extends BaseEntity {

    public static final String TYPE_ONE_TO_ONE = "ONE_TO_ONE";
    public static final String TYPE_ONE_TO_MANY = "ONE_TO_MANY";
    public static final String TYPE_MANY_TO_ONE = "MANY_TO_ONE";

    private String sourceEntityId;

    private String targetEntityId;

    private String relationType;

    private String sourceFieldCode;

    private String targetFieldCode;

    /** CASCADE / SET_NULL / RESTRICT */
    private String cascadePolicy;

    private Boolean required;
}
