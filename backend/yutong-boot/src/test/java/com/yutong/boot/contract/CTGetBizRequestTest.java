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
 * GA2-L189 CT-getBizRequest 契约测试。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-getBizRequest 验收标准)
 * <p>策略来源: operation-policies.yaml getBizRequest → read profile (queryOne)
 * <p>契约来源: openapi.yaml GET /api/v1/biz-requests/{id} (x-permission: biz:request:detail, x-error-codes: [BIZ-404001])
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功查询 (biz 用户, 200, code=0, 返回 BizRequestDetailVO + traceId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (approver 无 biz:request:detail, 403 AUTH-403001)</li>
 *   <li>CT-4 申请单不存在 (404 BIZ-404001)</li>
 *   <li>CT-5 已删除申请单 (selectById 过滤逻辑删除, 404 BIZ-404001)</li>
 *   <li>CT-6 敏感字段脱敏 (viewer, customerNameSnapshot/applicantNameSnapshot/applyReason 脱敏为 ***)</li>
 *   <li>CT-7 敏感字段不脱敏 (biz, 原文)</li>
 *   <li>CT-8 traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)</li>
 * </ol>
 *
 * <p>数据隔离: 每个用例独立创建测试数据 (UUID 后缀 title), @AfterEach 物理清理。
 */
@DisplayName("CT-getBizRequest: GET /api/v1/biz-requests/{id} 契约测试")
class CTGetBizRequestTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/biz-requests";
    private static final String ERROR_CODE_FORBIDDEN = "AUTH-403001";
    private static final String ERROR_CODE_NOT_FOUND = "BIZ-404001";

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
        req.setTitle("CT-L189-G-" + UUID.randomUUID().toString().substring(0, 8));
        req.setCustomerId("01CTCUSTOMER0000000000000001");
        req.setCustomerNameSnapshot("CT详情测试客户");
        req.setApplyReason("CT详情测试申请原因");
        return req;
    }

    /** 创建申请单草稿并跟踪 ID。返回创建的申请单 ID。 */
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

    // ===== CT-1: 成功查询 =====

    @Test
    @DisplayName("CT-1: 成功查询申请单详情 (biz 用户, 200, code=0, 返回 BizRequestDetailVO + traceId)")
    void testGetBizRequestSuccess() throws Exception {
        String id = createRequestAndTrack();

        MvcResult result = performGet(API_PATH + "/" + id, mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        ResultNode node = parseResult(result);
        JsonNode data = node.data();
        assertNotNull(data, "data 不应为 null");
        assertEquals(id, data.get("id").asText(), "返回 id 应与请求 id 一致");
        assertNotNull(data.get("requestNo"), "requestNo 不应为 null");
        assertNotNull(data.get("title"), "title 不应为 null");
        assertNotNull(data.get("requestStatus"), "requestStatus 不应为 null");
        assertEquals("DRAFT", data.get("requestStatus").asText(), "新建草稿状态应为 DRAFT");
        assertNotNull(data.get("version"), "version 不应为 null");
        // BizRequestDetailVO 应包含 items 和 approvals 数组
        assertNotNull(data.get("items"), "items 不应为 null");
        assertTrue(data.get("items").isArray(), "items 应为数组");
        assertNotNull(data.get("approvals"), "approvals 不应为 null");
        assertTrue(data.get("approvals").isArray(), "approvals 应为数组");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testGetBizRequestUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (approver 无 biz:request:detail, 403 AUTH-403001)")
    void testGetBizRequestForbidden() throws Exception {
        String id = createRequestAndTrack();

        MvcResult result = performGet(API_PATH + "/" + id, mockUser("approver"));

        assertEquals(403, result.getResponse().getStatus(), "approver 无详情权限, HTTP 应为 403");
        assertError(result, ERROR_CODE_FORBIDDEN);
        assertTraceIdPresent(result);
    }

    // ===== CT-4: 申请单不存在 =====

    @Test
    @DisplayName("CT-4: 申请单不存在 (404 BIZ-404001)")
    void testGetBizRequestNotFound() throws Exception {
        String nonExistentId = "01NOTEXIST00000000000000CT";

        MvcResult result = performGet(API_PATH + "/" + nonExistentId, mockUser("biz"));

        assertEquals(404, result.getResponse().getStatus(), "申请单不存在, HTTP 应为 404");
        assertError(result, ERROR_CODE_NOT_FOUND);
        assertTraceIdPresent(result);
    }

    // ===== CT-5: 已删除申请单 =====

    @Test
    @DisplayName("CT-5: 已删除申请单 (selectById 过滤逻辑删除, 404 BIZ-404001)")
    void testGetBizRequestDeleted() throws Exception {
        String id = createRequestAndTrack();

        // 先删除 (逻辑删除)
        MvcResult deleteResult = performDelete(API_PATH + "/" + id, mockUser("biz"));
        assertEquals(200, deleteResult.getResponse().getStatus(), "删除应成功");
        assertSuccess(deleteResult);

        // 再 GET: selectById 过滤 deleted=true → 返回 null → 404 BIZ-404001
        MvcResult result = performGet(API_PATH + "/" + id, mockUser("biz"));

        assertEquals(404, result.getResponse().getStatus(), "已删除申请单查询应返回 404");
        assertError(result, ERROR_CODE_NOT_FOUND);
        assertTraceIdPresent(result);
    }

    // ===== CT-6: 敏感字段脱敏 (viewer) =====

    @Test
    @DisplayName("CT-6: 敏感字段脱敏 (viewer, customerNameSnapshot/applicantNameSnapshot/applyReason 脱敏为 ***)")
    void testGetBizRequestMaskedForViewer() throws Exception {
        String id = createRequestAndTrack();

        MvcResult result = performGet(API_PATH + "/" + id, mockUser("viewer"));

        assertEquals(200, result.getResponse().getStatus(), "viewer 有 biz:request:detail, HTTP 应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");
        assertEquals(id, data.get("id").asText(), "返回 id 应与请求 id 一致");

        // viewer 无 includeSensitive → 敏感字段脱敏为 ***
        String customerName = data.get("customerNameSnapshot") != null ? data.get("customerNameSnapshot").asText() : null;
        String applicantName = data.get("applicantNameSnapshot") != null ? data.get("applicantNameSnapshot").asText() : null;
        String applyReason = data.get("applyReason") != null ? data.get("applyReason").asText() : null;

        if (customerName != null) {
            assertEquals("***", customerName, "viewer 查询 customerNameSnapshot 应脱敏为 ***");
        }
        if (applicantName != null) {
            assertEquals("***", applicantName, "viewer 查询 applicantNameSnapshot 应脱敏为 ***");
        }
        if (applyReason != null) {
            assertEquals("***", applyReason, "viewer 查询 applyReason 应脱敏为 ***");
        }
    }

    // ===== CT-7: 敏感字段不脱敏 (biz) =====

    @Test
    @DisplayName("CT-7: 敏感字段不脱敏 (biz 有 includeSensitive, 原文返回)")
    void testGetBizRequestUnmaskedForBiz() throws Exception {
        SaveBizRequestRequest request = buildValidRequest();
        MvcResult createResult = performPost(API_PATH, request, mockUser("biz"));
        assertSuccess(createResult);
        String id = parseResult(createResult).data().get("id").asText();
        createdRequestIds.add(id);

        MvcResult result = performGet(API_PATH + "/" + id, mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus(), "biz 用户 HTTP 应为 200");
        assertSuccess(result);

        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");

        // biz 有 includeSensitive → 敏感字段原文返回
        String customerName = data.get("customerNameSnapshot").asText();
        assertNotNull(customerName, "customerNameSnapshot 不应为 null");
        assertEquals(request.getCustomerNameSnapshot(), customerName, "biz 用户应看到原文 customerNameSnapshot");
        assertNotEquals("***", customerName, "biz 用户 customerNameSnapshot 不应脱敏");

        String applyReason = data.get("applyReason").asText();
        assertNotNull(applyReason, "applyReason 不应为 null");
        assertEquals(request.getApplyReason(), applyReason, "biz 用户应看到原文 applyReason");
        assertNotEquals("***", applyReason, "biz 用户 applyReason 不应脱敏");
    }

    // ===== CT-8: traceId 透传 =====

    @Test
    @DisplayName("CT-8: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)")
    void testGetBizRequestTraceIdPropagation() throws Exception {
        String id = createRequestAndTrack();

        MvcResult result = performGet(API_PATH + "/" + id, mockUser("biz"));

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
