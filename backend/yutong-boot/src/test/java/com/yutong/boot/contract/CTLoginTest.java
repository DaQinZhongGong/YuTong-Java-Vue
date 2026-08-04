package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CT-login 契约测试 (operationId: login)。
 *
 * <p>契约来源: routes.yaml operationIds: [login], POST /api/v1/auth/login (@PublicEndpoint)
 * <p>控制器: AuthController.login(LoginRequest{username, password})
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功登录 - 非 Demo 账号兼容旧模式 (200, code=0, token/userId/username/roles)</li>
 *   <li>CT-2 成功登录 - Demo 账号 admin_demo (200, code=0, mockUserType=admin)</li>
 *   <li>CT-3 成功登录 - Demo 账号 viewer_demo (200, code=0, mockUserType=viewer)</li>
 *   <li>CT-4 Demo 账号密码错误 (403 AUTH-403001, PermissionDeniedException)</li>
 *   <li>CT-5 traceId 透传 (响应头 + 响应体)</li>
 *   <li>CT-6 响应字段完整性 (token/userId/username/tenantId/roles/permissions/dataScopeType)</li>
 *   <li>CT-7 标准错误信封 (code/message/traceId 字段齐全)</li>
 *   <li>CT-8 认证失败 (Mock 模式限制, @Disabled 待真实 AuthAdapter)</li>
 * </ol>
 *
 * <p>注意: login 端点为 @PublicEndpoint, 无需 X-Mock-User 头。
 * LoginRequest 上无 @Valid 注解, @NotBlank 不生效, 故不测试空字段校验场景。
 * Demo 账号密码由环境变量 YUTONG_DEMO_PASSWORD 控制 (默认 demo123)。
 */
@DisplayName("CT-login: POST /api/v1/auth/login 契约测试")
class CTLoginTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/auth/login";
    private static final String ERROR_CODE_FORBIDDEN = "AUTH-403001";

    /** 读取 Demo 账号密码 (与 MockAuthAdapter 逻辑一致: 环境变量 > 默认 demo123)。 */
    private String demoPassword() {
        String pwd = System.getenv("YUTONG_DEMO_PASSWORD");
        return (pwd == null || pwd.isBlank()) ? "demo123" : pwd;
    }

    // ===== CT-1: 成功登录 - 非 Demo 账号兼容旧模式 =====

    @Test
    @DisplayName("CT-1: 成功登录 - 非 Demo 账号兼容旧模式 (200, code=0, token/userId/username)")
    void testLoginSuccessNonDemo() throws Exception {
        Map<String, String> body = Map.of("username", "admin", "password", "any");

        MvcResult result = performPost(API_PATH, body, null);

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");
        assertNotNull(data.get("token"), "token 不应为 null");
        assertNotNull(data.get("userId"), "userId 不应为 null");
        assertNotNull(data.get("username"), "username 不应为 null");
        assertNotNull(data.get("tenantId"), "tenantId 不应为 null");
        assertNotNull(data.get("roles"), "roles 不应为 null");
        assertNotNull(data.get("permissions"), "permissions 不应为 null");
    }

    // ===== CT-2: 成功登录 - Demo 账号 admin_demo =====

    @Test
    @DisplayName("CT-2: 成功登录 - Demo 账号 admin_demo (200, code=0, mockUserType=admin)")
    void testLoginSuccessAdminDemo() throws Exception {
        Map<String, String> body = Map.of("username", "admin_demo", "password", demoPassword());

        MvcResult result = performPost(API_PATH, body, null);

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");
        assertNotNull(data.get("token"), "token 不应为 null");
        assertEquals("admin", data.get("mockUserType").asText(), "admin_demo 应映射 mockUserType=admin");
        assertTrue(data.get("roles").isArray(), "roles 应为数组");
    }

    // ===== CT-3: 成功登录 - Demo 账号 viewer_demo =====

    @Test
    @DisplayName("CT-3: 成功登录 - Demo 账号 viewer_demo (200, code=0, mockUserType=viewer)")
    void testLoginSuccessViewerDemo() throws Exception {
        Map<String, String> body = Map.of("username", "viewer_demo", "password", demoPassword());

        MvcResult result = performPost(API_PATH, body, null);

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");
        assertEquals("viewer", data.get("mockUserType").asText(), "viewer_demo 应映射 mockUserType=viewer");
    }

    // ===== CT-4: Demo 账号密码错误 =====

    @Test
    @DisplayName("CT-4: Demo 账号密码错误 (403 AUTH-403001, PermissionDeniedException)")
    void testLoginWrongPassword() throws Exception {
        Map<String, String> body = Map.of("username", "admin_demo", "password", "wrong-password-ct");

        MvcResult result = performPost(API_PATH, body, null);

        assertEquals(403, result.getResponse().getStatus(), "Demo 账号密码错误 HTTP 应为 403");
        assertError(result, ERROR_CODE_FORBIDDEN);
        assertTraceIdPresent(result);
    }

    // ===== CT-5: traceId 透传 =====

    @Test
    @DisplayName("CT-5: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)")
    void testLoginTraceIdPropagation() throws Exception {
        Map<String, String> body = Map.of("username", "admin", "password", "any");

        MvcResult result = performPost(API_PATH, body, null);

        assertEquals(200, result.getResponse().getStatus());
        ResultNode node = parseResult(result);
        assertNotNull(node.traceId(), "响应体应包含 traceId");
        assertTrue(node.traceId().length() >= 16 && node.traceId().length() <= 64,
                "traceId 长度应在 16-64 之间");

        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertNotNull(headerTraceId, "响应头应包含 X-Trace-Id");
        assertEquals(node.traceId(), headerTraceId, "响应头 X-Trace-Id 应与响应体 traceId 一致");
    }

    // ===== CT-6: 响应字段完整性 =====

    @Test
    @DisplayName("CT-6: 响应字段完整性 (token/userId/username/tenantId/roles/permissions/dataScopeType)")
    void testLoginResponseFields() throws Exception {
        Map<String, String> body = Map.of("username", "admin", "password", "any");

        MvcResult result = performPost(API_PATH, body, null);

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");
        assertNotNull(data.get("token").asText(), "应返回 token");
        assertNotNull(data.get("userId").asText(), "应返回 userId");
        assertNotNull(data.get("username").asText(), "应返回 username");
        assertNotNull(data.get("tenantId").asText(), "应返回 tenantId");
        assertTrue(data.get("roles").isArray(), "roles 应为数组");
        assertTrue(data.get("permissions").isArray(), "permissions 应为数组");
        assertNotNull(data.get("dataScopeType").asText(), "应返回 dataScopeType");
        assertNotNull(data.get("mock").asText(), "应返回 mock 标识");
    }

    // ===== CT-7: 标准错误信封结构 =====

    @Test
    @DisplayName("CT-7: 标准错误信封 (code/message/traceId 字段齐全, 非成功响应)")
    void testLoginErrorEnvelopeStructure() throws Exception {
        Map<String, String> body = Map.of("username", "admin_demo", "password", "wrong-ct-envelope");

        MvcResult result = performPost(API_PATH, body, null);

        ResultNode node = parseResult(result);
        assertNotNull(node.code(), "错误信封必须包含 code 字段");
        assertNotEquals("0", node.code(), "错误响应 code 不应为 0");
        assertNotNull(node.message(), "错误信封必须包含 message 字段");
        assertNotNull(node.traceId(), "错误信封必须包含 traceId 字段");
        assertTrue(node.traceId().length() >= 16 && node.traceId().length() <= 64,
                "traceId 长度应在 16-64 之间");
    }

    // ===== CT-8: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-8: 认证失败 (login 为 @PublicEndpoint 无需鉴权, @Disabled)")
    @org.junit.jupiter.api.Disabled("login 为 @PublicEndpoint 公开端点, 无认证拦截; " +
            "未认证场景需真实 AuthAdapter 接入后验证 token 失效逻辑")
    void testLoginUnauthenticated() {
        // Mock 模式限制: login 是公开端点, 无需认证; token 失效/过期场景需真实 AuthAdapter
    }
}
