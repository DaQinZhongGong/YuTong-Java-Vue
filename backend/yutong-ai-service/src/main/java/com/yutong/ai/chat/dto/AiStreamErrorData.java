package com.yutong.ai.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * SSE 流式 error 事件数据。设计来源: contracts/openapi/openapi.yaml AiStreamErrorData (line 2217-2224)
 * <p>
 * 设计文档 13 号 line 141 硬约束: 服务端遇到异常时推送 event: error 事件，不再继续推送 delta。
 * 客户端收到 error 后应展示错误提示，并根据 retryable 决定是否提供重试按钮。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiStreamErrorData {

    /** 错误码（如 AI-500001、AI-429001 等，最长 32 字符） */
    private String code;

    /** 国际化消息 key（前端通过 i18n 解析为本地化文案） */
    private String messageKey;

    /** 追踪 ID，便于客户端反馈问题时定位服务端日志 */
    private String traceId;

    /** 是否可重试（如限流、临时故障为 true，鉴权失败、参数错误为 false） */
    private boolean retryable;
}
