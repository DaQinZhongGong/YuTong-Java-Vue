package com.yutong.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 平台业务指标服务。设计来源: 62-可观测性指标日志链路详设
 * 提供业务指标埋点: biz_request / ai / lowcode / file_upload / message 等
 * 所有指标带 tenantId tag 支持多租户聚合
 */
@Component
public class PlatformMetrics {

    private final MeterRegistry registry;
    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();

    public PlatformMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    // ===== 业务申请单指标 =====

    public void recordBizRequestCreated(String tenantId, String bizType) {
        counter("biz_request_created_total", "tenantId", tenantId, "bizType", bizType).increment();
    }

    public void recordBizRequestStatus(String tenantId, String bizType, String status) {
        counter("biz_request_status_total", "tenantId", tenantId, "bizType", bizType, "status", status).increment();
    }

    // ===== AI 指标 =====

    public void recordAiRequest(String tenantId, String scenario, String status, Duration duration) {
        String safeScenario = scenario != null ? scenario : "none";
        Timer.builder("ai_request_duration_seconds")
                .tag("tenantId", tenantId)
                .tag("scenario", safeScenario)
                .tag("status", status)
                .register(registry)
                .record(duration);
        counter("ai_request_total", "tenantId", tenantId, "scenario", safeScenario, "status", status).increment();
    }

    public void recordAiTokenUsage(String tenantId, String provider, long tokens) {
        Counter c = Counter.builder("ai_token_usage_total")
                .tag("tenantId", tenantId)
                .tag("provider", provider)
                .register(registry);
        c.increment(tokens);
    }

    public void recordAiCost(String tenantId, String provider, double amount) {
        Counter c = Counter.builder("ai_cost_amount_total")
                .tag("tenantId", tenantId)
                .tag("provider", provider)
                .register(registry);
        c.increment(amount);
    }

    public void recordAiToolCall(String tenantId, String toolName, String status) {
        counter("ai_tool_call_total", "tenantId", tenantId, "toolName", toolName, "status", status).increment();
    }

    // ===== 低代码指标 =====

    public void recordLowcodeGenerateTask(String tenantId, String scope, String status, Duration duration) {
        Timer.builder("lowcode_generate_duration_seconds")
                .tag("tenantId", tenantId)
                .tag("scope", scope)
                .tag("status", status)
                .register(registry)
                .record(duration);
        counter("lowcode_generate_task_total", "tenantId", tenantId, "scope", scope, "status", status).increment();
    }

    // ===== 文件上传指标 =====

    public void recordFileUpload(String tenantId, String status) {
        counter("file_upload_total", "tenantId", tenantId, "status", status).increment();
    }

    // ===== 导入导出指标 =====

    public void recordImportExportTask(String tenantId, String type, String status) {
        counter("import_export_task_total", "tenantId", tenantId, "type", type, "status", status).increment();
    }

    // ===== 消息指标 =====

    public void recordMessageUnread(String tenantId, long count) {
        Counter c = Counter.builder("message_unread_total")
                .tag("tenantId", tenantId)
                .register(registry);
        c.increment(count);
    }

    // ===== HTTP 错误指标 =====

    public void recordHttpError(String module, String errorCode) {
        counter("http_server_errors_total", "module", module, "errorCode", errorCode).increment();
    }

    // ===== 工具方法 =====

    private Counter counter(String name, String... tags) {
        String key = name + ":" + String.join("|", tags);
        return counters.computeIfAbsent(key, k ->
                Counter.builder(name).tags(tags).register(registry));
    }
}
