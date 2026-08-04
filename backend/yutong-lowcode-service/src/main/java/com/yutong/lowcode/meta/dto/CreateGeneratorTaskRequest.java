package com.yutong.lowcode.meta.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 代码生成任务创建请求。设计来源: 14-低代码平台设计 API 契约
 * target_scope: DDL / JAVA / VUE / UNIAPP / OPENAPI
 */
@Getter
@Setter
public class CreateGeneratorTaskRequest {

    private String entityId;

    private String pageId;

    private String templateVersion;

    /** DDL / JAVA / VUE / UNIAPP / OPENAPI */
    private String targetScope;

    /** 幂等键，同 tenantId+lowcode+task+idempotencyKey 已存在直接返回 */
    private String idempotencyKey;
}
