package com.yutong.lowcode.meta.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 保存实体草稿请求。设计来源: 14-低代码平台设计 API 契约
 * id 为空表示新建，非空表示修改（修改时 version 必填用于乐观锁）
 */
@Getter
@Setter
public class SaveLcEntityRequest {

    private String id;

    private Integer version;

    private String entityCode;

    private String entityName;

    private String tableName;

    private String moduleCode;

    private String ownerUserId;

    private List<LcFieldDTO> fields;

    private List<LcRelationDTO> relations;

    @Getter
    @Setter
    public static class LcFieldDTO {
        private String id;
        private String fieldCode;
        private String fieldName;
        private String dbColumn;
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
    }

    @Getter
    @Setter
    public static class LcRelationDTO {
        private String targetEntityId;
        private String relationType;
        private String sourceFieldCode;
        private String targetFieldCode;
        private String cascadePolicy;
        private Boolean required;
    }
}
