package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CT-clearCache 契约测试 (operationId: clearCache)。
 *
 * <p>契约来源: routes.yaml operationIds: [clearCache], DELETE /api/v1/monitor/cache/{cacheName}
 * <p>控制器: MonitorController.clearCache(cacheName) - @RequiresPermission("monitor:cache:clear")
 * <p>权限码: monitor:cache:clear (仅 admin 通配 * 拥有)
 * <p>白名单: {config, dict, report-dataset}; idem 不在白名单
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功清理缓存 - config (200, code=0, cacheName/deletedKeys/clearedAt)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (viewer 无 monitor:cache:clear, 403 AUTH-403001)</li>
 *   <li>CT-4 非白名单 cacheName (idem → 400 SYS-400001)</li>
 *   <li>CT-5 traceId 透传 (响应头 + 响应体)</li>
 *   <li>CT-6 标准错误信封 (非白名单场景)</li>
 * </ol>
 */
@DisplayName("CT-clearCache: DELETE /api/v1/monitor/cache/{cacheName} 契约测试")
class CTClearCacheTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/monitor/cache";
    private static final String ERROR_CODE_FORBIDDEN = "AUTH-403001";
    private static final String ERROR_CODE_PARAM_INVALID = "SYS-400001";

    // ===== CT-1: 成功清理缓存 - config =====

    @Test
    @DisplayName("CT-1: 成功清理缓存 - config (200, code=0, cacheName/deletedKeys/clearedAt)")
    void testClearCacheConfigSuccess() throws Exception {
        MvcResult result = performDelete(API_PATH + "/config", mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");
        assertEquals("config", data.get("cacheName").asText(), "cacheName 应为 config");
        assertNotNull(data.get("deletedKeys"), "应返回 deletedKeys");
        assertNotNull(data.get("clearedAt"), "应返回 clearedAt");
        assertTrue(data.get("deletedKeys").asLong() >= 0, "deletedKeys 应 >= 0");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testClearCacheUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (viewer 无 monitor:cache:clear, 403 AUTH-403001)")
    void testClearCacheForbidden() throws Exception {
        MvcResult result = performDelete(API_PATH + "/config", mockUser("viewer"));

        assertEquals(403, result.getResponse().getStatus(), "viewer 无缓存清理权限, HTTP 应为 403");
        assertError(result, ERROR_CODE_FORBIDDEN);
        assertTraceIdPresent(result);
    }

    // ===== CT-4: 非白名单 cacheName =====

    @Test
    @DisplayName("CT-4: 非白名单 cacheName (idem → 400 SYS-400001)")
    void testClearCacheNonWhitelisted() throws Exception {
        MvcResult result = performDelete(API_PATH + "/idem", mockUser("admin"));

        assertEquals(400, result.getResponse().getStatus(), "非白名单缓存 HTTP 应为 400");
        assertError(result, ERROR_CODE_PARAM_INVALID);
        assertTraceIdPresent(result);
    }

    // ===== CT-5: traceId 透传 =====

    @Test
    @DisplayName("CT-5: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)")
    void testClearCacheTraceIdPropagation() throws Exception {
        MvcResult result = performDelete(API_PATH + "/dict", mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        ResultNode node = parseResult(result);
        assertNotNull(node.traceId(), "响应体应包含 traceId");
        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertNotNull(headerTraceId, "响应头应包含 X-Trace-Id");
        assertEquals(node.traceId(), headerTraceId, "响应头 X-Trace-Id 应与响应体 traceId 一致");
    }

    // ===== CT-6: 标准错误信封 (非白名单场景) =====

    @Test
    @DisplayName("CT-6: 标准错误信封 (非白名单 cacheName, code/message/traceId 字段齐全)")
    void testClearCacheErrorEnvelopeStructure() throws Exception {
        MvcResult result = performDelete(API_PATH + "/nonexistent-cache-ct", mockUser("admin"));

        assertEquals(400, result.getResponse().getStatus());
        ResultNode node = parseResult(result);
        assertNotNull(node.code(), "错误信封必须包含 code 字段");
        assertNotEquals("0", node.code(), "错误响应 code 不应为 0");
        assertEquals(ERROR_CODE_PARAM_INVALID, node.code(), "应返回 SYS-400001");
        assertNotNull(node.message(), "错误信封必须包含 message 字段");
        assertNotNull(node.traceId(), "错误信封必须包含 traceId 字段");
        assertTrue(node.traceId().length() >= 16 && node.traceId().length() <= 64,
                "traceId 长度应在 16-64 之间");
    }
}
