package com.yutong.lowcode.meta.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 页面详情 VO（含组件和动作列表）。设计来源: 14-低代码平台设计
 */
@Getter
@Setter
public class LcPageDetailVO {

    private String id;
    private String pageCode;
    private String pageName;
    private String entityId;
    private String pageType;
    private String layoutJson;
    private String layoutSchemaVersion;
    private Integer versionNo;
    private String status;
    private OffsetDateTime publishedTime;
    private Integer version;
    private OffsetDateTime createdTime;

    private List<ComponentDTO> components;
    private List<ActionDTO> actions;

    @Getter
    @Setter
    public static class ComponentDTO {
        private String id;
        private String componentCode;
        private String componentType;
        private String propsJson;
        private String rulesJson;
        private String eventsJson;
        private String propsSchemaVersion;
        private String parentComponentId;
        private Integer sortNo;
    }

    @Getter
    @Setter
    public static class ActionDTO {
        private String id;
        private String actionCode;
        private String actionName;
        private String actionType;
        private String permissionCode;
        private Boolean confirmRequired;
        private String apiMethod;
        private String apiPath;
        private String payloadMapping;
    }
}
