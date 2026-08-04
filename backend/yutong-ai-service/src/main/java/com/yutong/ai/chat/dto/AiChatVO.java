package com.yutong.ai.chat.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * AI 对话响应。设计来源: 13-AI能力设计
 * 包含回复内容、引用来源、token 用量和耗时
 */
@Getter
@Setter
public class AiChatVO {

    private String conversationId;

    private String messageId;

    /** AI 回复内容 */
    private String content;

    /** 引用来源列表（RAG 检索结果） */
    private List<Citation> citations;

    /** 场景 */
    private String scenario;

    /** 模型编码 */
    private String modelCode;

    /** token 用量 */
    private Integer tokenInput;

    private Integer tokenOutput;

    /** 响应耗时 ms */
    private Integer latencyMs;

    private OffsetDateTime createdTime;

    @Getter
    @Setter
    public static class Citation {
        private String documentId;
        private String chunkId;
        private String docTitle;
        private String sectionPath;
        private String sourceType;
        private double score;
    }
}
