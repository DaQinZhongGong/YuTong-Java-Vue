package com.yutong.ai.memory.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 跨会话记忆。设计来源: V044 ai_memory，业界同类实现 记忆管理长期/用户/全局层。
 * 短期记忆仍由 AgentMemoryService 的会话窗口承担。
 */
@Getter
@Setter
@TableName("ai_memory")
public class AiMemory extends BaseEntity {

    public static final String OWNER_GLOBAL = "GLOBAL";
    public static final String OWNER_TENANT = "TENANT";
    public static final String OWNER_USER = "USER";
    public static final String OWNER_AGENT = "AGENT";

    public static final String KIND_LONG_TERM = "LONG_TERM";
    public static final String KIND_USER = "USER";
    public static final String KIND_GLOBAL = "GLOBAL";

    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String SOURCE_CHAT = "CHAT";
    public static final String SOURCE_KNOWLEDGE = "KNOWLEDGE";
    public static final String SOURCE_TOOL = "TOOL";
    public static final String SOURCE_AGENT = "AGENT";

    private String ownerType;
    private String ownerId;
    private String memoryKind;
    private String content;
    private String source;
    private BigDecimal confidence;
    private OffsetDateTime expiresAt;
}
