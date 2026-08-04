package com.yutong.ai.chat.service.llm;

import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;

/**
 * LLM 对话消息。对齐 OpenAI ChatCompletion message 结构。
 * 设计来源: 13-AI能力设计、P6-02 免费 LLM 供应商集成
 */
public record LlmMessage(String role, String content) {

    public static final String ROLE_SYSTEM = "system";
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    public LlmMessage {
        if (role == null || role.isBlank()) {
            throw new BusinessException(ErrorCode.AI_PROMPT_INVALID, "role 不能为空");
        }
        if (content == null) {
            throw new BusinessException(ErrorCode.AI_PROMPT_INVALID, "content 不能为空");
        }
    }

    public static LlmMessage system(String content) {
        return new LlmMessage(ROLE_SYSTEM, content);
    }

    public static LlmMessage user(String content) {
        return new LlmMessage(ROLE_USER, content);
    }

    public static LlmMessage assistant(String content) {
        return new LlmMessage(ROLE_ASSISTANT, content);
    }
}
