package com.yutong.ai.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * SSE 流式 done 事件数据。设计来源: contracts/openapi/openapi.yaml AiStreamDoneData (line 2211-2216)
 * <p>
 * 流正常结束时由服务端推送一次。客户端收到 done 后停止拼接 delta，
 * 根据 requiresHumanConfirmation 决定是否提示用户进入草稿差异预览流程。
 * <p>
 * 设计文档 13 号 line 143 硬约束: 流式 token 不写 ai_cost_log，等到流结束后一次性写入完整 token 用量。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiStreamDoneData {

    /** token 用量和耗时（流结束后一次性统计） */
    private AiUsage usage;

    /** 是否需要人工确认（涉及工具调用且风险等级 >= A2 时为 true） */
    private boolean requiresHumanConfirmation;

    /**
     * 本次 assistant 回复的消息 ID (V049 P2-C, 前端据此挂载点赞/点踩, 可空保持兼容)。
     * 注意 @AllArgsConstructor 参数顺序: (usage, requiresHumanConfirmation, messageId)。
     */
    private String messageId;

    /** token 用量内部对象，对齐 openapi.yaml AiUsageVO */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiUsage {
        /** 输入 token 数 */
        private int inputTokens;
        /** 输出 token 数 */
        private int outputTokens;
        /** 响应耗时 ms */
        private long latencyMs;
        /** 估算成本（字符串避免精度丢失，可空） */
        private String estimatedCost;
        /** 币种（如 CNY/USD，可空） */
        private String currency;
    }
}
