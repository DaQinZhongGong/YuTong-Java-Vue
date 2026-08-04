package com.yutong.system.message.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** 站内信。设计来源: 57-完整DDL清单 sys_message */
@Getter
@Setter
@TableName("sys_message")
public class SysMessage extends BaseEntity {
    /** 接收人ID */
    private String receiverId;
    /** 消息类型: SYSTEM/BIZ/APPROVAL/EXPORT */
    private String msgType;
    /** 标题 */
    private String title;
    /** 内容 */
    private String content;
    /** 已读状态: UNREAD/READ */
    private String readStatus;
    /** 已读时间 */
    private OffsetDateTime readTime;
    /** 业务类型 */
    private String bizType;
    /** 业务ID */
    private String bizId;
    /** 目标路由ID */
    private String targetRouteId;
    /** 目标参数(JSON字符串，对应jsonb) */
    private String targetParams;
}
