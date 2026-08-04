package com.yutong.sample.dashboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 保存大屏请求。设计来源: 42-报表与大屏可视化设计。
 */
public record SaveDashboardRequest(
        @NotBlank(message = "大屏编码不能为空")
        @Size(max = 64, message = "大屏编码长度不能超过 64")
        String dashboardCode,

        @NotBlank(message = "大屏名称不能为空")
        @Size(max = 128, message = "大屏名称长度不能超过 128")
        String dashboardName,

        Integer canvasWidth,

        Integer canvasHeight,

        String theme,

        String backgroundImage,

        String layoutJson,

        String componentBindings,

        Integer refreshInterval,

        String permissionCode,

        String description
) {
}
