package com.yutong.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 任务委派/转办请求。设计来源: 41 号文档 API 契约 POST /delegate 和 /transfer。
 * <p>委派 (DELEGATE): 任务转到被委派人, 被委派人办理后任务回到原办理人。
 * <p>转办 (TRANSFER): 任务转到新人, 原办理人不再参与。
 */
@Data
public class DelegateTaskRequest {

    @NotBlank
    private String delegateToUserId;

    /** 委派/转办说明 */
    private String opinion;

    /** 委派类型: DELEGATE / TRANSFER (默认 DELEGATE) */
    private String delegateType;
}
