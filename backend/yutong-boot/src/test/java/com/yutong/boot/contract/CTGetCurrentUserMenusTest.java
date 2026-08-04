package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CT-getCurrentUserMenus 契约测试 (operationId: getCurrentUserMenus)。
 *
 * <p>契约来源: routes.yaml headlessOperations: getCurrentUserMenus, GET /api/v1/auth/menus
 * <p>控制器: AuthController.getCurrentUserMenus() - 无 @RequiresPermission, 返回 List.of() (占位实现)
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功获取菜单 - admin (200, code=0, data 为数组)</li>
 *   <li>CT-2 成功获取菜单 - viewer (200, code=0)</li>
 *   <li>CT-3 traceId 透传 (响应头 + 响应体)</li>
 *   <li>CT-4 响应结构 (data 为数组)</li>
 *   <li>CT-5 认证失败 (Mock 模式限制, @Disabled)</li>
 * </ol>
 *
 * <p>注意: 当前 getCurrentUserMenus 基于用户权限构建菜单树 (AuthController.buildUserMenuTree)。
 * admin (权限 *) 可见全部菜单; 普通用户仅可见有权限的菜单项; 父节点无可见子菜单时被剔除。
 */
@DisplayName("CT-getCurrentUserMenus: GET /api/v1/auth/menus 契约测试")
class CTGetCurrentUserMenusTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/auth/menus";

    // ===== CT-1: 成功获取菜单 - admin =====

    @Test
    @DisplayName("CT-1: 成功获取菜单 - admin (200, code=0, data 为数组)")
    void testGetCurrentUserMenusAdmin() throws Exception {
        MvcResult result = performGet(API_PATH, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");
        assertTrue(data.isArray(), "menus data 应为数组");
    }

    // ===== CT-2: 成功获取菜单 - viewer =====

    @Test
    @DisplayName("CT-2: 成功获取菜单 - viewer (200, code=0)")
    void testGetCurrentUserMenusViewer() throws Exception {
        MvcResult result = performGet(API_PATH, mockUser("viewer"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertTrue(data.isArray(), "menus data 应为数组");
    }

    // ===== CT-3: traceId 透传 =====

    @Test
    @DisplayName("CT-3: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)")
    void testGetCurrentUserMenusTraceIdPropagation() throws Exception {
        MvcResult result = performGet(API_PATH, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        ResultNode node = parseResult(result);
        assertNotNull(node.traceId(), "响应体应包含 traceId");
        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertNotNull(headerTraceId, "响应头应包含 X-Trace-Id");
        assertEquals(node.traceId(), headerTraceId, "响应头 X-Trace-Id 应与响应体 traceId 一致");
    }

    // ===== CT-4: 响应结构 =====

    @Test
    @DisplayName("CT-4: 响应结构 (data 为数组, admin 可见全部菜单树)")
    void testGetCurrentUserMenusStructure() throws Exception {
        MvcResult result = performGet(API_PATH, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");
        assertTrue(data.isArray(), "menus data 应为数组");
        // admin (权限 *) 基于权限构建菜单树, 应返回非空菜单列表
        assertTrue(data.size() > 0, "admin 应可见非空菜单树");
        // 每个菜单节点应包含 id/title/path/children 字段
        for (JsonNode menu : data) {
            assertNotNull(menu.get("id"), "菜单节点应包含 id");
            assertNotNull(menu.get("title"), "菜单节点应包含 title");
            assertNotNull(menu.get("path"), "菜单节点应包含 path");
            assertNotNull(menu.get("children"), "菜单节点应包含 children");
        }
    }

    // ===== CT-5: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-5: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testGetCurrentUserMenusUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }
}
