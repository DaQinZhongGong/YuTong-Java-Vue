package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CT-logout 契约测试 (operationId: logout)。
 *
 * <p>契约来源: routes.yaml operationIds: [getCurrentUser, logout], POST /api/v1/auth/logout
 * <p>控制器: AuthController.logout() - 无 @RequiresPermission, 所有认证用户可访问
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功注销 - admin (200, code=0, data=null)</li>
 *   <li>CT-2 成功注销 - biz (200, code=0)</li>
 *   <li>CT-3 traceId 透传 (响应头 + 响应体)</li>
 *   <li>CT-4 标准成功响应信封 (code/message/data/traceId)</li>
 *   <li>CT-5 认证失败 (Mock 模式限制, @Disabled)</li>
 * </ol>
 */
@DisplayName("CT-logout: POST /api/v1/auth/logout 契约测试")
class CTLogoutTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/auth/logout";

    // ===== CT-1: 成功注销 - admin =====

    @Test
    @DisplayName("CT-1: 成功注销 - admin (200, code=0, data=null)")
    void testLogoutAdmin() throws Exception {
        MvcResult result = performPost(API_PATH, null, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertNull(data, "logout 成功响应 data 应为 null");
    }

    // ===== CT-2: 成功注销 - biz =====

    @Test
    @DisplayName("CT-2: 成功注销 - biz (200, code=0)")
    void testLogoutBiz() throws Exception {
        MvcResult result = performPost(API_PATH, null, mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        assertTraceIdPresent(result);
    }

    // ===== CT-3: traceId 透传 =====

    @Test
    @DisplayName("CT-3: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)")
    void testLogoutTraceIdPropagation() throws Exception {
        MvcResult result = performPost(API_PATH, null, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        ResultNode node = parseResult(result);
        assertNotNull(node.traceId(), "响应体应包含 traceId");
        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertNotNull(headerTraceId, "响应头应包含 X-Trace-Id");
        assertEquals(node.traceId(), headerTraceId, "响应头 X-Trace-Id 应与响应体 traceId 一致");
    }

    // ===== CT-4: 标准成功响应信封 =====

    @Test
    @DisplayName("CT-4: 标准成功响应信封 (code=0/message/data=null/traceId)")
    void testLogoutSuccessEnvelope() throws Exception {
        MvcResult result = performPost(API_PATH, null, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        ResultNode node = parseResult(result);
        assertEquals("0", node.code(), "成功响应 code 应为 0");
        assertNotNull(node.message(), "成功响应应包含 message 字段");
        assertNull(node.data(), "logout 成功响应 data 应为 null");
        assertNotNull(node.traceId(), "响应应包含 traceId");
    }

    // ===== CT-5: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-5: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testLogoutUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }
}
