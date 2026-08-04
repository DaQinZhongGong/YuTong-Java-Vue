package com.yutong.system.notification.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 消息模板。设计来源: 35-样例业务矩阵扩展设计 P2 实时通知 + 44-实时通信与消息推送设计
 * <p>
 * GA2-40 落地: 通过模板代码 + 变量渲染标题/内容，支持 6 种 msg_type。
 * 业务侧调用 NotificationOpsApplicationService.sendNotification(templateCode, receiver, variables) 触发渲染+分发。
 * <p>
 * 表结构: sys_message_template 由 V002 创建 (channel/status/params_schema 列),
 * V016 扩展 msg_type/target_route_id/priority/delivery_mode 列用于通知运营场景。
 */
@Getter
@Setter
@TableName("sys_message_template")
public class SysMessageTemplate extends BaseEntity {
    /** 模板代码 (业务侧引用, 同租户内 + channel 唯一) */
    private String templateCode;
    /** 模板名称 (展示用) */
    private String templateName;
    /** 模板默认渠道 (V002 列, 通知运营场景统一用 IN_APP) */
    private String channel;
    /** 标题模板 (含 var 占位符, 如 "您有新的申请单 ${requestNo}") */
    private String titleTemplate;
    /** 内容模板 (含 var 占位符) */
    private String contentTemplate;
    /** 参数 schema (V002 列, JSON, 可空, 暂未使用) */
    private String paramsSchema;
    /** 模板状态 (V002 列, 取值 ENABLED/DISABLED) */
    private String status;
    /** 消息类型 (V016 追加): SYSTEM/BIZ/APPROVAL/EXPORT/WORKFLOW/ALERT */
    private String msgType;
    /** 默认跳转路由 (V016 追加) */
    private String targetRouteId;
    /** 默认优先级 (V016 追加): LOW/NORMAL/HIGH/URGENT */
    private String priority;
    /** 默认投递模式 (V016 追加): PERSIST_THEN_PUSH / PUSH_ONLY */
    private String deliveryMode;
    /** 备注 */
    private String remark;

    /** status 常量 */
    public static final String STATUS_ENABLED = "ENABLED";
    public static final String STATUS_DISABLED = "DISABLED";

    /** msg_type 常量 */
    public static final String MSG_TYPE_SYSTEM = "SYSTEM";
    public static final String MSG_TYPE_BIZ = "BIZ";
    public static final String MSG_TYPE_APPROVAL = "APPROVAL";
    public static final String MSG_TYPE_EXPORT = "EXPORT";
    public static final String MSG_TYPE_WORKFLOW = "WORKFLOW";
    public static final String MSG_TYPE_ALERT = "ALERT";

    /** priority 常量 */
    public static final String PRIORITY_LOW = "LOW";
    public static final String PRIORITY_NORMAL = "NORMAL";
    public static final String PRIORITY_HIGH = "HIGH";
    public static final String PRIORITY_URGENT = "URGENT";

    /** delivery_mode 常量 */
    public static final String DELIVERY_PERSIST_THEN_PUSH = "PERSIST_THEN_PUSH";
    public static final String DELIVERY_PUSH_ONLY = "PUSH_ONLY";

    /** channel 常量 (V002 列, 通知运营场景统一用 IN_APP) */
    public static final String CHANNEL_IN_APP = "IN_APP";
}
