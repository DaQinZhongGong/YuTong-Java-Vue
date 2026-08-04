package com.yutong.system.event.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import com.yutong.infra.persistence.JsonbTypeHandler;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 端侧埋点事件实体。设计来源: 94-端侧埋点与体验监控详设
 *
 * <p>存储 Web/Uniapp 端侧 SDK 上报的 RUM、接口错误、JS 错误、页面访问、
 * 关键业务操作事件。payload 字段为 jsonb，使用 {@link JsonbTypeHandler} 避免
 * varchar/jsonb 类型不匹配（对齐 GA2-17-1 JsonbTypeHandler 范式）。
 *
 * <p>隐私约束: userIdHash/tenantIdHash/bizIdHash 由端侧 SDK 脱敏后上报，
 * 后端不采集明文用户 ID/租户 ID/业务 ID。payload 中的业务字段也必须已脱敏。
 */
@Getter
@Setter
@TableName(value = "sys_client_event", autoResultMap = true)
public class ClientEvent extends BaseEntity {

    /** 端侧生成 ULID，唯一标识一次事件 */
    private String eventId;

    /** 事件名，小写点分格式 端.模块.对象.动作 */
    private String eventName;

    /** 事件发生时间（端侧时间） */
    private OffsetDateTime occurredTime;

    /** 与接口链路关联的 traceId */
    private String traceId;

    /** 会话 ID，运行期生成 */
    private String sessionId;

    /** 用户标识 hash，隐私脱敏 */
    private String userIdHash;

    /** 租户标识 hash，隐私脱敏 */
    private String tenantIdHash;

    /** 当前路由 */
    private String route;

    /** 页面标题 */
    private String pageTitle;

    /** 平台: web/h5/mp/app */
    private String platform;

    /** 构建版本 */
    private String appVersion;

    /** 业务类型 */
    private String bizType;

    /** 业务对象 hash */
    private String bizIdHash;

    /** 结果: success/failed/cancel */
    private String result;

    /** 错误码 */
    private String errorCode;

    /** 耗时（毫秒） */
    private Integer durationMs;

    /** 附加业务字段 JSON（已脱敏），使用 JsonbTypeHandler 写入 jsonb 列 */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String payload;
}
