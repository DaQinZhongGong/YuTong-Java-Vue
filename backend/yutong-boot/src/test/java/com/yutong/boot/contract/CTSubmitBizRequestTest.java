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
 * CT-submitBizRequest 契约测试。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-submitBizRequest 验收标准)
 * <p>策略来源: operation-policies.yaml submitBizRequest → workflowAction profile
 * <p>契约来源: openapi.yaml POST /api/v1/biz-requests/{id}/submit
 *              (x-permission: biz:request:submit, x-error-codes: [BIZ-404001, BIZ-409001, SYS-409001])
 *
 * <p>状态机: DRAFT/REJECTED → SUBMITTED (submit)
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功提交 (biz 创建+提交, DRAFT→SUBMITTED, 200, code=0)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (viewer 无 biz:request:submit, 403 AUTH-403001)</li>
 *   <li>CT-4 申请单不存在 (404 BIZ-404001)</li>
 *   <li>CT-5 状态冲突 (SUBMITTED→submit 不允许, 409 BIZ-409001)</li>
 *   <li>CT-6 版本冲突 (stale version, 409 SYS-409001)</li>
 *   <li>CT-7 审计落库 (@Auditable SUBMIT → sys_operation_log 有记录)</li>
 *   <li>CT-8 traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)</li>
 * </ol>
 *
 * <p>数据隔离: 每个用例独立创建测试数据 (UUID 后缀 title), @AfterEach 物理清理。
 */
@DisplayName("CT-submitBizRequest: POST /api/v1/biz-requests/{id}/submit 契约测试")
class CTSubmitBizRequestTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/biz-requests";
    private static final String ERROR_CODE_FORBIDDEN = "AUTH-403001";
    private static final String ERROR_CODE_NOT_FOUND = "BIZ-404001";
    private static final String ERROR_CODE_STATUS_CONFLICT = "BIZ-409001";
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
        req.setTitle("CT-SUBMIT-" + UUID.randomUUID().toString().substring(0, 8));
        req.setCustomerId("01CTCUSTOMER0000000000000001");
        req.setCustomerNameSnapshot("CT提交测试客户");
        req.setApplyReason("CT提交测试申请原因");
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

    /** 提交申请单 (指定用户, version=null 跳过版本校验)。 */
    private MvcResult submitRequest(String id, Integer version, HttpHeaders headers) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("version", version);
        body.put("idempotencyKey", "submit-" + UUID.randomUUID());
        return performPost(API_PATH + "/" + id + "/submit", body, headers);
    }

    /** 获取申请单当前 version (通过 GET 详情接口)。 */
    private int getCurrentVersion(String id, HttpHeaders headers) throws Exception {
        MvcResult result = performGet(API_PATH + "/" + id, headers);
        assertSuccess(result);
        return parseResult(result).data().get("version").asInt();
    }

    /** 创建+提交+审批通过 (biz 创建提交, admin 审批), 返回申请单 ID (状态 APPROVED)。 */
    private String createSubmittedAndApprove() throws Exception {
        String id = createRequestAndTrack();
        submitRequest(id, null, mockUser("biz"));
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

    // ===== CT-1: 成功提交 =====

    @Test
    @DisplayName("CT-1: 成功提交 (biz 创建+提交, DRAFT→SUBMITTED, 200, code=0)")
    void testSubmitBizRequestSuccess() throws Exception {
        String id = createRequestAndTrack();

        MvcResult result = submitRequest(id, null, mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        ResultNode node = parseResult(result);
        JsonNode data = node.data();
        assertNotNull(data, "data 不应为 null");
        assertEquals(id, data.get("id").asText(), "返回 id 应与请求 id 一致");
        assertEquals("SUBMITTED", data.get("requestStatus").asText(), "提交后状态应为 SUBMITTED");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testSubmitBizRequestUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (viewer 无 biz:request:submit, 403 AUTH-403001)")
    void testSubmitBizRequestForbidden() throws Exception {
        String id = createRequestAndTrack();

        MvcResult result = submitRequest(id, null, mockUser("viewer"));

        assertEquals(403, result.getResponse().getStatus(), "viewer 无提交权限, HTTP 应为 403");
        assertError(result, ERROR_CODE_FORBIDDEN);
        assertTraceIdPresent(result);
    }

    // ===== CT-4: 申请单不存在 =====

    @Test
    @DisplayName("CT-4: 申请单不存在 (404 BIZ-404001)")
    void testSubmitBizRequestNotFound() throws Exception {
        String nonExistentId = "01NOTEXIST00000000000000CT";

        MvcResult result = submitRequest(nonExistentId, null, mockUser("biz"));

        assertEquals(404, result.getResponse().getStatus(), "申请单不存在, HTTP 应为 404");
        assertError(result, ERROR_CODE_NOT_FOUND);
        assertTraceIdPresent(result);
    }

    // ===== CT-5: 状态冲突 =====

    @Test
    @DisplayName("CT-5: 状态冲突 (APPROVED→submit 不允许, 409 BIZ-409001)")
    void testSubmitBizRequestStatusConflict() throws Exception {
        // 创建+提交+审批通过 → 状态为 APPROVED
        String id = createSubmittedAndApprove();

        // 从 APPROVED 提交 → 不允许 (submit 仅允许 DRAFT/REJECTED; APPROVED 非幂等目标)
        MvcResult result = submitRequest(id, null, mockUser("admin"));

        assertEquals(409, result.getResponse().getStatus(), "状态冲突 HTTP 应为 409");
        assertError(result, ERROR_CODE_STATUS_CONFLICT);
        assertTraceIdPresent(result);
    }

    // ===== CT-6: 版本冲突 =====

    @Test
    @DisplayName("CT-6: 版本冲突 (stale version, 409 SYS-409001)")
    void testSubmitBizRequestVersionConflict() throws Exception {
        String id = createRequestAndTrack();

        // 用一个不匹配的 version 提交 → 乐观锁冲突
        // 创建后 version=0, 用 version=999 提交
        MvcResult result = submitRequest(id, 999, mockUser("biz"));

        assertEquals(409, result.getResponse().getStatus(), "版本冲突 HTTP 应为 409");
        assertError(result, ERROR_CODE_VERSION_CONFLICT);
        assertTraceIdPresent(result);
    }

    // ===== CT-7: 审计落库 =====

    @Test
    @DisplayName("CT-7: 审计落库 (@Auditable SUBMIT → sys_operation_log 有记录)")
    void testSubmitBizRequestAuditLog() throws Exception {
        String id = createRequestAndTrack();

        MvcResult result = submitRequest(id, null, mockUser("biz"));
        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);

        String traceId = parseResult(result).traceId();
        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_operation_log " +
                        "WHERE biz_type = 'biz_request' AND operation_type = 'SUBMIT' " +
                        "AND trace_id = ?",
                Integer.class, traceId);

        assertNotNull(auditCount, "审计日志查询不应返回 null");
        assertTrue(auditCount >= 1,
                "sys_operation_log 应至少有 1 条 SUBMIT 审计记录 (traceId=" + traceId + "), 实际: " + auditCount);

        jdbcTemplate.query("SELECT biz_id, module, operation_type, biz_type FROM sys_operation_log " +
                        "WHERE biz_type = 'biz_request' AND operation_type = 'SUBMIT' AND trace_id = ?",
                rs -> {
                    assertEquals(id, rs.getString("biz_id"), "审计记录 biz_id 应为提交的申请单 ID");
                    assertEquals("sample", rs.getString("module"), "审计记录 module 应为 sample");
                    assertEquals("SUBMIT", rs.getString("operation_type"), "审计记录 operation_type 应为 SUBMIT");
                    assertEquals("biz_request", rs.getString("biz_type"), "审计记录 biz_type 应为 biz_request");
                }, traceId);
    }

    // ===== CT-8: traceId 透传 =====

    @Test
    @DisplayName("CT-8: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)")
    void testSubmitBizRequestTraceIdPropagation() throws Exception {
        String id = createRequestAndTrack();

        MvcResult result = submitRequest(id, null, mockUser("biz"));

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
