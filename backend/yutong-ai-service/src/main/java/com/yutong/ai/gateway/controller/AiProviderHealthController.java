package com.yutong.ai.gateway.controller;

import com.yutong.ai.gateway.dto.ProviderHealthResult;
import com.yutong.ai.gateway.service.AiProviderHealthService;
import com.yutong.common.response.Result;
import com.yutong.auth.RequiresPermission;
import com.yutong.common.trace.TraceContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 供应商健康检查接口。
 * 设计来源: P6-02 免费 LLM 供应商集成 - 健康检查能力
 * <p>
 * 提供两个端点:
 * - POST /api/v1/ai/providers/health-check  触发健康检查
 * - GET  /api/v1/ai/providers/health        返回最近缓存的健康状态
 */
@Tag(name = "AI-供应商管理")
@RestController
@RequestMapping("/api/v1/ai/providers")
public class AiProviderHealthController {

    private final AiProviderHealthService healthService;

    public AiProviderHealthController(AiProviderHealthService healthService) {
        this.healthService = healthService;
    }

    @Operation(summary = "触发供应商健康检查", operationId = "checkAiProvidersHealth")
    @PostMapping("/health-check")
    public Result<List<ProviderHealthResult>> healthCheck() {
        return Result.ok(healthService.checkAllEnabledProviders(), TraceContext.getTraceId());
    }

    @Operation(summary = "查询最近供应商健康状态", operationId = "getAiProvidersHealth")
    @GetMapping("/health")
    public Result<List<ProviderHealthResult>> getHealth() {
        return Result.ok(healthService.getLatestHealth(), TraceContext.getTraceId());
    }
}
