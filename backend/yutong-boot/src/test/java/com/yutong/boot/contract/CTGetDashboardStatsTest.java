package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CT-getDashboardStats 契约测试 (operationId: getDashboardStats)。
 *
 * <p>契约来源: routes.yaml operationIds: [getDashboardStats], GET /api/v1/workbench/stats
 * <p>控制器: WorkbenchController.getStats() - 无 @RequiresPermission
 * <p>权限码: dashboard:view (biz/approver/viewer 均有, routes.yaml web.dashboard)
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功获取工作台统计 - admin (200, code=0, 10 个统计字段)</li>
 *   <li>CT-2 成功获取工作台统计 - biz (200, code=0)</li>
 *   <li>CT-3 成功获取工作台统计 - viewer (200, code=0)</li>
 *   <li>CT-4 traceId 透传 (响应头 + 响应体)</li>
 *   <li>CT-5 响应字段完整性 (totalCustomers/totalProducts/totalRequests/...)</li>
 *   <li>CT-6 认证失败 (Mock 模式限制, @Disabled)</li>
 * </ol>
 */
@DisplayName("CT-getDashboardStats: GET /api/v1/workbench/stats 契约测试")
class CTGetDashboardStatsTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/workbench/stats";

    // ===== CT-1: 成功获取工作台统计 - admin =====

    @Test
    @DisplayName("CT-1: 成功获取工作台统计 - admin (200, code=0)")
    void testGetDashboardStatsAdmin() throws Exception {
        MvcResult result = performGet(API_PATH, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");
        assertNotNull(data.get("totalCustomers"), "应返回 totalCustomers");
        assertNotNull(data.get("totalProducts"), "应返回 totalProducts");
        assertNotNull(data.get("totalRequests"), "应返回 totalRequests");
    }

    // ===== CT-2: 成功获取工作台统计 - biz =====

    @Test
    @DisplayName("CT-2: 成功获取工作台统计 - biz (200, code=0)")
    void testGetDashboardStatsBiz() throws Exception {
        MvcResult result = performGet(API_PATH, mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");
        assertTrue(data.get("totalCustomers").asLong() >= 0, "totalCustomers 应 >= 0");
    }

    // ===== CT-3: 成功获取工作台统计 - viewer =====

    @Test
    @DisplayName("CT-3: 成功获取工作台统计 - viewer (200, code=0)")
    void testGetDashboardStatsViewer() throws Exception {
        MvcResult result = performGet(API_PATH, mockUser("viewer"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");
    }

    // ===== CT-4: traceId 透传 =====

    @Test
    @DisplayName("CT-4: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)")
    void testGetDashboardStatsTraceIdPropagation() throws Exception {
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
    @DisplayName("CT-5: 响应字段完整性 (totalCustomers/totalProducts/totalRequests/draftRequests/...)")
    void testGetDashboardStatsResponseFields() throws Exception {
        MvcResult result = performGet(API_PATH, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");
        // WorkbenchStatsVO 10 个字段
        assertNotNull(data.get("totalCustomers"), "应返回 totalCustomers");
        assertNotNull(data.get("totalProducts"), "应返回 totalProducts");
        assertNotNull(data.get("totalRequests"), "应返回 totalRequests");
        assertNotNull(data.get("draftRequests"), "应返回 draftRequests");
        assertNotNull(data.get("submittedRequests"), "应返回 submittedRequests");
        assertNotNull(data.get("approvedRequests"), "应返回 approvedRequests");
        assertNotNull(data.get("rejectedRequests"), "应返回 rejectedRequests");
        assertNotNull(data.get("archivedRequests"), "应返回 archivedRequests");
        assertNotNull(data.get("pendingTodos"), "应返回 pendingTodos");
        assertNotNull(data.get("unreadMessages"), "应返回 unreadMessages");
    }

    // ===== CT-6: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-6: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testGetDashboardStatsUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }
}
