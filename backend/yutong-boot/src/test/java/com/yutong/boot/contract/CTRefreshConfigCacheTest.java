package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CT-refreshConfigCache 契约测试 (operationId: refreshConfigCache)。
 *
 * <p>契约来源: routes.yaml operationIds: [refreshConfigCache], PUT /api/v1/configs/refresh-cache
 * <p>控制器: SysConfigController.refreshCache() - @RequiresPermission("system:config:refresh-cache")
 * <p>权限码: system:config:refresh-cache (仅 admin 通配 * 拥有)
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功刷新配置缓存 (admin, 200, code=0, data=null)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (viewer 无 system:config:refresh-cache, 403 AUTH-403001)</li>
 *   <li>CT-4 traceId 透传 (响应头 + 响应体)</li>
 *   <li>CT-5 标准成功响应信封 (code=0/message/data=null/traceId)</li>
 *   <li>CT-6 标准错误信封 (权限拒绝场景, code/message/traceId 字段齐全)</li>
 * </ol>
 *
 * <p>注意: 系统配置已有种子数据, refreshCache 清理 Redis yutong:config: 缓存, 无需创建测试数据。
 */
@DisplayName("CT-refreshConfigCache: PUT /api/v1/configs/refresh-cache 契约测试")
class CTRefreshConfigCacheTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/configs/refresh-cache";
    private static final String ERROR_CODE_FORBIDDEN = "AUTH-403001";

    // ===== CT-1: 成功刷新配置缓存 =====

    @Test
    @DisplayName("CT-1: 成功刷新配置缓存 (admin, 200, code=0, data=null)")
    void testRefreshConfigCacheSuccess() throws Exception {
        MvcResult result = performPut(API_PATH, null, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertNull(data, "refreshCache 成功响应 data 应为 null");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testRefreshConfigCacheUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (viewer 无 system:config:refresh-cache, 403 AUTH-403001)")
    void testRefreshConfigCacheForbidden() throws Exception {
        MvcResult result = performPut(API_PATH, null, mockUser("viewer"));

        assertEquals(403, result.getResponse().getStatus(), "viewer 无刷新缓存权限, HTTP 应为 403");
        assertError(result, ERROR_CODE_FORBIDDEN);
        assertTraceIdPresent(result);
    }

    // ===== CT-4: traceId 透传 =====

    @Test
    @DisplayName("CT-4: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)")
    void testRefreshConfigCacheTraceIdPropagation() throws Exception {
        MvcResult result = performPut(API_PATH, null, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        ResultNode node = parseResult(result);
        assertNotNull(node.traceId(), "响应体应包含 traceId");
        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertNotNull(headerTraceId, "响应头应包含 X-Trace-Id");
        assertEquals(node.traceId(), headerTraceId, "响应头 X-Trace-Id 应与响应体 traceId 一致");
    }

    // ===== CT-5: 标准成功响应信封 =====

    @Test
    @DisplayName("CT-5: 标准成功响应信封 (code=0/message/data=null/traceId)")
    void testRefreshConfigCacheSuccessEnvelope() throws Exception {
        MvcResult result = performPut(API_PATH, null, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        ResultNode node = parseResult(result);
        assertEquals("0", node.code(), "成功响应 code 应为 0");
        assertNotNull(node.message(), "成功响应应包含 message 字段");
        assertNull(node.data(), "refreshCache 成功响应 data 应为 null");
        assertNotNull(node.traceId(), "响应应包含 traceId");
        assertTrue(node.traceId().length() >= 16 && node.traceId().length() <= 64,
                "traceId 长度应在 16-64 之间");
    }

    // ===== CT-6: 标准错误信封 (权限拒绝场景) =====

    @Test
    @DisplayName("CT-6: 标准错误信封 (权限拒绝场景, code/message/traceId 字段齐全)")
    void testRefreshConfigCacheErrorEnvelopeStructure() throws Exception {
        MvcResult result = performPut(API_PATH, null, mockUser("viewer"));

        ResultNode node = parseResult(result);
        assertNotNull(node.code(), "错误信封必须包含 code 字段");
        assertNotEquals("0", node.code(), "错误响应 code 不应为 0");
        assertEquals(ERROR_CODE_FORBIDDEN, node.code(), "应返回 AUTH-403001");
        assertNotNull(node.message(), "错误信封必须包含 message 字段");
        assertNotNull(node.traceId(), "错误信封必须包含 traceId 字段");
        assertTrue(node.traceId().length() >= 16 && node.traceId().length() <= 64,
                "traceId 长度应在 16-64 之间");
    }
}
