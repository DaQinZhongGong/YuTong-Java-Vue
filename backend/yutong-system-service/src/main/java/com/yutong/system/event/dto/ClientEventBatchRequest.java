package com.yutong.system.event.dto;

import com.yutong.system.event.domain.ClientEvent;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 端侧埋点批量上报请求体。设计来源: 94-端侧埋点与体验监控详设
 *
 * <p>POST /api/v1/client-events/batch 接收端侧 SDK 批量上报的事件数组。
 * 单次上限 100 条（由 service 层强制截断）。
 */
@Schema(description = "端侧埋点批量上报请求")
public record ClientEventBatchRequest(

        @Schema(description = "事件列表（单次上限 100 条）", requiredMode = Schema.RequiredMode.REQUIRED)
        List<ClientEvent> events
) {
}
