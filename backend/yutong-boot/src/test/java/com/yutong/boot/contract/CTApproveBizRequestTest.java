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
 * GA2-L189 CT-approveBizRequest 契约测试。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-approveBizRequest 验收标准)
 * <p>策略来源: operation-policies.yaml approveBizRequest → workflowAction profile (optimistic-version-and-todo-assignee)
 * <p>契约来源: openapi.yaml POST /api/v1/biz-requests/{id}/approve (x-permission: biz:request:approve, x-error-codes: [BIZ-404001, BIZ-409002])
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功审批 (biz 创建+提交, admin 审批, SUBMITTED→APPROVED, 200, code=0)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (viewer 无 biz:request:approve, 403 AUTH-403001)</li>
 *   <li>CT-4 申请单不存在 (404 BIZ-404001)</li>
 *   <li>CT-5 状态冲突 (DRAFT→approve 不允许, 409 BIZ-409002)</li>
 *   <li>CT-6 版本冲突 (stale version, 409 SYS-409001)</li>
 *   <li>CT-7 审计落库 (sys_operation_log APPROVE 记录)</li>
 *   <li>CT-8 traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)</li>
 *   <li>CT-9 数据范围拒绝 (approver CUSTOM 空白名单, 403 AUTH-403002)</li>
 * </ol>
 *
 * <p>数据隔离: 每个用例独立创建测试数据 (UUID 后缀 title), @AfterEach 物理清理。
 *
 * <p>用户策略说明:
 * <ul>
 *   <li>biz 用户: 创建+提交申请单 (有 biz:request:add/submit, SELF 数据范围)</li>
 *   <li>admin 用户: 执行审批 (有 "*" 通配权限, ALL 数据范围, 可跨用户访问)</li>
 *   <li>approver 用户: 有 biz:request:approve 但 CUSTOM 空白名单 → 数据范围拒绝</li>
 *   <li>viewer 用户: 无 biz:request:approve → 权限拒绝</li>
 * </ul>
 */
@DisplayName("CT-approveBizRequest: POST /api/v1/biz-requests/{id}/approve 契约测试")
class CTApproveBizRequestTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/biz-requests";
    private static final String ERROR_CODE_FORBIDDEN = "AUTH-403001";
    private static final String ERROR_CODE_DATA_SCOPE_DENIED = "AUTH-403002";
    private static final String ERROR_CODE_NOT_FOUND = "BIZ-404001";
    private static final String ERROR_CODE_STATUS_CONFLICT = "BIZ-409002";
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
        req.setTitle("CT-L189-A-" + UUID.randomUUID().toString().substring(0, 8));
        req.setCustomerId("01CTCUSTOMER0000000000000001");
        req.setCustomerNameSnapshot("CT审批测试客户");
        req.setApplyReason("CT审批测试申请原因");
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

    /** 构造审批请求体。 */
    private Map<String, Object> buildApproveBody(String opinion, Integer version) {
        Map<String, Object> body = new HashMap<>();
        body.put("opinion", opinion);
        body.put("version", version);
        body.put("idempotencyKey", "approve-" + UUID.randomUUID());
        return body;
    }

    /** 获取申请单当前 version (通过 GET 详情接口)。 */
    private int getCurrentVersion(String id, HttpHeaders headers) throws Exception {
        MvcResult result = performGet(API_PATH + "/" + id, headers);
        assertSuccess(result);
        return parseResult(result).data().get("version").asInt();
    }

    // ===== CT-1: 成功审批 =====

    @Test
    @DisplayName("CT-1: 成功审批 (biz 创建+提交, admin 审批, SUBMITTED→APPROVED, 200, code=0)")
    void testApproveBizRequestSuccess() throws Exception {
        String id = createAndSubmitRequest();

        // admin 获取当前 version, 然后审批
        HttpHeaders adminHeaders = mockUser("admin");
        int currentVersion = getCurrentVersion(id, adminHeaders);

        Map<String, Object> body = buildApproveBody("同意", currentVersion);
        MvcResult result = performPost(API_PATH + "/" + id + "/approve", body, adminHeaders);

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        ResultNode node = parseResult(result);
        JsonNode data = node.data();
        assertNotNull(data, "data 不应为 null");
        assertEquals(id, data.get("id").asText(), "返回 id 应与请求 id 一致");
        assertEquals("APPROVED", data.get("requestStatus").asText(), "审批后状态应为 APPROVED");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testApproveBizRequestUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (viewer 无 biz:request:approve, 403 AUTH-403001)")
    void testApproveBizRequestForbidden() throws Exception {
        String id = createAndSubmitRequest();

        Map<String, Object> body = buildApproveBody("同意", null);
        MvcResult result = performPost(API_PATH + "/" + id + "/approve", body, mockUser("viewer"));

        assertEquals(403, result.getResponse().getStatus(), "viewer 无审批权限, HTTP 应为 403");
        assertError(result, ERROR_CODE_FORBIDDEN);
        assertTraceIdPresent(result);
    }

    // ===== CT-4: 申请单不存在 =====

    @Test
    @DisplayName("CT-4: 申请单不存在 (404 BIZ-404001)")
    void testApproveBizRequestNotFound() throws Exception {
        String nonExistentId = "01NOTEXIST00000000000000CT";

        Map<String, Object> body = buildApproveBody("同意", null);
        MvcResult result = performPost(API_PATH + "/" + nonExistentId + "/approve", body, mockUser("admin"));

        assertEquals(404, result.getResponse().getStatus(), "申请单不存在, HTTP 应为 404");
        assertError(result, ERROR_CODE_NOT_FOUND);
        assertTraceIdPresent(result);
    }

    // ===== CT-5: 状态冲突 =====

    @Test
    @DisplayName("CT-5: 状态冲突 (DRAFT→approve 不允许, 409 BIZ-409002)")
    void testApproveBizRequestStatusConflict() throws Exception {
        // 创建草稿但不提交 → 状态为 DRAFT
        String id = createRequestAndTrack();

        // 直接审批 DRAFT 状态 → 状态机不允许 (APPROVE 仅允许 SUBMITTED)
        Map<String, Object> body = buildApproveBody("同意", 0);
        MvcResult result = performPost(API_PATH + "/" + id + "/approve", body, mockUser("admin"));

        assertEquals(409, result.getResponse().getStatus(), "状态冲突 HTTP 应为 409");
        assertError(result, ERROR_CODE_STATUS_CONFLICT);
        assertTraceIdPresent(result);
    }

    // ===== CT-6: 版本冲突 =====

    @Test
    @DisplayName("CT-6: 版本冲突 (stale version, 409 SYS-409001)")
    void testApproveBizRequestVersionConflict() throws Exception {
        // 创建+提交 → version 从 0 递增到 1
        String id = createAndSubmitRequest();

        // 用过期 version=0 审批 → 乐观锁冲突 (DB version=1)
        Map<String, Object> body = buildApproveBody("同意", 0);
        MvcResult result = performPost(API_PATH + "/" + id + "/approve", body, mockUser("admin"));

        assertEquals(409, result.getResponse().getStatus(), "版本冲突 HTTP 应为 409");
        assertError(result, ERROR_CODE_VERSION_CONFLICT);
        assertTraceIdPresent(result);
    }

    // ===== CT-7: 审计落库 =====

    @Test
    @DisplayName("CT-7: 审计落库 (@Auditable APPROVE → sys_operation_log 有记录)")
    void testApproveBizRequestAuditLog() throws Exception {
        String id = createAndSubmitRequest();

        HttpHeaders adminHeaders = mockUser("admin");
        int currentVersion = getCurrentVersion(id, adminHeaders);

        Map<String, Object> body = buildApproveBody("同意", currentVersion);
        MvcResult result = performPost(API_PATH + "/" + id + "/approve", body, adminHeaders);
        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);

        String traceId = parseResult(result).traceId();
        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_operation_log " +
                        "WHERE biz_type = 'biz_request' AND operation_type = 'APPROVE' " +
                        "AND trace_id = ?",
                Integer.class, traceId);

        assertNotNull(auditCount, "审计日志查询不应返回 null");
        assertTrue(auditCount >= 1,
                "sys_operation_log 应至少有 1 条 APPROVE 审计记录 (traceId=" + traceId + "), 实际: " + auditCount);

        jdbcTemplate.query("SELECT biz_id, module, operation_type, biz_type FROM sys_operation_log " +
                        "WHERE biz_type = 'biz_request' AND operation_type = 'APPROVE' AND trace_id = ?",
                rs -> {
                    assertEquals(id, rs.getString("biz_id"), "审计记录 biz_id 应为审批的申请单 ID");
                    assertEquals("sample", rs.getString("module"), "审计记录 module 应为 sample");
                    assertEquals("APPROVE", rs.getString("operation_type"), "审计记录 operation_type 应为 APPROVE");
                    assertEquals("biz_request", rs.getString("biz_type"), "审计记录 biz_type 应为 biz_request");
                }, traceId);
    }

    // ===== CT-8: traceId 透传 =====

    @Test
    @DisplayName("CT-8: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)")
    void testApproveBizRequestTraceIdPropagation() throws Exception {
        String id = createAndSubmitRequest();

        HttpHeaders adminHeaders = mockUser("admin");
        int currentVersion = getCurrentVersion(id, adminHeaders);

        Map<String, Object> body = buildApproveBody("同意", currentVersion);
        MvcResult result = performPost(API_PATH + "/" + id + "/approve", body, adminHeaders);

        assertEquals(200, result.getResponse().getStatus());
        ResultNode node = parseResult(result);
        assertNotNull(node.traceId(), "响应体应包含 traceId");
        assertTrue(node.traceId().length() >= 16 && node.traceId().length() <= 64,
                "traceId 长度应在 16-64 之间");

        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertNotNull(headerTraceId, "响应头应包含 X-Trace-Id");
        assertEquals(node.traceId(), headerTraceId, "响应头 X-Trace-Id 应与响应体 traceId 一致");
    }

    // ===== CT-9: 数据范围拒绝 =====

    @Test
    @DisplayName("CT-9: 数据范围拒绝 (approver CUSTOM 空白名单, 403 AUTH-403002)")
    void testApproveBizRequestDataScopeDenied() throws Exception {
        String id = createAndSubmitRequest();

        // approver 有 biz:request:approve 但 CUSTOM 空白名单 → 数据范围拒绝
        Map<String, Object> body = buildApproveBody("同意", null);
        MvcResult result = performPost(API_PATH + "/" + id + "/approve", body, mockUser("approver"));

        assertEquals(403, result.getResponse().getStatus(), "approver 数据范围拒绝, HTTP 应为 403");
        assertError(result, ERROR_CODE_DATA_SCOPE_DENIED);
        assertTraceIdPresent(result);
    }
}
