package com.yutong.lowcode.meta.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 实体详情 VO（含字段和关系列表）。设计来源: 14-低代码平台设计
 */
@Getter
@Setter
public class LcEntityDetailVO {

    private String id;
    private String entityCode;
    private String entityName;
    private String tableName;
    private String moduleCode;
    private Integer versionNo;
    private String schemaVersion;
    private String configHash;
    private String status;
    private String ownerUserId;
    private Integer version;
    private OffsetDateTime createdTime;
    private OffsetDateTime publishedTime;

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
        private String id;
        private String sourceEntityId;
        private String targetEntityId;
        private String relationType;
        private String sourceFieldCode;
        private String targetFieldCode;
        private String cascadePolicy;
        private Boolean required;
    }
}
