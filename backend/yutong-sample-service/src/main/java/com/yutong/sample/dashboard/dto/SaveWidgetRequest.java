package com.yutong.sample.dashboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 保存 Widget 请求。设计来源: 42-报表与大屏可视化设计。
 */
public record SaveWidgetRequest(
        @NotBlank(message = "Widget 编码不能为空")
        @Size(max = 64, message = "Widget 编码长度不能超过 64")
        String widgetCode,

        @NotBlank(message = "Widget 名称不能为空")
        @Size(max = 128, message = "Widget 名称长度不能超过 128")
        String widgetName,

        @NotBlank(message = "Widget 类型不能为空")
        String widgetType,

        String datasetCode,

        String propsJson,

        String styleJson,

        String description
) {
}
