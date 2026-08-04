package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CT-getCurrentUser 契约测试 (operationId: getCurrentUser)。
 *
 * <p>契约来源: routes.yaml operationIds: [getCurrentUser], GET /api/v1/auth/me
 * <p>控制器: AuthController.me() - 无 @RequiresPermission, 所有认证用户可访问
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功获取当前用户 - admin (200, code=0, userId/username/roles/permissions/dataScopeType)</li>
 *   <li>CT-2 成功获取当前用户 - biz (dataScopeType=SELF)</li>
 *   <li>CT-3 成功获取当前用户 - viewer (dataScopeType=TENANT)</li>
 *   <li>CT-4 traceId 透传 (响应头 + 响应体)</li>
 *   <li>CT-5 响应字段完整性</li>
 *   <li>CT-6 认证失败 (Mock 模式限制, @Disabled)</li>
 * </ol>
 */
@DisplayName("CT-getCurrentUser: GET /api/v1/auth/me 契约测试")
class CTGetCurrentUserTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/auth/me";

    // ===== CT-1: 成功获取当前用户 - admin =====

    @Test
    @DisplayName("CT-1: 成功获取当前用户 - admin (200, code=0, dataScopeType=ALL)")
    void testGetCurrentUserAdmin() throws Exception {
        MvcResult result = performGet(API_PATH, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");
        assertEquals("admin", data.get("username").asText(), "username 应为 admin");
        assertEquals("ALL", data.get("dataScopeType").asText(), "admin dataScopeType 应为 ALL");
        assertTrue(data.get("roles").isArray(), "roles 应为数组");
        assertTrue(data.get("permissions").isArray(), "permissions 应为数组");
    }

    // ===== CT-2: 成功获取当前用户 - biz =====

    @Test
    @DisplayName("CT-2: 成功获取当前用户 - biz (200, code=0, dataScopeType=SELF)")
    void testGetCurrentUserBiz() throws Exception {
        MvcResult result = performGet(API_PATH, mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertEquals("biz_user", data.get("username").asText(), "biz username 应为 biz_user");
        assertEquals("SELF", data.get("dataScopeType").asText(), "biz dataScopeType 应为 SELF");
    }

    // ===== CT-3: 成功获取当前用户 - viewer =====

    @Test
    @DisplayName("CT-3: 成功获取当前用户 - viewer (200, code=0, dataScopeType=TENANT)")
    void testGetCurrentUserViewer() throws Exception {
        MvcResult result = performGet(API_PATH, mockUser("viewer"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertEquals("viewer", data.get("username").asText(), "viewer username 应为 viewer");
        assertEquals("TENANT", data.get("dataScopeType").asText(), "viewer dataScopeType 应为 TENANT");
    }

    // ===== CT-4: traceId 透传 =====

    @Test
    @DisplayName("CT-4: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)")
    void testGetCurrentUserTraceIdPropagation() throws Exception {
        MvcResult result = performGet(API_PATH, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        ResultNode node = parseResult(result);
        assertNotNull(node.traceId(), "响应体应包含 traceId");
        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertNotNull(headerTraceId, "响应头应包含 X-Trace-Id");
        assertEquals(node.traceId(), headerTraceId, "响应头 X-Trace-Id 应与响应体 traceId 一致");
    }

    // ===== CT-5: 响应字段完整性 =====

    @Test
    @DisplayName("CT-5: 响应字段完整性 (userId/username/tenantId/roles/permissions/dataScopeType)")
    void testGetCurrentUserResponseFields() throws Exception {
        MvcResult result = performGet(API_PATH, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");
        assertNotNull(data.get("userId").asText(), "应返回 userId");
        assertNotNull(data.get("username").asText(), "应返回 username");
        assertNotNull(data.get("tenantId").asText(), "应返回 tenantId");
        assertTrue(data.get("roles").isArray(), "roles 应为数组");
        assertTrue(data.get("permissions").isArray(), "permissions 应为数组");
        assertNotNull(data.get("dataScopeType").asText(), "应返回 dataScopeType");
    }

    // ===== CT-6: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-6: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testGetCurrentUserUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }
}
