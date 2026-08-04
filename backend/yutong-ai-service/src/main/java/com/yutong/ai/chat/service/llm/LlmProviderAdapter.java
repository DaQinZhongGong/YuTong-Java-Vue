package com.yutong.ai.chat.service.llm;

/**
 * LLM 供应商适配器接口。
 * 设计来源: P6-02 免费 LLM 供应商集成
 */
public interface LlmProviderAdapter {

    /**
     * 是否支持该供应商协议。
     *
     * @param protocol 供应商协议编码
     * @return true 表示支持
     */
    boolean supports(String protocol);

    /**
     * 同步调用 LLM 获取完整回复。
     *
     * @param request LLM 请求
     * @return LLM 响应
     */
    LlmResponse chat(LlmRequest request);
}
