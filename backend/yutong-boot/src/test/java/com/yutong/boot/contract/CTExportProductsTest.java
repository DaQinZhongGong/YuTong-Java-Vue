package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CT-exportProducts 契约测试。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-exportProducts 验收标准)
 * <p>契约来源: openapi.yaml GET /api/v1/products/export
 *              (x-permission: biz:product:export)
 *
 * <p>当前实现: 控制器返回异步任务响应 {taskId, status, message: "导出任务已提交"}。
 *   测试聚焦端点存在性、权限校验、任务响应字段与 traceId 透传。
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功调用 (biz, 200, code=0, 桩响应 message/fileId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (viewer 无 biz:product:export, 403 AUTH-403001)</li>
 *   <li>CT-4 traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)</li>
 * </ol>
 *
 * <p>权限矩阵: admin (*) 有权限; biz 用户有 biz:product:export; viewer/approver 无。
 */
@DisplayName("CT-exportProducts: GET /api/v1/products/export 契约测试")
class CTExportProductsTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/products/export";
    private static final String ERROR_CODE_FORBIDDEN = "AUTH-403001";

    // ===== CT-1: 成功调用 =====

    @Test
    @DisplayName("CT-1: 成功调用导出端点 (biz, 200, code=0, 任务响应 taskId/status/message)")
    void testExportProductsSuccess() throws Exception {
        MvcResult result = performGet(API_PATH, mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");
        assertNotNull(data.get("taskId"), "任务响应应包含 taskId");
        assertNotNull(data.get("status"), "任务响应应包含 status");
        assertNotNull(data.get("message"), "任务响应应包含 message");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testExportProductsUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 (viewer) =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (viewer 无 biz:product:export, 403 AUTH-403001)")
    void testExportProductsForbiddenViewer() throws Exception {
        MvcResult result = performGet(API_PATH, mockUser("viewer"));

        assertEquals(403, result.getResponse().getStatus(), "viewer 无导出权限, HTTP 应为 403");
        assertError(result, ERROR_CODE_FORBIDDEN);
        assertTraceIdPresent(result);
    }

    // ===== CT-4: traceId 透传 =====

    @Test
    @DisplayName("CT-4: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)")
    void testExportProductsTraceIdPropagation() throws Exception {
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
