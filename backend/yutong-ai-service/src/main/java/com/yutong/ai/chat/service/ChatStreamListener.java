package com.yutong.ai.chat.service;

import com.yutong.ai.chat.dto.AiStreamEvent;

import java.io.IOException;

/**
 * AI 流式输出监听器。SSE 与 WebSocket 共用同一套生成链路。
 */
public interface ChatStreamListener {

    void onEvent(AiStreamEvent event) throws IOException;

    void onComplete();

    void onFailure(Throwable error);
}
