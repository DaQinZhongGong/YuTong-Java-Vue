package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CT-getCacheOverview 契约测试 (operationId: getCacheOverview)。
 *
 * <p>契约来源: routes.yaml operationIds: [getCacheOverview], GET /api/v1/monitor/cache
 * <p>控制器: MonitorController.getCacheOverview() - @RequiresPermission("monitor:cache:view")
 * <p>权限码: monitor:cache:view (仅 admin 通配 * 拥有)
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功获取缓存概览 - admin (200, code=0, status/provider/caches)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (viewer 无 monitor:cache:view, 403 AUTH-403001)</li>
 *   <li>CT-4 traceId 透传 (响应头 + 响应体)</li>
 *   <li>CT-5 缓存结构 (config/dict/idem/report-dataset + clearable 标记)</li>
 *   <li>CT-6 全局命中率字段 (globalHitRate)</li>
 * </ol>
 */
@DisplayName("CT-getCacheOverview: GET /api/v1/monitor/cache 契约测试")
class CTGetCacheOverviewTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/monitor/cache";
    private static final String ERROR_CODE_FORBIDDEN = "AUTH-403001";

    // ===== CT-1: 成功获取缓存概览 - admin =====

    @Test
    @DisplayName("CT-1: 成功获取缓存概览 - admin (200, code=0, status/provider/caches)")
    void testGetCacheOverviewAdmin() throws Exception {
        MvcResult result = performGet(API_PATH, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");
        assertNotNull(data.get("status"), "应返回 status");
        assertNotNull(data.get("provider"), "应返回 provider");
        assertNotNull(data.get("endpoint"), "应返回 endpoint");
        assertNotNull(data.get("globalHitRate"), "应返回 globalHitRate");
        assertNotNull(data.get("checkedAt"), "应返回 checkedAt");
        assertTrue(data.get("caches").isArray(), "caches 应为数组");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testGetCacheOverviewUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (viewer 无 monitor:cache:view, 403 AUTH-403001)")
    void testGetCacheOverviewForbidden() throws Exception {
        MvcResult result = performGet(API_PATH, mockUser("viewer"));

        assertEquals(403, result.getResponse().getStatus(), "viewer 无缓存查看权限, HTTP 应为 403");
        assertError(result, ERROR_CODE_FORBIDDEN);
        assertTraceIdPresent(result);
    }

    // ===== CT-4: traceId 透传 =====

    @Test
    @DisplayName("CT-4: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)")
    void testGetCacheOverviewTraceIdPropagation() throws Exception {
        MvcResult result = performGet(API_PATH, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        ResultNode node = parseResult(result);
        assertNotNull(node.traceId(), "响应体应包含 traceId");
        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertNotNull(headerTraceId, "响应头应包含 X-Trace-Id");
        assertEquals(node.traceId(), headerTraceId, "响应头 X-Trace-Id 应与响应体 traceId 一致");
    }

    // ===== CT-5: 缓存结构 =====

    @Test
    @DisplayName("CT-5: 缓存结构 (config/dict/idem/report-dataset + clearable 标记)")
    void testGetCacheOverviewCacheStructure() throws Exception {
        MvcResult result = performGet(API_PATH, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        ArrayNode caches = (ArrayNode) data.get("caches");
        assertNotNull(caches, "caches 不应为 null");
        assertTrue(caches.size() >= 4, "应至少包含 4 个缓存 (config/dict/idem/report-dataset)");

        // 验证缓存名称集合 + clearable 标记
        java.util.Set<String> names = new java.util.HashSet<>();
        for (JsonNode c : caches) {
            assertNotNull(c.get("cacheName"), "缓存应包含 cacheName");
            assertNotNull(c.get("keyPrefix"), "缓存应包含 keyPrefix");
            assertNotNull(c.get("clearable"), "缓存应包含 clearable");
            assertNotNull(c.get("keyCount"), "缓存应包含 keyCount");
            names.add(c.get("cacheName").asText());
        }
        assertTrue(names.contains("config"), "应包含 config 缓存");
        assertTrue(names.contains("dict"), "应包含 dict 缓存");
        assertTrue(names.contains("idem"), "应包含 idem 缓存");
        assertTrue(names.contains("report-dataset"), "应包含 report-dataset 缓存");

        // 白名单缓存 clearable=true (config/dict/report-dataset), idem clearable=false
        for (JsonNode c : caches) {
            String name = c.get("cacheName").asText();
            boolean clearable = c.get("clearable").asBoolean();
            if ("idem".equals(name)) {
                assertFalse(clearable, "idem 不在白名单, clearable 应为 false");
            } else if ("config".equals(name) || "dict".equals(name) || "report-dataset".equals(name)) {
                assertTrue(clearable, name + " 在白名单, clearable 应为 true");
            }
        }
    }

    // ===== CT-6: 全局命中率字段 =====

    @Test
    @DisplayName("CT-6: 全局命中率字段 (globalHitRate 为 0-1 之间数值)")
    void testGetCacheOverviewGlobalHitRate() throws Exception {
        MvcResult result = performGet(API_PATH, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        double hitRate = data.get("globalHitRate").asDouble();
        assertTrue(hitRate >= 0.0 && hitRate <= 1.0, "globalHitRate 应在 0-1 之间, 实际: " + hitRate);
    }
}
