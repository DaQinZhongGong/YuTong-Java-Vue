package com.yutong.lowcode.meta.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 低代码页面动作。设计来源: 14-低代码平台设计、57-完整DDL清单 lc_action
 * 动作类型: SUBMIT / RESET / API_CALL / NAVIGATE / EXPORT
 */
@Getter
@Setter
@TableName("lc_action")
public class LcAction extends BaseEntity {

    public static final String TYPE_SUBMIT = "SUBMIT";
    public static final String TYPE_RESET = "RESET";
    public static final String TYPE_API_CALL = "API_CALL";
    public static final String TYPE_NAVIGATE = "NAVIGATE";
    public static final String TYPE_EXPORT = "EXPORT";

    private String pageId;

    private String actionCode;

    private String actionName;

    private String actionType;

    private String permissionCode;

    private Boolean confirmRequired;

    private String apiMethod;

    private String apiPath;

    /** 请求参数映射 JSON */
    private String payloadMapping;
}
