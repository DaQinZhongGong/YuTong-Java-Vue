package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.yutong.sample.request.dto.SaveBizRequestRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CT-withdrawBizRequest 契约测试。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-withdrawBizRequest 验收标准)
 * <p>策略来源: operation-policies.yaml withdrawBizRequest → workflowAction profile
 * <p>契约来源: openapi.yaml POST /api/v1/biz-requests/{id}/withdraw
 *              (x-permission: biz:request:withdraw, x-error-codes: [BIZ-404001, BIZ-409004, SYS-409001])
 *
 * <p>状态机: SUBMITTED → DRAFT (withdraw)
 *   注意: 状态机允许的源状态仅为 SUBMITTED (非 APPROVED)。
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功撤回 (biz 创建+提交, biz 撤回, SUBMITTED→DRAFT, 200, code=0)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (viewer 无 biz:request:withdraw, 403 AUTH-403001)</li>
 *   <li>CT-4 申请单不存在 (404 BIZ-404001)</li>
 *   <li>CT-5 状态冲突 (DRAFT→withdraw 不允许, 409 BIZ-409004)</li>
 *   <li>CT-6 版本冲突 (stale version, 409 SYS-409001)</li>
 *   <li>CT-7 审计落库 (@Auditable WITHDRAW → sys_operation_log 有记录)</li>
 *   <li>CT-8 traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)</li>
 * </ol>
 *
 * <p>数据隔离: 每个用例独立创建测试数据 (UUID 后缀 title), @AfterEach 物理清理。
 */
@DisplayName("CT-withdrawBizRequest: POST /api/v1/biz-requests/{id}/withdraw 契约测试")
class CTWithdrawBizRequestTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/biz-requests";
    private static final String ERROR_CODE_FORBIDDEN = "AUTH-403001";
    private static final String ERROR_CODE_NOT_FOUND = "BIZ-404001";
    private static final String ERROR_CODE_STATUS_CONFLICT = "BIZ-409004";
    private static final String ERROR_CODE_VERSION_CONFLICT = "SYS-409001";

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
        req.setTitle("CT-WITHDRAW-" + UUID.randomUUID().toString().substring(0, 8));
        req.setCustomerId("01CTCUSTOMER0000000000000001");
        req.setCustomerNameSnapshot("CT撤回测试客户");
        req.setApplyReason("CT撤回测试申请原因");
        return req;
    }

    /** 创建申请单草稿 (biz 用户) 并跟踪 ID。 */
    private String createRequestAndTrack() throws Exception {
        SaveBizRequestRequest request = buildValidRequest();
        MvcResult result = performPost(API_PATH, request, mockUser("biz"));
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertNotNull(data, "创建响应 data 不应为 null");
        String id = data.get("id").asText();
        createdRequestIds.add(id);
        return id;
    }

    /** 提交申请单 (biz 用户, version=null 跳过版本校验)。 */
    private void submitRequest(String id) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("version", null);
        body.put("idempotencyKey", "submit-" + UUID.randomUUID());
        MvcResult result = performPost(API_PATH + "/" + id + "/submit", body, mockUser("biz"));
        assertEquals(200, result.getResponse().getStatus(), "提交申请单应成功");
    }

    /** 创建并提交申请单 (biz 用户), 返回申请单 ID。 */
    private String createAndSubmitRequest() throws Exception {
        String id = createRequestAndTrack();
        submitRequest(id);
        return id;
    }

    /** 构造撤回请求体。 */
    private Map<String, Object> buildWithdrawBody(String reason, Integer version) {
        Map<String, Object> body = new HashMap<>();
        body.put("reason", reason);
        body.put("version", version);
        body.put("idempotencyKey", "withdraw-" + UUID.randomUUID());
        return body;
    }

    /** 获取申请单当前 version (通过 GET 详情接口)。 */
    private int getCurrentVersion(String id, HttpHeaders headers) throws Exception {
        MvcResult result = performGet(API_PATH + "/" + id, headers);
        assertSuccess(result);
        return parseResult(result).data().get("version").asInt();
    }

    /** 创建+提交+审批通过 (biz 创建提交, admin 审批), 返回申请单 ID (状态 APPROVED)。 */
    private String createSubmittedAndApprove() throws Exception {
        String id = createAndSubmitRequest();
        HttpHeaders adminHeaders = mockUser("admin");
        int version = getCurrentVersion(id, adminHeaders);
        Map<String, Object> approveBody = new HashMap<>();
        approveBody.put("opinion", "同意");
        approveBody.put("version", version);
        approveBody.put("idempotencyKey", "approve-" + UUID.randomUUID());
        MvcResult result = performPost(API_PATH + "/" + id + "/approve", approveBody, adminHeaders);
        assertEquals(200, result.getResponse().getStatus(), "审批应成功");
        return id;
    }

    // ===== CT-1: 成功撤回 =====

    @Test
    @DisplayName("CT-1: 成功撤回 (biz 创建+提交, biz 撤回, SUBMITTED→DRAFT, 200, code=0)")
    void testWithdrawBizRequestSuccess() throws Exception {
        String id = createAndSubmitRequest();

        Map<String, Object> body = buildWithdrawBody("信息需补充", null);
        MvcResult result = performPost(API_PATH + "/" + id + "/withdraw", body, mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        ResultNode node = parseResult(result);
        JsonNode data = node.data();
        assertNotNull(data, "data 不应为 null");
        assertEquals(id, data.get("id").asText(), "返回 id 应与请求 id 一致");
        assertEquals("DRAFT", data.get("requestStatus").asText(), "撤回后状态应为 DRAFT");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testWithdrawBizRequestUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (viewer 无 biz:request:withdraw, 403 AUTH-403001)")
    void testWithdrawBizRequestForbidden() throws Exception {
        String id = createAndSubmitRequest();

        Map<String, Object> body = buildWithdrawBody("信息需补充", null);
        MvcResult result = performPost(API_PATH + "/" + id + "/withdraw", body, mockUser("viewer"));

        assertEquals(403, result.getResponse().getStatus(), "viewer 无撤回权限, HTTP 应为 403");
        assertError(result, ERROR_CODE_FORBIDDEN);
        assertTraceIdPresent(result);
    }

    // ===== CT-4: 申请单不存在 =====

    @Test
    @DisplayName("CT-4: 申请单不存在 (404 BIZ-404001)")
    void testWithdrawBizRequestNotFound() throws Exception {
        String nonExistentId = "01NOTEXIST00000000000000CT";

        Map<String, Object> body = buildWithdrawBody("信息需补充", null);
        MvcResult result = performPost(API_PATH + "/" + nonExistentId + "/withdraw", body, mockUser("biz"));

        assertEquals(404, result.getResponse().getStatus(), "申请单不存在, HTTP 应为 404");
        assertError(result, ERROR_CODE_NOT_FOUND);
        assertTraceIdPresent(result);
    }

    // ===== CT-5: 状态冲突 =====

    @Test
    @DisplayName("CT-5: 状态冲突 (APPROVED→withdraw 不允许, 409 BIZ-409004)")
    void testWithdrawBizRequestStatusConflict() throws Exception {
        // 创建+提交+审批通过 → 状态为 APPROVED
        String id = createSubmittedAndApprove();

        // 从 APPROVED 撤回 → 不允许 (WITHDRAW 仅允许 SUBMITTED; APPROVED 非幂等目标 DRAFT)
        Map<String, Object> body = buildWithdrawBody("信息需补充", null);
        MvcResult result = performPost(API_PATH + "/" + id + "/withdraw", body, mockUser("admin"));

        assertEquals(409, result.getResponse().getStatus(), "状态冲突 HTTP 应为 409");
        assertError(result, ERROR_CODE_STATUS_CONFLICT);
        assertTraceIdPresent(result);
    }

    // ===== CT-6: 版本冲突 =====

    @Test
    @DisplayName("CT-6: 版本冲突 (stale version, 409 SYS-409001)")
    void testWithdrawBizRequestVersionConflict() throws Exception {
        // 创建+提交 → version 从 0 递增到 1
        String id = createAndSubmitRequest();

        // 用过期 version=0 撤回 → 乐观锁冲突 (DB version=1)
        Map<String, Object> body = buildWithdrawBody("信息需补充", 0);
        MvcResult result = performPost(API_PATH + "/" + id + "/withdraw", body, mockUser("biz"));

        assertEquals(409, result.getResponse().getStatus(), "版本冲突 HTTP 应为 409");
        assertError(result, ERROR_CODE_VERSION_CONFLICT);
        assertTraceIdPresent(result);
    }

    // ===== CT-7: 审计落库 =====

    @Test
    @DisplayName("CT-7: 审计落库 (@Auditable WITHDRAW → sys_operation_log 有记录)")
    void testWithdrawBizRequestAuditLog() throws Exception {
        String id = createAndSubmitRequest();

        Map<String, Object> body = buildWithdrawBody("信息需补充", null);
        MvcResult result = performPost(API_PATH + "/" + id + "/withdraw", body, mockUser("biz"));
        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);

        String traceId = parseResult(result).traceId();
        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_operation_log " +
                        "WHERE biz_type = 'biz_request' AND operation_type = 'WITHDRAW' " +
                        "AND trace_id = ?",
                Integer.class, traceId);

        assertNotNull(auditCount, "审计日志查询不应返回 null");
        assertTrue(auditCount >= 1,
                "sys_operation_log 应至少有 1 条 WITHDRAW 审计记录 (traceId=" + traceId + "), 实际: " + auditCount);

        jdbcTemplate.query("SELECT biz_id, module, operation_type, biz_type FROM sys_operation_log " +
                        "WHERE biz_type = 'biz_request' AND operation_type = 'WITHDRAW' AND trace_id = ?",
                rs -> {
                    assertEquals(id, rs.getString("biz_id"), "审计记录 biz_id 应为撤回的申请单 ID");
                    assertEquals("sample", rs.getString("module"), "审计记录 module 应为 sample");
                    assertEquals("WITHDRAW", rs.getString("operation_type"), "审计记录 operation_type 应为 WITHDRAW");
                    assertEquals("biz_request", rs.getString("biz_type"), "审计记录 biz_type 应为 biz_request");
                }, traceId);
    }

    // ===== CT-8: traceId 透传 =====

    @Test
    @DisplayName("CT-8: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)")
    void testWithdrawBizRequestTraceIdPropagation() throws Exception {
        String id = createAndSubmitRequest();

        Map<String, Object> body = buildWithdrawBody("信息需补充", null);
        MvcResult result = performPost(API_PATH + "/" + id + "/withdraw", body, mockUser("biz"));

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
