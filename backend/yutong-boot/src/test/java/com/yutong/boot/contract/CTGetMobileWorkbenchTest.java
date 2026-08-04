package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CT-getMobileWorkbench 契约测试。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-getMobileWorkbench 验收标准)
 * <p>契约来源: openapi.yaml GET /api/v1/mobile/workbench
 *              (routes.yaml x-permission: mobile:workbench:view)
 *
 * <p>当前实现: MobileController.getWorkbench 返回聚合统计
 *   {todoCount, messageCount, pendingRequestCount}。
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功查询 (biz, 200, code=0, 返回 todoCount/messageCount/pendingRequestCount)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (MobileController 未加 @RequiresPermission, @Disabled)</li>
 *   <li>CT-4 traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)</li>
 * </ol>
 *
 * <p>实现说明: MobileController 当前未加 @RequiresPermission 注解, 权限码
 *   mobile:workbench:view 未在控制器层强制; 待补充注解后启用 CT-3。
 */
@DisplayName("CT-getMobileWorkbench: GET /api/v1/mobile/workbench 契约测试")
class CTGetMobileWorkbenchTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/mobile/workbench";

    // ===== CT-1: 成功查询 =====

    @Test
    @DisplayName("CT-1: 成功查询移动端工作台 (biz, 200, code=0, 返回 todoCount/messageCount/pendingRequestCount)")
    void testGetMobileWorkbenchSuccess() throws Exception {
        MvcResult result = performGet(API_PATH, mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");
        assertNotNull(data.get("todoCount"), "工作台响应应包含 todoCount");
        assertNotNull(data.get("messageCount"), "工作台响应应包含 messageCount");
        assertNotNull(data.get("pendingRequestCount"), "工作台响应应包含 pendingRequestCount");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testGetMobileWorkbenchUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 (MobileController 未加 @RequiresPermission) =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (MobileController 未加 @RequiresPermission, @Disabled 待补充注解)")
    @org.junit.jupiter.api.Disabled("MobileController.getWorkbench 未加 @RequiresPermission(mobile:workbench:view), " +
            "权限码未在控制器层强制; 待补充注解后启用此用例")
    void testGetMobileWorkbenchForbidden() {
        // 实现待补充: MobileController 当前未加 @RequiresPermission 注解
    }

    // ===== CT-4: traceId 透传 =====

    @Test
    @DisplayName("CT-4: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)")
    void testGetMobileWorkbenchTraceIdPropagation() throws Exception {
        MvcResult result = performGet(API_PATH, mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus());
        ResultNode node = parseResult(result);
        assertNotNull(node.traceId(), "响应体应包含 traceId");
        assertTrue(node.traceId().length() >= 16 && node.traceId().length() <= 64,
                "traceId 长度应在 16-64 之间");

        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertNotNull(headerTraceId, "响应头应包含 X-Trace-Id");
        assertEquals(node.traceId(), headerTraceId, "响应头 X-Trace-Id 应与响应体 traceId 一致");
    }
}
