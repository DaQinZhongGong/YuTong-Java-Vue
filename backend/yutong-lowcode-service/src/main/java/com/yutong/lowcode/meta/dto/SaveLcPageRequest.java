package com.yutong.lowcode.meta.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 保存页面草稿请求。设计来源: 14-低代码平台设计 API 契约
 */
@Getter
@Setter
public class SaveLcPageRequest {

    private String id;

    private Integer version;

    private String pageCode;

    private String pageName;

    private String entityId;

    private String pageType;

    private String layoutJson;

    private String layoutSchemaVersion;

    private List<LcComponentDTO> components;

    private List<LcActionDTO> actions;

    @Getter
    @Setter
    public static class LcComponentDTO {
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
    public static class LcActionDTO {
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
