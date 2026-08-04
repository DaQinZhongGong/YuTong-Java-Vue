package com.yutong.system.notification.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建/更新消息模板请求。GA2-40 实时通知 P2 落地。
 */
@Data
public class SaveTemplateRequest {
    @NotBlank(message = "templateCode 不能为空")
    private String templateCode;

    @NotBlank(message = "templateName 不能为空")
    private String templateName;

    @NotBlank(message = "msgType 不能为空")
    private String msgType;

    @NotBlank(message = "titleTemplate 不能为空")
    private String titleTemplate;

    @NotBlank(message = "contentTemplate 不能为空")
    private String contentTemplate;

    private String targetRouteId;
    private String priority;
    private String deliveryMode;
    private Boolean enabled;
    private String remark;
}
