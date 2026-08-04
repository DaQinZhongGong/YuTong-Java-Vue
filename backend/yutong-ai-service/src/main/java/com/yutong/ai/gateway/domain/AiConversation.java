package com.yutong.ai.gateway.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * AI 会话。设计来源: 13-AI能力设计、57-完整DDL清单 ai_conversation
 * 状态: ACTIVE / ARCHIVED。
 */
@Getter
@Setter
@TableName("ai_conversation")
public class AiConversation extends BaseEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    /** 会话编号 */
    private String conversationNo;

    /** 发起用户 ID */
    private String userId;

    /** 会话标题 */
    private String title;

    /** 使用场景 */
    private String scenario;

    /** 语言区域 */
    private String locale;

    /** 使用的模型编码 */
    private String modelCode;

    /** ACTIVE / ARCHIVED */
    private String status;

    /** 最后消息时间 */
    private OffsetDateTime lastMessageTime;
}
