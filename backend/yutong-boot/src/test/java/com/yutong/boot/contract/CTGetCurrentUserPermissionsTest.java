package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CT-getCurrentUserPermissions 契约测试 (operationId: getCurrentUserPermissions)。
 *
 * <p>契约来源: routes.yaml headlessOperations: getCurrentUserPermissions, GET /api/v1/auth/permissions
 * <p>控制器: AuthController.getCurrentUserPermissions() - 无 @RequiresPermission
 * 返回 roles/permissions/dataScopeType
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功获取权限 - admin (200, code=0, permissions 含 *)</li>
 *   <li>CT-2 成功获取权限 - viewer (dataScopeType=TENANT)</li>
 *   <li>CT-3 traceId 透传 (响应头 + 响应体)</li>
 *   <li>CT-4 响应字段完整性 (roles/permissions/dataScopeType)</li>
 *   <li>CT-5 权限矩阵差异 (admin 通配 * vs viewer 有限权限集)</li>
 *   <li>CT-6 认证失败 (Mock 模式限制, @Disabled)</li>
 * </ol>
 */
@DisplayName("CT-getCurrentUserPermissions: GET /api/v1/auth/permissions 契约测试")
class CTGetCurrentUserPermissionsTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/auth/permissions";

    // ===== CT-1: 成功获取权限 - admin =====

    @Test
    @DisplayName("CT-1: 成功获取权限 - admin (200, code=0, permissions 含通配 *)")
    void testGetCurrentUserPermissionsAdmin() throws Exception {
        MvcResult result = performGet(API_PATH, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");
        assertTrue(data.get("roles").isArray(), "roles 应为数组");
        assertTrue(data.get("permissions").isArray(), "permissions 应为数组");
        assertEquals("ALL", data.get("dataScopeType").asText(), "admin dataScopeType 应为 ALL");

        // admin 拥有通配权限 *
        boolean hasWildcard = false;
        for (JsonNode p : data.get("permissions")) {
            if ("*".equals(p.asText())) hasWildcard = true;
        }
        assertTrue(hasWildcard, "admin permissions 应包含通配 *");
    }

    // ===== CT-2: 成功获取权限 - viewer =====

    @Test
    @DisplayName("CT-2: 成功获取权限 - viewer (200, code=0, dataScopeType=TENANT)")
    void testGetCurrentUserPermissionsViewer() throws Exception {
        MvcResult result = performGet(API_PATH, mockUser("viewer"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertEquals("TENANT", data.get("dataScopeType").asText(), "viewer dataScopeType 应为 TENANT");
        assertTrue(data.get("permissions").isArray(), "permissions 应为数组");
        assertTrue(data.get("permissions").size() > 0, "viewer 应有具体权限码");
    }

    // ===== CT-3: traceId 透传 =====

    @Test
    @DisplayName("CT-3: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)")
    void testGetCurrentUserPermissionsTraceIdPropagation() throws Exception {
        MvcResult result = performGet(API_PATH, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        ResultNode node = parseResult(result);
        assertNotNull(node.traceId(), "响应体应包含 traceId");
        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertNotNull(headerTraceId, "响应头应包含 X-Trace-Id");
        assertEquals(node.traceId(), headerTraceId, "响应头 X-Trace-Id 应与响应体 traceId 一致");
    }

    // ===== CT-4: 响应字段完整性 =====

    @Test
    @DisplayName("CT-4: 响应字段完整性 (roles/permissions/dataScopeType)")
    void testGetCurrentUserPermissionsResponseFields() throws Exception {
        MvcResult result = performGet(API_PATH, mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");
        assertTrue(data.get("roles").isArray(), "roles 应为数组");
        assertTrue(data.get("permissions").isArray(), "permissions 应为数组");
        assertNotNull(data.get("dataScopeType").asText(), "应返回 dataScopeType");
    }

    // ===== CT-5: 权限矩阵差异 (admin vs viewer) =====

    @Test
    @DisplayName("CT-5: 权限矩阵差异 (admin 通配 * vs viewer 有限权限集)")
    void testGetCurrentUserPermissionsMatrixDifference() throws Exception {
        MvcResult adminResult = performGet(API_PATH, mockUser("admin"));
        MvcResult viewerResult = performGet(API_PATH, mockUser("viewer"));

        assertEquals(200, adminResult.getResponse().getStatus());
        assertEquals(200, viewerResult.getResponse().getStatus());
        assertSuccess(adminResult);
        assertSuccess(viewerResult);

        JsonNode adminData = parseResult(adminResult).data();
        JsonNode viewerData = parseResult(viewerResult).data();

        // admin 有通配 *, viewer 没有
        boolean adminHasWildcard = false;
        for (JsonNode p : adminData.get("permissions")) {
            if ("*".equals(p.asText())) adminHasWildcard = true;
        }
        boolean viewerHasWildcard = false;
        for (JsonNode p : viewerData.get("permissions")) {
            if ("*".equals(p.asText())) viewerHasWildcard = true;
        }
        assertTrue(adminHasWildcard, "admin 应有通配权限 *");
        assertFalse(viewerHasWildcard, "viewer 不应有通配权限 *");
        assertNotEquals(adminData.get("dataScopeType").asText(),
                viewerData.get("dataScopeType").asText(),
                "admin 与 viewer dataScopeType 应不同");
    }

    // ===== CT-6: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-6: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testGetCurrentUserPermissionsUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }
}
