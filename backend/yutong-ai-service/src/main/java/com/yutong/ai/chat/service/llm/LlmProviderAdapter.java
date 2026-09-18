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

    /**
     * 流式调用 LLM，逐 token 回调。
     * 默认实现降级为同步 chat 后分块回调（保证旧适配器兼容）。
     *
     * @param request  LLM 请求（stream=true）
     * @param onDelta  每收到一个文本增量回调
     * @param onFinish 流正常结束回调（携带 finishReason/usage）
     * @param onError  流异常回调
     */
    default void stream(LlmRequest request,
                        java.util.function.Consumer<String> onDelta,
                        java.util.function.Consumer<StreamFinish> onFinish,
                        java.util.function.Consumer<Throwable> onError) {
        try {
            LlmResponse resp = chat(request);
            if (resp.isError()) {
                onError.accept(resp.error());
                return;
            }
            String content = resp.content() == null ? "" : resp.content();
            // 按 80 字符分块模拟流式，保持与前端逐字渲染兼容
            int chunk = 80;
            for (int i = 0; i < content.length(); i += chunk) {
                onDelta.accept(content.substring(i, Math.min(i + chunk, content.length())));
            }
            onFinish.accept(new StreamFinish(resp.finishReason(), resp.tokenInput(), resp.tokenOutput()));
        } catch (Throwable t) {
            onError.accept(t);
        }
    }

    /**
     * 流结束信息。
     */
    record StreamFinish(String finishReason, Integer tokenInput, Integer tokenOutput) {}
}
