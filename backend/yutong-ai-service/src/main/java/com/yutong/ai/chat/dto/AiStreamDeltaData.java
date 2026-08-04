package com.yutong.ai.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * SSE 流式 delta 事件数据。设计来源: contracts/openapi/openapi.yaml AiStreamDeltaData (line 2206-2210)
 * <p>
 * 携带一段文本增量，客户端按 sequence 顺序拼接即可得到完整 AI 回复。
 * 单次 delta 的 text 长度不超过 8000 字符。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiStreamDeltaData {

    /** 文本增量（逐字或分块） */
    private String text;
}
