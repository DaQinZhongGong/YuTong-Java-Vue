package com.yutong.ai.ops.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 知识库问答请求。设计来源: 35-样例业务矩阵扩展设计 P2 知识库运营。
 */
public record AskQuestionRequest(
    @NotBlank(message = "知识库 ID 不能为空")
    String kbId,
    @NotBlank(message = "问题不能为空")
    @Size(max = 4000, message = "问题长度不能超过 4000 字符")
    String question,
    Integer topK  // 可空, 默认 5
) {}
