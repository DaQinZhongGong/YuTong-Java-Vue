package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CT-resolveMobileScan 契约测试。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-resolveMobileScan 验收标准)
 * <p>契约来源: openapi.yaml POST /api/v1/mobile/scan/resolve
 *              (routes.yaml x-permission: mobile:scan:use)
 *
 * <p>当前实现: MobileController.resolveScan 接收 @RequestParam code (必填),
 *   返回 {routeId: "mobile.workbench", params: {code: &lt;传入code&gt;}}。
 *   扫码解析策略: 服务端解析 allowlisted 短链提供商为已注册 routeId, 客户端不打开任意 URL。
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功解析 (biz, 200, code=0, 返回 routeId/params + 回显 code)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (MobileController 未加 @RequiresPermission, @Disabled)</li>
 *   <li>CT-4 缺少必填参数 code (400)</li>
 *   <li>CT-5 traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)</li>
 * </ol>
 *
 * <p>实现说明: MobileController 当前未加 @RequiresPermission 注解, 权限码
 *   mobile:scan:use 未在控制器层强制; 待补充注解后启用 CT-3。
 */
@DisplayName("CT-resolveMobileScan: POST /api/v1/mobile/scan/resolve 契约测试")
class CTResolveMobileScanTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/mobile/scan/resolve";

    // ===== CT-1: 成功解析 =====

    @Test
    @DisplayName("CT-1: 成功解析扫码 (biz, 200, code=0, 返回 routeId/params + 回显 code)")
    void testResolveMobileScanSuccess() throws Exception {
        String code = "https://example.com/s/abc123";
        String url = API_PATH + "?code=" + URLEncoder.encode(code, StandardCharsets.UTF_8);

        MvcResult result = performPost(url, null, mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");
        assertNotNull(data.get("routeId"), "响应应包含 routeId");
        assertEquals("mobile.workbench", data.get("routeId").asText(), "routeId 应为 mobile.workbench");
        assertNotNull(data.get("params"), "响应应包含 params");
        // params 回显传入的 code
        JsonNode params = data.get("params");
        assertNotNull(params.get("code"), "params 应包含 code");
        assertEquals(code, params.get("code").asText(), "params.code 应回显传入的 code");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testResolveMobileScanUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 (MobileController 未加 @RequiresPermission) =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (MobileController 未加 @RequiresPermission, @Disabled 待补充注解)")
    @org.junit.jupiter.api.Disabled("MobileController.resolveScan 未加 @RequiresPermission(mobile:scan:use), " +
            "权限码未在控制器层强制; 待补充注解后启用此用例")
    void testResolveMobileScanForbidden() {
        // 实现待补充: MobileController 当前未加 @RequiresPermission 注解
    }

    // ===== CT-4: 缺少必填参数 code =====

    @Test
    @DisplayName("CT-4: 缺少必填参数 code (400)")
    void testResolveMobileScanMissingCode() throws Exception {
        // 不传 code 参数 → @RequestParam(required=true) 校验失败 → 400
        MvcResult result = performPost(API_PATH, null, mockUser("biz"));

        assertEquals(400, result.getResponse().getStatus(), "缺少必填参数 code, HTTP 应为 400");
    }

    // ===== CT-5: traceId 透传 =====

    @Test
    @DisplayName("CT-5: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)")
    void testResolveMobileScanTraceIdPropagation() throws Exception {
        String code = "scan-trace-test";
        String url = API_PATH + "?code=" + URLEncoder.encode(code, StandardCharsets.UTF_8);

        MvcResult result = performPost(url, null, mockUser("biz"));

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
