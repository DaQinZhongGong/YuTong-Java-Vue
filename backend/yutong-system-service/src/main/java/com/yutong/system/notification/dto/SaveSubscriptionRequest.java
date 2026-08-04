package com.yutong.system.notification.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建/更新移动端订阅请求。GA2-40 实时通知 P2 落地。
 */
@Data
public class SaveSubscriptionRequest {
    @NotBlank(message = "topic 不能为空")
    private String topic;

    @NotBlank(message = "channel 不能为空")
    private String channel;

    private Boolean enabled;
    private String deviceToken;
    private String remark;
}
