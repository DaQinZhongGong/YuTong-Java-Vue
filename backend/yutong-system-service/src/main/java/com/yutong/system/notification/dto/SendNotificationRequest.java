package com.yutong.system.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 发送通知请求。GA2-40 实时通知 P2 落地。
 * <p>
 * 业务侧通过 templateCode + variables 触发通知发送：
 * 1. 加载模板 sys_message_template
 * 2. 渲染标题/内容（变量替换 ${var}）
 * 3. 写 sys_message（站内信）
 * 4. 写 sys_notification_dispatch_log（IN_APP 渠道，初始 PENDING）
 * 5. PushService.push（WebSocket 实时推送）
 * 6. 若用户订阅了对应 topic 的 MOBILE_PUSH，写 MOBILE_PUSH 分发日志 + 调用 MockMobilePushAdapter
 */
@Data
public class SendNotificationRequest {
    /** 模板代码 (sys_message_template.template_code) */
    @NotBlank(message = "templateCode 不能为空")
    private String templateCode;

    /** 接收人 user_id */
    @NotBlank(message = "receiverId 不能为空")
    private String receiverId;

    /** 模板变量 (key=变量名, value=替换值)，如 {"requestNo":"REQ001","amount":"1000"} */
    @NotNull(message = "variables 不能为 null")
    private Map<String, String> variables;

    /** 业务类型 (覆盖模板默认值，可选) */
    private String bizType;

    /** 业务 ID (可选) */
    private String bizId;
}
