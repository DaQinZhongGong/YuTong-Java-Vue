package com.yutong.ai.chat.service.llm;

import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;

import java.util.List;

/**
 * LLM 调用请求。对齐 OpenAI /v1/chat/completions 请求体。
 * 设计来源: 13-AI能力设计、P6-02 免费 LLM 供应商集成
 */
public record LlmRequest(
        String model,
        List<LlmMessage> messages,
        Double temperature,
        Integer maxTokens,
        Boolean stream,
        String scenario
) {

    public LlmRequest {
        if (model == null || model.isBlank()) {
            throw new BusinessException(ErrorCode.AI_PROMPT_INVALID, "model 不能为空");
        }
        if (messages == null || messages.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_PROMPT_INVALID, "messages 不能为空");
        }
        if (temperature == null) {
            temperature = 0.7;
        }
        if (stream == null) {
            stream = false;
        }
    }

    public LlmRequest(String model, List<LlmMessage> messages) {
        this(model, messages, 0.7, null, false, null);
    }
}
