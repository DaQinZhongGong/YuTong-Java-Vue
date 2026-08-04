package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.yutong.sample.request.dto.SaveBizRequestRequest;
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
 * GA2-L189 CT-createBizRequest 契约测试。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-createBizRequest 验收标准)
 * <p>策略来源: operation-policies.yaml createBizRequest → businessDraftCreate profile
 * <p>契约来源: openapi.yaml POST /api/v1/biz-requests (x-permission: biz:request:add, x-error-codes: [BIZ-400001, BIZ-400002, BIZ-409003])
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功创建 (200, code=0, 返回 BizRequest + requestNo/version/status=DRAFT + traceId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (viewer 无 biz:request:add, 403 AUTH-403001)</li>
 *   <li>CT-4 参数校验失败 - title 为空 (400)</li>
 *   <li>CT-5 标准错误信封 (code/message/traceId 字段齐全)</li>
 *   <li>CT-6 traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)</li>
 *   <li>CT-7 审计落库 (sys_operation_log 有 CREATE 记录)</li>
 * </ol>
 *
 * <p>数据隔离: 每个用例使用唯一 title (UUID 后缀), @AfterEach 清理创建的数据。
 */
@DisplayName("CT-createBizRequest: POST /api/v1/biz-requests 契约测试")
class CTCreateBizRequestTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/biz-requests";
    private static final String ERROR_CODE_FORBIDDEN = "AUTH-403001";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final java.util.Set<String> createdRequestIds = ConcurrentHashMap.newKeySet();

    @AfterEach
    void cleanupTestData() {
        for (String id : createdRequestIds) {
            jdbcTemplate.update("DELETE FROM sys_operation_log WHERE biz_type = 'biz_request' AND biz_id = ?", id);
            jdbcTemplate.update("DELETE FROM biz_approval_record WHERE request_id = ?", id);
            jdbcTemplate.update("DELETE FROM biz_request_item WHERE request_id = ?", id);
            jdbcTemplate.update("DELETE FROM biz_request WHERE id = ?", id);
        }
        createdRequestIds.clear();
    }

    /** 构造合法 SaveBizRequestRequest (唯一 title)。 */
    private SaveBizRequestRequest buildValidRequest() {
        SaveBizRequestRequest req = new SaveBizRequestRequest();
        req.setTitle("CT-L189-创建测试-" + UUID.randomUUID().toString().substring(0, 8));
        req.setCustomerId("01CTCUSTOMER0000000000000001");
        req.setCustomerNameSnapshot("CT测试客户");
        req.setApplyReason("CT测试申请原因");
        return req;
    }

    /** 从成功响应中提取申请单 ID 并记录到清理集合。 */
    private String extractAndTrackRequestId(MvcResult result) throws Exception {
        ResultNode node = parseResult(result);
        JsonNode data = node.data();
        assertNotNull(data, "成功响应 data 不应为 null");
        String id = data.get("id").asText();
        createdRequestIds.add(id);
        return id;
    }

    // ===== CT-1: 成功创建 =====

    @Test
    @DisplayName("CT-1: 成功创建申请单草稿 (biz 用户, 200, code=0, 返回 id/requestNo/version/status=DRAFT)")
    void testCreateBizRequestSuccess() throws Exception {
        SaveBizRequestRequest request = buildValidRequest();

        MvcResult result = performPost(API_PATH, request, mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        ResultNode node = parseResult(result);
        JsonNode data = node.data();
        assertNotNull(data.get("id"), "返回的 id 不应为 null");
        assertNotNull(data.get("requestNo"), "返回的 requestNo 不应为 null");
        assertEquals(request.getTitle(), data.get("title").asText());
        assertEquals("DRAFT", data.get("requestStatus").asText(), "新建草稿状态应为 DRAFT");
        assertNotNull(data.get("version"), "version 不应为 null");
        assertNotNull(data.get("totalAmount"), "totalAmount 不应为 null");

        extractAndTrackRequestId(result);
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testCreateBizRequestUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (viewer 无 biz:request:add, 403 AUTH-403001)")
    void testCreateBizRequestForbidden() throws Exception {
        SaveBizRequestRequest request = buildValidRequest();

        MvcResult result = performPost(API_PATH, request, mockUser("viewer"));

        assertEquals(403, result.getResponse().getStatus(), "viewer 无写权限, HTTP 应为 403");
        assertError(result, ERROR_CODE_FORBIDDEN);
        assertTraceIdPresent(result);
    }

    // ===== CT-4: 参数校验失败 =====

    @Test
    @DisplayName("CT-4: 参数校验失败 - title 为空 (400)")
    void testCreateBizRequestValidationBlankTitle() throws Exception {
        SaveBizRequestRequest request = buildValidRequest();
        request.setTitle(""); // @NotBlank 校验

        MvcResult result = performPost(API_PATH, request, mockUser("biz"));

        assertEquals(400, result.getResponse().getStatus(), "title 为空, HTTP 应为 400");
        ResultNode node = parseResult(result);
        assertNotEquals("0", node.code(), "校验失败不应返回 code=0");
        assertTraceIdPresent(result);
    }

    // ===== CT-5: 标准错误信封结构 =====

    @Test
    @DisplayName("CT-5: 标准错误信封 (code/message/traceId 字段齐全, 非成功响应)")
    void testCreateBizRequestErrorEnvelopeStructure() throws Exception {
        SaveBizRequestRequest request = buildValidRequest();
        request.setTitle(""); // 触发 400

        MvcResult result = performPost(API_PATH, request, mockUser("biz"));

        ResultNode node = parseResult(result);
        assertNotNull(node.code(), "错误信封必须包含 code 字段");
        assertNotEquals("0", node.code(), "错误响应 code 不应为 0");
        assertNotNull(node.message(), "错误信封必须包含 message 字段");
        assertNotNull(node.traceId(), "错误信封必须包含 traceId 字段");
        assertTrue(node.traceId().length() >= 16 && node.traceId().length() <= 64,
                "traceId 长度应在 16-64 之间");
    }

    // ===== CT-6: traceId 透传 =====

    @Test
    @DisplayName("CT-6: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)")
    void testCreateBizRequestTraceIdPropagation() throws Exception {
        SaveBizRequestRequest request = buildValidRequest();

        MvcResult result = performPost(API_PATH, request, mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus());

        ResultNode node = parseResult(result);
        assertNotNull(node.traceId(), "响应体应包含 traceId");
        assertTrue(node.traceId().length() >= 16 && node.traceId().length() <= 64,
                "traceId 长度应在 16-64 之间");

        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertNotNull(headerTraceId, "响应头应包含 X-Trace-Id");
        assertEquals(node.traceId(), headerTraceId,
                "响应头 X-Trace-Id 应与响应体 traceId 一致");

        extractAndTrackRequestId(result);
    }

    // ===== CT-7: 审计落库 =====

    @Test
    @DisplayName("CT-7: 审计落库 (@Auditable CREATE → sys_operation_log 有记录)")
    void testCreateBizRequestAuditLog() throws Exception {
        SaveBizRequestRequest request = buildValidRequest();

        MvcResult result = performPost(API_PATH, request, mockUser("biz"));
        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        String requestId = extractAndTrackRequestId(result);

        String traceId = parseResult(result).traceId();
        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_operation_log " +
                        "WHERE biz_type = 'biz_request' AND operation_type = 'CREATE' " +
                        "AND trace_id = ?",
                Integer.class, traceId);

        assertNotNull(auditCount, "审计日志查询不应返回 null");
        assertTrue(auditCount >= 1,
                "sys_operation_log 应至少有 1 条 CREATE 审计记录 (traceId=" + traceId + "), 实际: " + auditCount);

        jdbcTemplate.query("SELECT biz_id, module, operation_type, biz_type FROM sys_operation_log " +
                        "WHERE biz_type = 'biz_request' AND operation_type = 'CREATE' AND trace_id = ?",
                rs -> {
                    assertEquals(requestId, rs.getString("biz_id"), "审计记录 biz_id 应为创建的申请单 ID");
                    assertEquals("sample", rs.getString("module"), "审计记录 module 应为 sample");
                    assertEquals("CREATE", rs.getString("operation_type"), "审计记录 operation_type 应为 CREATE");
                    assertEquals("biz_request", rs.getString("biz_type"), "审计记录 biz_type 应为 biz_request");
                }, traceId);
    }
}
