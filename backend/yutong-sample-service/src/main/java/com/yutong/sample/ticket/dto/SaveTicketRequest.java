package com.yutong.sample.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建工单请求。
 */
@Data
public class SaveTicketRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 128, message = "标题不能超过 128 字")
    private String title;

    @Size(max = 2000, message = "描述不能超过 2000 字")
    private String description;

    @NotBlank(message = "分类不能为空")
    private String categoryId;

    /** 优先级 LOW/MEDIUM/HIGH/URGENT，默认 MEDIUM */
    private String priority;

    /** 报告人 ID（可选，默认当前用户） */
    private String reporterId;
}
