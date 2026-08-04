package com.yutong.ai.chat.service.llm;

import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;

/**
 * LLM 调用响应。统一不同供应商的返回字段。
 * 设计来源: 13-AI能力设计、P6-02 免费 LLM 供应商集成
 */
public record LlmResponse(
        String providerCode,
        String modelCode,
        String content,
        Integer tokenInput,
        Integer tokenOutput,
        Integer latencyMs,
        String finishReason,
        Throwable error
) {

    public LlmResponse {
        if (providerCode == null || providerCode.isBlank()) {
            throw new BusinessException(ErrorCode.AI_PROMPT_INVALID, "providerCode 不能为空");
        }
        if (modelCode == null || modelCode.isBlank()) {
            throw new BusinessException(ErrorCode.AI_PROMPT_INVALID, "modelCode 不能为空");
        }
    }

    public boolean isError() {
        return error != null;
    }

    public static LlmResponse error(String providerCode, String modelCode, Throwable error) {
        return new LlmResponse(
                providerCode, modelCode, null, 0, 0, 0, null, error);
    }
}
