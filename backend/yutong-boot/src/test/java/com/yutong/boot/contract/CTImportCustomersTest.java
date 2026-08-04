package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CT-importCustomers 契约测试。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-importCustomers 验收标准)
 * <p>契约来源: openapi.yaml POST /api/v1/customers/import
 *              (x-permission: biz:customer:import, multipart/form-data)
 *
 * <p>当前实现: 控制器返回异步任务响应 {taskId, status, message: "导入任务已提交"}。
 *   测试聚焦端点存在性、权限校验、任务响应字段与 traceId 透传。
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功调用 (biz, 200, code=0, 桩响应 successCount/failCount/message)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (viewer 无 biz:customer:import, 403 AUTH-403001)</li>
 *   <li>CT-4 traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)</li>
 * </ol>
 *
 * <p>权限矩阵: admin (*) 有权限; biz 用户有 biz:customer:import; viewer/approver 无。
 */
@DisplayName("CT-importCustomers: POST /api/v1/customers/import 契约测试")
class CTImportCustomersTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/customers/import";
    private static final String ERROR_CODE_FORBIDDEN = "AUTH-403001";

    /** 构造最小 CSV 内容用于上传。 */
    private byte[] csvContent() {
        return ("customerCode,customerName,contactName,contactPhone,address,status\n" +
                "CT-IMP-001,CT导入客户,联系人,13800138000,测试地址,ENABLED\n").getBytes(StandardCharsets.UTF_8);
    }

    // ===== CT-1: 成功调用 =====

    @Test
    @DisplayName("CT-1: 成功调用导入端点 (biz, 200, code=0, 任务响应 taskId/status/message)")
    void testImportCustomersSuccess() throws Exception {
        MvcResult result = performFileUpload(API_PATH, csvContent(), "customers.csv", "text/csv", mockUser("biz"));

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
    void testImportCustomersUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 (viewer) =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (viewer 无 biz:customer:import, 403 AUTH-403001)")
    void testImportCustomersForbiddenViewer() throws Exception {
        MvcResult result = performFileUpload(API_PATH, csvContent(), "customers.csv", "text/csv", mockUser("viewer"));

        assertEquals(403, result.getResponse().getStatus(), "viewer 无导入权限, HTTP 应为 403");
        assertError(result, ERROR_CODE_FORBIDDEN);
        assertTraceIdPresent(result);
    }

    // ===== CT-4: traceId 透传 =====

    @Test
    @DisplayName("CT-4: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)")
    void testImportCustomersTraceIdPropagation() throws Exception {
        MvcResult result = performFileUpload(API_PATH, csvContent(), "customers.csv", "text/csv", mockUser("biz"));

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
