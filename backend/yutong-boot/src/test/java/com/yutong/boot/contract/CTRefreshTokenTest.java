package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CT-refreshToken 契约测试 (operationId: refreshToken)。
 *
 * <p>契约来源: routes.yaml headlessOperations: refreshToken, POST /api/v1/auth/refresh-token
 * <p>控制器: AuthController.refreshToken() - 无 @RequiresPermission
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功刷新 Token - admin (200, code=0, userId/username/roles/permissions)</li>
 *   <li>CT-2 成功刷新 Token - biz (200, code=0)</li>
 *   <li>CT-3 traceId 透传 (响应头 + 响应体)</li>
 *   <li>CT-4 响应字段完整性</li>
 *   <li>CT-5 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-6 真实 Token 轮换 (Mock 模式限制, @Disabled 待真实 AuthAdapter)</li>
 * </ol>
 *
 * <p>注意: Mock 模式下 refreshToken 仅读取当前用户并返回, 不做真实 token 轮换。
 * 真实 refresh-token family 轮换 + 旧 token 失效逻辑需真实 AuthAdapter 接入后验证。
 */
@DisplayName("CT-refreshToken: POST /api/v1/auth/refresh-token 契约测试")
class CTRefreshTokenTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/auth/refresh-token";

    // ===== CT-1: 成功刷新 Token - admin =====

    @Test
    @DisplayName("CT-1: 成功刷新 Token - admin (200, code=0, userId/username/roles/permissions)")
    void testRefreshTokenAdmin() throws Exception {
        MvcResult result = performPost(API_PATH, null, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");
        assertNotNull(data.get("userId"), "userId 不应为 null");
        assertNotNull(data.get("username"), "username 不应为 null");
        assertNotNull(data.get("tenantId"), "tenantId 不应为 null");
        assertTrue(data.get("roles").isArray(), "roles 应为数组");
        assertTrue(data.get("permissions").isArray(), "permissions 应为数组");
    }

    // ===== CT-2: 成功刷新 Token - biz =====

    @Test
    @DisplayName("CT-2: 成功刷新 Token - biz (200, code=0)")
    void testRefreshTokenBiz() throws Exception {
        MvcResult result = performPost(API_PATH, null, mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertEquals("biz_user", data.get("username").asText(), "biz username 应为 biz_user");
    }

    // ===== CT-3: traceId 透传 =====

    @Test
    @DisplayName("CT-3: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)")
    void testRefreshTokenTraceIdPropagation() throws Exception {
        MvcResult result = performPost(API_PATH, null, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        ResultNode node = parseResult(result);
        assertNotNull(node.traceId(), "响应体应包含 traceId");
        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertNotNull(headerTraceId, "响应头应包含 X-Trace-Id");
        assertEquals(node.traceId(), headerTraceId, "响应头 X-Trace-Id 应与响应体 traceId 一致");
    }

    // ===== CT-4: 响应字段完整性 =====

    @Test
    @DisplayName("CT-4: 响应字段完整性 (userId/username/tenantId/roles/permissions)")
    void testRefreshTokenResponseFields() throws Exception {
        MvcResult result = performPost(API_PATH, null, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");
        assertNotNull(data.get("userId").asText(), "应返回 userId");
        assertNotNull(data.get("username").asText(), "应返回 username");
        assertNotNull(data.get("tenantId").asText(), "应返回 tenantId");
        assertTrue(data.get("roles").isArray(), "roles 应为数组");
        assertTrue(data.get("permissions").isArray(), "permissions 应为数组");
    }

    // ===== CT-5: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-5: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testRefreshTokenUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-6: 真实 Token 轮换 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-6: 真实 Token 轮换 (refresh-token family 轮换 + 旧 token 失效, @Disabled)")
    @org.junit.jupiter.api.Disabled("Mock 模式 refreshToken 仅读取当前用户不做真实 token 轮换; " +
            "refresh-token family 轮换 + 旧 token 失效逻辑需真实 AuthAdapter 接入后验证")
    void testRefreshTokenRotation() {
        // Mock 模式限制: 真实 refresh-token family 轮换需真实 AuthAdapter
    }
}
