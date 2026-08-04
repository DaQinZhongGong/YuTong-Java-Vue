package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CT-getOperationLog 契约测试 (operationId: getOperationLog)。
 *
 * <p>契约来源: routes.yaml operationIds: [getOperationLog],
 * GET /api/v1/operation-logs/{id}
 * <p>控制器: OperationLogController.get(id) - 无 @RequiresPermission (所有认证用户可访问)
 * <p>服务: OperationLogService.getLog - 不存在时抛 ResourceNotFoundException (SYS-404001);
 * 返回前对 beforeJson/afterJson 做深度递归脱敏
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功查询 (200, code=0, 返回 SysOperationLog + traceId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 操作日志不存在 (404 SYS-404001)</li>
 *   <li>CT-4 字段完整性 (id/operationType/module/bizType/bizId/content/result/traceId/operatorId 全返回)</li>
 *   <li>CT-5 traceId 透传 (响应头 + 响应体)</li>
 *   <li>CT-6 敏感字段脱敏 (beforeJson/afterJson 中 password/secret/token 被脱敏)</li>
 * </ol>
 *
 * <p>数据隔离: 通过 JdbcTemplate 直接插入 sys_operation_log 测试数据, @AfterEach 物理清理。
 */
@DisplayName("CT-getOperationLog: GET /api/v1/operation-logs/{id} 契约测试")
class CTGetOperationLogTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/operation-logs";
    private static final String ERROR_CODE_NOT_FOUND = "SYS-404001";
    private static final String TENANT_ID = "default";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final java.util.Set<String> createdIds = ConcurrentHashMap.newKeySet();

    @AfterEach
    void cleanupTestData() {
        for (String id : createdIds) {
            jdbcTemplate.update("DELETE FROM sys_operation_log WHERE id = ?", id);
        }
        createdIds.clear();
    }

    /** 通过 JdbcTemplate 插入操作日志测试数据, 返回生成的 id。 */
    private String createLogAndTrack(String beforeJson, String afterJson) {
        String id = "CTOPLOG" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String traceId = "ct-trace-" + UUID.randomUUID().toString().substring(0, 16);
        jdbcTemplate.update(
                "INSERT INTO sys_operation_log (id, tenant_id, operation_type, module, biz_type, biz_id, " +
                        "content, before_json, after_json, result, error_code, trace_id, operator_id, " +
                        "operator_name, ip, user_agent, operated_time, created_by, created_time, " +
                        "updated_by, updated_time, deleted, version) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, NULL, ?, ?, ?, ?, ?, now(), ?, now(), ?, now(), false, 0)",
                id, TENANT_ID, "CREATE", "sample", "biz_request", "ct-biz-detail",
                "CT详情查询测试", beforeJson, afterJson, "SUCCESS", traceId,
                "ct-op-detail", "CT测试用户", "127.0.0.1", "CT-Test-Agent",
                "ct-op-detail", "ct-op-detail");
        createdIds.add(id);
        return id;
    }

    // ===== CT-1: 成功查询 =====

    @Test
    @DisplayName("CT-1: 成功查询操作日志详情 (admin 用户, 200, code=0, 返回 SysOperationLog + traceId)")
    void testGetOperationLogSuccess() throws Exception {
        String id = createLogAndTrack("{}", "{}");

        MvcResult result = performGet(API_PATH + "/" + id, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertEquals(id, data.get("id").asText(), "返回 id 应与请求 id 一致");
        assertEquals("CREATE", data.get("operationType").asText());
        assertEquals("sample", data.get("module").asText());
        assertEquals("SUCCESS", data.get("result").asText());
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testGetOperationLogUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 操作日志不存在 =====

    @Test
    @DisplayName("CT-3: 操作日志不存在 (404 SYS-404001)")
    void testGetOperationLogNotFound() throws Exception {
        String nonExistentId = "NOSUCHLOG" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        MvcResult result = performGet(API_PATH + "/" + nonExistentId, mockUser("admin"));

        assertEquals(404, result.getResponse().getStatus(), "操作日志不存在, HTTP 应为 404");
        assertError(result, ERROR_CODE_NOT_FOUND);
        assertTraceIdPresent(result);
    }

    // ===== CT-4: 字段完整性 =====

    @Test
    @DisplayName("CT-4: 字段完整性 (id/operationType/module/bizType/content/result/traceId/operatorId 全返回)")
    void testGetOperationLogFieldCompleteness() throws Exception {
        String id = createLogAndTrack("{}", "{}");

        MvcResult result = performGet(API_PATH + "/" + id, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertNotNull(data.get("id"), "应包含 id 字段");
        assertNotNull(data.get("operationType"), "应包含 operationType 字段");
        assertNotNull(data.get("module"), "应包含 module 字段");
        assertNotNull(data.get("bizType"), "应包含 bizType 字段");
        assertNotNull(data.get("content"), "应包含 content 字段");
        assertNotNull(data.get("result"), "应包含 result 字段");
        assertNotNull(data.get("traceId"), "应包含 traceId 字段");
        assertNotNull(data.get("operatorId"), "应包含 operatorId 字段");
        assertNotNull(data.get("operatorName"), "应包含 operatorName 字段");
        assertNotNull(data.get("operatedTime"), "应包含 operatedTime 字段");
    }

    // ===== CT-5: traceId 透传 =====

    @Test
    @DisplayName("CT-5: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)")
    void testGetOperationLogTraceIdPropagation() throws Exception {
        String id = createLogAndTrack("{}", "{}");

        MvcResult result = performGet(API_PATH + "/" + id, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        ResultNode node = parseResult(result);
        assertNotNull(node.traceId(), "响应体应包含 traceId");
        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertNotNull(headerTraceId, "响应头应包含 X-Trace-Id");
        assertEquals(node.traceId(), headerTraceId, "响应头 X-Trace-Id 应与响应体 traceId 一致");
    }

    // ===== CT-6: 敏感字段脱敏 =====

    @Test
    @DisplayName("CT-6: 敏感字段脱敏 (beforeJson/afterJson 中 password 被脱敏)")
    void testGetOperationLogSensitiveMasking() throws Exception {
        // beforeJson 含 password 字段, afterJson 含 token 字段
        String beforeJson = "{\"username\":\"admin\",\"password\":\"secret123\"}";
        String afterJson = "{\"username\":\"admin\",\"password\":\"newpass456\",\"token\":\"abc-token\"}";
        String id = createLogAndTrack(beforeJson, afterJson);

        MvcResult result = performGet(API_PATH + "/" + id, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        JsonNode data = parseResult(result).data();

        // beforeJson 中 password 应被脱敏
        String beforeJsonResp = data.get("beforeJson").asText();
        assertTrue(beforeJsonResp.contains("******"), "beforeJson 中 password 应被脱敏为 ******");

        // afterJson 中 password/token 应被脱敏
        String afterJsonResp = data.get("afterJson").asText();
        assertTrue(afterJsonResp.contains("******"), "afterJson 中敏感字段应被脱敏为 ******");
        assertFalse(afterJsonResp.contains("newpass456"), "afterJson 中 password 明文不应出现");
        assertFalse(afterJsonResp.contains("abc-token"), "afterJson 中 token 明文不应出现");
    }
}
