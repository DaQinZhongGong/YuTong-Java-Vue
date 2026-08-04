package com.yutong.ai.chat.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * AI 流式事件信封。设计来源: 13-AI能力设计 line 137-143/252、contracts/openapi/openapi.yaml AiStreamEvent
 * <p>
 * 事件类型:
 * - meta:     流开始，携带 modelCode/scenario
 * - delta:    文本增量（逐字或分块）
 * - citation: 引用来源（RAG 检索结果，可多次推送）
 * - tool:     工具调用摘要（如 generate_page_draft）
 * - done:     流正常结束，携带 token 用量和耗时
 * - error:    流异常终止，携带 code/messageKey/traceId/retryable
 * <p>
 * 硬约束（13 号文档 line 252）:
 * - 每个事件必须带 eventId / sequence / conversationId / messageId
 * - sequence 从 0 递增
 * - 断线重连仅允许在服务端仍保留生成上下文时使用 Last-Event-ID，否则客户端查询会话详情恢复
 */
@Getter
@Setter
public class AiStreamEvent {

    /** 事件 ID（用于 Last-Event-ID 重连，格式: {messageId}-{sequence}） */
    private String eventId;

    /** 事件类型 */
    private String eventType;

    /** 递增序列号，从 0 开始 */
    private int sequence;

    /** 会话 ID */
    private String conversationId;

    /** 消息 ID（AI 回复消息的 ID，meta 事件中即给出，后续事件复用） */
    private String messageId;

    /** 事件数据（按 eventType 不同为不同 DTO） */
    private Object data;

    public AiStreamEvent() {
    }

    public AiStreamEvent(String eventId, String eventType, int sequence,
                         String conversationId, String messageId, Object data) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.sequence = sequence;
        this.conversationId = conversationId;
        this.messageId = messageId;
        this.data = data;
    }

    /** 事件类型常量（对齐 openapi.yaml AiStreamEvent.eventType 枚举） */
    public static final String TYPE_META = "meta";
    public static final String TYPE_DELTA = "delta";
    public static final String TYPE_CITATION = "citation";
    public static final String TYPE_TOOL = "tool";
    public static final String TYPE_DONE = "done";
    public static final String TYPE_ERROR = "error";

    /** 构造事件 ID（{messageId}-{sequence}） */
    public static String buildEventId(String messageId, int sequence) {
        return messageId + "-" + sequence;
    }
}
