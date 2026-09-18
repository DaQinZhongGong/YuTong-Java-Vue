package com.yutong.ai.chat.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

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

    /** 供应商类型: openai/deepseek/qianwen/zhipu/ollama/dify/coze/custom_api，为空则按 providerCode 自动推断 */
    private String providerType;

    /** 模型类型: chat/image/vector/reranker/audio/text/video/ppt/music，默认 chat */
    private String modelType;

    /** 自定义 API 透传 endpoint（仅 providerType=custom_api 时生效，可覆盖 DB 配置） */
    private String endpoint;

    /** 智能体 ID（移动端 / 用户端透传，用于会话上下文） */
    private String agentId;

    /**
     * 父消息 ID (V052 P2-C 分支链): 本条用户消息接着哪条消息继续, 空 = 链首/默认续尾。
     * 必须指向同一会话消息, 否则 400 (service 层校验)。
     */
    private String parentMessageId;

    /** 额外系统提示（移动端 WS 透传） */
    private String systemPrompt;

    /**
     * 多模态附件（P1-7 多模态视觉）。
     * 当前仅 image 类型走 OpenAI 多模态 content；audio 折叠为文本说明；
     * 为空或缺失时按纯文本消息处理。
     */
    private List<ChatAttachment> attachments;

}
