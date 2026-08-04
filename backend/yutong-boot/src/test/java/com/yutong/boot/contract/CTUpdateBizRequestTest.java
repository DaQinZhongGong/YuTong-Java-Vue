package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.yutong.sample.request.dto.SaveBizRequestRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GA2-L189 CT-updateBizRequest 契约测试。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-updateBizRequest 验收标准)
 * <p>策略来源: operation-policies.yaml updateBizRequest → businessDraftUpdate profile (optimistic-version)
 * <p>契约来源: openapi.yaml PUT /api/v1/biz-requests/{id} (x-permission: biz:request:edit)
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功更新 (200, code=0, 字段更新 + traceId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (viewer 无 biz:request:edit, 403 AUTH-403001)</li>
 *   <li>CT-4 参数校验失败 - title 为空 (400)</li>
 *   <li>CT-5 申请单不存在 (404 BIZ-404001)</li>
 *   <li>CT-6 版本冲突 (乐观锁 SYS-409001)</li>
 *   <li>CT-7 状态冲突 (更新 SUBMITTED 状态申请单, 409 SYS-409004)</li>
 *   <li>CT-8 审计落库 (sys_operation_log UPDATE 记录)</li>
 *   <li>CT-9 traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)</li>
 * </ol>
 */
@DisplayName("CT-updateBizRequest: PUT /api/v1/biz-requests/{id} 契约测试")
class CTUpdateBizRequestTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/biz-requests";
    private static final String ERROR_CODE_FORBIDDEN = "AUTH-403001";
    private static final String ERROR_CODE_NOT_FOUND = "BIZ-404001";
    private static final String ERROR_CODE_VERSION_CONFLICT = "SYS-409001";
    private static final String ERROR_CODE_STATUS_CONFLICT = "SYS-409004";

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
        req.setTitle("CT-L189-更新测试-" + UUID.randomUUID().toString().substring(0, 8));
        req.setCustomerId("01CTCUSTOMER0000000000000001");
        req.setCustomerNameSnapshot("CT测试客户");
        req.setApplyReason("CT测试申请原因");
        return req;
    }

    /** 创建申请单草稿并跟踪 ID。 */
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

    /** 构造更新请求体 (修改 title, 指定 version)。 */
    private SaveBizRequestRequest buildUpdateRequest(int version) {
        SaveBizRequestRequest req = new SaveBizRequestRequest();
        req.setTitle("CT-L189-更新后-" + UUID.randomUUID().toString().substring(0, 8));
        req.setCustomerId("01CTCUSTOMER0000000000000001");
        req.setCustomerNameSnapshot("CT更新客户");
        req.setApplyReason("CT更新原因");
        req.setVersion(version);
        return req;
    }

    /** 提交申请单 (biz 用户, version=null 跳过版本校验)。 */
    private void submitRequest(String id) throws Exception {
        java.util.Map<String, Object> actionBody = new java.util.HashMap<>();
        actionBody.put("version", null);
        actionBody.put("idempotencyKey", "submit-" + java.util.UUID.randomUUID());
        MvcResult result = performPost(API_PATH + "/" + id + "/submit", actionBody, mockUser("biz"));
        assertEquals(200, result.getResponse().getStatus(), "提交申请单应成功");
    }

    // ===== CT-1: 成功更新 =====

    @Test
    @DisplayName("CT-1: 成功更新申请单 (biz 用户, 200, code=0, 字段更新 + traceId)")
    void testUpdateBizRequestSuccess() throws Exception {
        String id = createRequestAndTrack();

        SaveBizRequestRequest updateReq = buildUpdateRequest(0);
        MvcResult result = performPut(API_PATH + "/" + id, updateReq, mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        ResultNode node = parseResult(result);
        JsonNode data = node.data();
        assertEquals(id, data.get("id").asText(), "返回 id 应与请求 id 一致");
        assertEquals(updateReq.getTitle(), data.get("title").asText(), "title 应已更新");
        assertEquals("DRAFT", data.get("requestStatus").asText(), "草稿编辑后状态应仍为 DRAFT");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景")
    void testUpdateBizRequestUnauthenticated() {
    }

    // ===== CT-3: 权限拒绝 =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (viewer 无 biz:request:edit, 403 AUTH-403001)")
    void testUpdateBizRequestForbidden() throws Exception {
        String id = createRequestAndTrack();
        SaveBizRequestRequest update = buildUpdateRequest(0);

        MvcResult result = performPut(API_PATH + "/" + id, update, mockUser("viewer"));

        assertEquals(403, result.getResponse().getStatus(), "viewer 无写权限, HTTP 应为 403");
        assertError(result, ERROR_CODE_FORBIDDEN);
        assertTraceIdPresent(result);
    }

    // ===== CT-4: 参数校验失败 =====

    @Test
    @DisplayName("CT-4: 参数校验失败 - title 为空 (400)")
    void testUpdateBizRequestValidationBlankTitle() throws Exception {
        String id = createRequestAndTrack();
        SaveBizRequestRequest update = buildUpdateRequest(0);
        update.setTitle(""); // @NotBlank 校验

        MvcResult result = performPut(API_PATH + "/" + id, update, mockUser("biz"));

        assertEquals(400, result.getResponse().getStatus(), "title 为空, HTTP 应为 400");
        ResultNode node = parseResult(result);
        assertNotEquals("0", node.code(), "校验失败不应返回 code=0");
        assertTraceIdPresent(result);
    }

    // ===== CT-5: 申请单不存在 =====

    @Test
    @DisplayName("CT-5: 申请单不存在 (404 BIZ-404001)")
    void testUpdateBizRequestNotFound() throws Exception {
        SaveBizRequestRequest update = buildUpdateRequest(0);
        String nonExistentId = "01NOTEXIST00000000000000CT";

        MvcResult result = performPut(API_PATH + "/" + nonExistentId, update, mockUser("biz"));

        assertEquals(404, result.getResponse().getStatus(), "申请单不存在, HTTP 应为 404");
        assertError(result, ERROR_CODE_NOT_FOUND);
        assertTraceIdPresent(result);
    }

    // ===== CT-6: 版本冲突 (乐观锁) =====

    @Test
    @DisplayName("CT-6: 版本冲突 (businessDraftUpdate 乐观锁, 旧 version → SYS-409001)")
    void testUpdateBizRequestVersionConflict() throws Exception {
        String id = createRequestAndTrack();

        // 第一次更新 version=0 → 成功 (DB version 0→1)
        SaveBizRequestRequest firstUpdate = buildUpdateRequest(0);
        MvcResult firstResult = performPut(API_PATH + "/" + id, firstUpdate, mockUser("biz"));
        assertEquals(200, firstResult.getResponse().getStatus(), "第一次更新应成功");
        assertSuccess(firstResult);

        // 第二次更新仍用 version=0 (stale, DB 已是 version=1) → 乐观锁冲突 SYS-409001
        SaveBizRequestRequest staleUpdate = buildUpdateRequest(0);
        MvcResult conflictResult = performPut(API_PATH + "/" + id, staleUpdate, mockUser("biz"));

        assertEquals(409, conflictResult.getResponse().getStatus(), "版本冲突 HTTP 应为 409");
        assertError(conflictResult, ERROR_CODE_VERSION_CONFLICT);
        assertTraceIdPresent(conflictResult);
    }

    // ===== CT-7: 状态冲突 (更新非 DRAFT/REJECTED 状态) =====

    @Test
    @DisplayName("CT-7: 状态冲突 (更新 SUBMITTED 状态申请单, 409 SYS-409004)")
    void testUpdateBizRequestStatusConflict() throws Exception {
        String id = createRequestAndTrack();

        // 提交申请单 → 状态变为 SUBMITTED, version 递增
        submitRequest(id);

        // GET 获取当前 version
        MvcResult getResult = performGet(API_PATH + "/" + id, mockUser("biz"));
        assertSuccess(getResult);
        int currentVersion = parseResult(getResult).data().get("version").asInt();

        // 用正确 version 更新 → 版本校验通过, 但状态校验失败 (SUBMITTED 不允许编辑)
        SaveBizRequestRequest update = buildUpdateRequest(currentVersion);
        MvcResult result = performPut(API_PATH + "/" + id, update, mockUser("biz"));

        assertEquals(409, result.getResponse().getStatus(), "状态冲突 HTTP 应为 409");
        assertError(result, ERROR_CODE_STATUS_CONFLICT);
        assertTraceIdPresent(result);
    }

    // ===== CT-8: 审计落库 =====

    @Test
    @DisplayName("CT-8: 审计落库 (@Auditable UPDATE → sys_operation_log 有记录)")
    void testUpdateBizRequestAuditLog() throws Exception {
        String id = createRequestAndTrack();

        SaveBizRequestRequest update = buildUpdateRequest(0);
        MvcResult result = performPut(API_PATH + "/" + id, update, mockUser("biz"));
        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);

        String traceId = parseResult(result).traceId();
        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_operation_log " +
                        "WHERE biz_type = 'biz_request' AND operation_type = 'UPDATE' " +
                        "AND trace_id = ?",
                Integer.class, traceId);

        assertNotNull(auditCount, "审计日志查询不应返回 null");
        assertTrue(auditCount >= 1,
                "sys_operation_log 应至少有 1 条 UPDATE 审计记录 (traceId=" + traceId + "), 实际: " + auditCount);

        jdbcTemplate.query("SELECT biz_id, module, operation_type, biz_type FROM sys_operation_log " +
                        "WHERE biz_type = 'biz_request' AND operation_type = 'UPDATE' AND trace_id = ?",
                rs -> {
                    assertEquals(id, rs.getString("biz_id"), "审计记录 biz_id 应为更新的申请单 ID");
                    assertEquals("sample", rs.getString("module"), "审计记录 module 应为 sample");
                    assertEquals("UPDATE", rs.getString("operation_type"), "审计记录 operation_type 应为 UPDATE");
                    assertEquals("biz_request", rs.getString("biz_type"), "审计记录 biz_type 应为 biz_request");
                }, traceId);
    }

    // ===== CT-9: traceId 透传 =====

    @Test
    @DisplayName("CT-9: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)")
    void testUpdateBizRequestTraceIdPropagation() throws Exception {
        String id = createRequestAndTrack();

        SaveBizRequestRequest update = buildUpdateRequest(0);
        MvcResult result = performPut(API_PATH + "/" + id, update, mockUser("biz"));

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
