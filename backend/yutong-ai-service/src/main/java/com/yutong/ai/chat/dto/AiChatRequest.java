package com.yutong.ai.chat.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * AI 对话请求。设计来源: 13-AI能力设计 chatWithAssistant
 * 第一版不支持 SSE 流式，只返回完整响应。
 * 内容协商: Accept: application/json → 完整响应；text/event-stream → SSE（v0.5+）
 */
@Getter
@Setter
public class AiChatRequest {

    /** 会话 ID，为空表示新会话 */
    private String conversationId;

    /** 用户问题 */
    private String message;

    /** 场景: PLATFORM_QA / FIELD_SUGGEST / PAGE_GENERATE / SQL_EXPLAIN / OPS_DIAGNOSE */
    private String scenario;

    /** 关联知识库 ID，用于 RAG 检索 */
    private String kbId;

    /** 幂等键 */
    private String idempotencyKey;

    /** 指定供应商编码，为空则自动选择 */
    private String providerCode;

    /** 指定模型编码，为空则使用供应商默认模型 */
    private String modelCode;
}
