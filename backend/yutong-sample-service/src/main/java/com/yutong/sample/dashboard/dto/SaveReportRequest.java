package com.yutong.sample.dashboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 保存报表请求。设计来源: 42-报表与大屏可视化设计。
 */
public record SaveReportRequest(
        @NotBlank(message = "报表编码不能为空")
        @Size(max = 64, message = "报表编码长度不能超过 64")
        String reportCode,

        @NotBlank(message = "报表名称不能为空")
        @Size(max = 128, message = "报表名称长度不能超过 128")
        String reportName,

        String reportType,

        @NotBlank(message = "布局 JSON 不能为空")
        String layoutJson,

        String datasetBindings,

        String permissionCode,

        String description
) {
}
