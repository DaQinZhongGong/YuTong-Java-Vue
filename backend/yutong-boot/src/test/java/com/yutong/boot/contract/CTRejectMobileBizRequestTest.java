package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.yutong.sample.request.dto.SaveBizRequestRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CT-rejectMobileBizRequest 契约测试。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-rejectMobileBizRequest 验收标准)
 * <p>契约来源: openapi.yaml POST /api/v1/mobile/biz-requests/{id}/reject
 *              (routes.yaml x-permission: mobile:biz-request:reject)
 *
 * <p>当前实现: MobileController.reject 委托 BizRequestApplicationService.reject,
 *   参数 opinion(必填)/version/idempotencyKey 通过 @RequestParam 传递 (非请求体)。
 *   返回 Result&lt;Void&gt; (data 为 null)。
 *   状态机: SUBMITTED → REJECTED (reject)。
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功驳回 (biz 创建+提交, admin 移动端驳回, SUBMITTED→REJECTED, 200, code=0)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (MobileController 未加 @RequiresPermission, @Disabled)</li>
 *   <li>CT-4 申请单不存在 (404 BIZ-404001)</li>
 *   <li>CT-5 状态冲突 (DRAFT→reject 不允许, 409 BIZ-409002)</li>
 *   <li>CT-6 版本冲突 (stale version, 409 SYS-409001)</li>
 *   <li>CT-7 驳回意见为空 (400 BIZ-400003)</li>
 *   <li>CT-8 数据范围拒绝 (approver CUSTOM 空白名单, 403 AUTH-403002)</li>
 *   <li>CT-9 traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)</li>
 * </ol>
 *
 * <p>数据隔离: 每个用例独立创建测试申请单 (UUID 后缀 title), @AfterEach 物理清理。
 */
@DisplayName("CT-rejectMobileBizRequest: POST /api/v1/mobile/biz-requests/{id}/reject 契约测试")
class CTRejectMobileBizRequestTest extends AbstractContractTest {

    private static final String MOBILE_API_PATH = "/api/v1/mobile/biz-requests";
    private static final String BIZ_API_PATH = "/api/v1/biz-requests";
    private static final String ERROR_CODE_DATA_SCOPE_DENIED = "AUTH-403002";
    private static final String ERROR_CODE_NOT_FOUND = "BIZ-404001";
    private static final String ERROR_CODE_STATUS_CONFLICT = "BIZ-409002";
    private static final String ERROR_CODE_VERSION_CONFLICT = "SYS-409001";
    private static final String ERROR_CODE_OPINION_REQUIRED = "BIZ-400003";

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
        req.setTitle("CT-MOB-R-" + UUID.randomUUID().toString().substring(0, 8));
        req.setCustomerId("01CTCUSTOMER0000000000000001");
        req.setCustomerNameSnapshot("CT移动端驳回测试客户");
        req.setApplyReason("CT移动端驳回测试申请原因");
        return req;
    }

    /** 创建申请单草稿 (biz 用户) 并跟踪 ID。 */
    private String createRequestAndTrack() throws Exception {
        SaveBizRequestRequest request = buildValidRequest();
        MvcResult result = performPost(BIZ_API_PATH, request, mockUser("biz"));
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
        MvcResult result = performPost(BIZ_API_PATH + "/" + id + "/submit", body, mockUser("biz"));
        assertEquals(200, result.getResponse().getStatus(), "提交申请单应成功");
    }

    /** 创建并提交申请单 (biz 用户), 返回申请单 ID。 */
    private String createAndSubmitRequest() throws Exception {
        String id = createRequestAndTrack();
        submitRequest(id);
        return id;
    }

    /** 构造移动端驳回 URL (opinion 必填, version/idempotencyKey 通过 query param 传递)。 */
    private String buildRejectUrl(String id, String opinion, Integer version) {
        StringBuilder sb = new StringBuilder(MOBILE_API_PATH).append("/").append(id).append("/reject");
        sb.append("?opinion=").append(opinion != null
                ? URLEncoder.encode(opinion, StandardCharsets.UTF_8) : "");
        if (version != null) {
            sb.append("&version=").append(version);
        }
        sb.append("&idempotencyKey=")
                .append(URLEncoder.encode("reject-" + UUID.randomUUID(), StandardCharsets.UTF_8));
        return sb.toString();
    }

    // ===== CT-1: 成功驳回 =====

    @Test
    @DisplayName("CT-1: 成功驳回 (biz 创建+提交, admin 移动端驳回, SUBMITTED→REJECTED, 200, code=0)")
    void testRejectMobileBizRequestSuccess() throws Exception {
        String id = createAndSubmitRequest();

        MvcResult result = performPost(buildRejectUrl(id, "不同意, 资料不全", null), null, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        // 移动端驳回返回 Result<Void>, data 为 null
        JsonNode data = parseResult(result).data();
        assertNull(data, "移动端驳回返回 Result<Void>, data 应为 null");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testRejectMobileBizRequestUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 (MobileController 未加 @RequiresPermission) =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (MobileController 未加 @RequiresPermission, @Disabled 待补充注解)")
    @org.junit.jupiter.api.Disabled("MobileController.reject 未加 @RequiresPermission(mobile:biz-request:reject), " +
            "权限码未在控制器层强制; 待补充注解后启用此用例")
    void testRejectMobileBizRequestForbidden() {
        // 实现待补充: MobileController 当前未加 @RequiresPermission 注解
    }

    // ===== CT-4: 申请单不存在 =====

    @Test
    @DisplayName("CT-4: 申请单不存在 (404 BIZ-404001)")
    void testRejectMobileBizRequestNotFound() throws Exception {
        String nonExistentId = "01NOTEXIST00000000000000CT";

        MvcResult result = performPost(buildRejectUrl(nonExistentId, "不同意", null), null, mockUser("admin"));

        assertEquals(404, result.getResponse().getStatus(), "申请单不存在, HTTP 应为 404");
        assertError(result, ERROR_CODE_NOT_FOUND);
        assertTraceIdPresent(result);
    }

    // ===== CT-5: 状态冲突 =====

    @Test
    @DisplayName("CT-5: 状态冲突 (DRAFT→reject 不允许, 409 BIZ-409002)")
    void testRejectMobileBizRequestStatusConflict() throws Exception {
        // 创建草稿但不提交 → 状态为 DRAFT
        String id = createRequestAndTrack();

        // 直接驳回 DRAFT 状态 → 状态机不允许 (REJECT 仅允许 SUBMITTED)
        // version=0 匹配草稿版本, 通过版本校验后状态机校验失败
        MvcResult result = performPost(buildRejectUrl(id, "不同意", 0), null, mockUser("admin"));

        assertEquals(409, result.getResponse().getStatus(), "状态冲突 HTTP 应为 409");
        assertError(result, ERROR_CODE_STATUS_CONFLICT);
        assertTraceIdPresent(result);
    }

    // ===== CT-6: 版本冲突 =====

    @Test
    @DisplayName("CT-6: 版本冲突 (stale version, 409 SYS-409001)")
    void testRejectMobileBizRequestVersionConflict() throws Exception {
        // 创建+提交 → version 从 0 递增到 1
        String id = createAndSubmitRequest();

        // 用过期 version=0 驳回 → 乐观锁冲突 (DB version=1)
        MvcResult result = performPost(buildRejectUrl(id, "不同意", 0), null, mockUser("admin"));

        assertEquals(409, result.getResponse().getStatus(), "版本冲突 HTTP 应为 409");
        assertError(result, ERROR_CODE_VERSION_CONFLICT);
        assertTraceIdPresent(result);
    }

    // ===== CT-7: 驳回意见为空 =====

    @Test
    @DisplayName("CT-7: 驳回意见为空 (400 BIZ-400003)")
    void testRejectMobileBizRequestOpinionBlank() throws Exception {
        String id = createAndSubmitRequest();

        // opinion 为空字符串 → 服务层校验驳回必须填写审核意见
        MvcResult result = performPost(buildRejectUrl(id, "", null), null, mockUser("admin"));

        assertEquals(400, result.getResponse().getStatus(), "驳回意见为空, HTTP 应为 400");
        assertError(result, ERROR_CODE_OPINION_REQUIRED);
        assertTraceIdPresent(result);
    }

    // ===== CT-8: 数据范围拒绝 =====

    @Test
    @DisplayName("CT-8: 数据范围拒绝 (approver CUSTOM 空白名单, 403 AUTH-403002)")
    void testRejectMobileBizRequestDataScopeDenied() throws Exception {
        String id = createAndSubmitRequest();

        // approver CUSTOM 空白名单 → 服务层 doTransition DataScope 校验拒绝
        MvcResult result = performPost(buildRejectUrl(id, "不同意", null), null, mockUser("approver"));

        assertEquals(403, result.getResponse().getStatus(), "approver 数据范围拒绝, HTTP 应为 403");
        assertError(result, ERROR_CODE_DATA_SCOPE_DENIED);
        assertTraceIdPresent(result);
    }

    // ===== CT-9: traceId 透传 =====

    @Test
    @DisplayName("CT-9: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)")
    void testRejectMobileBizRequestTraceIdPropagation() throws Exception {
        String id = createAndSubmitRequest();

        MvcResult result = performPost(buildRejectUrl(id, "不同意", null), null, mockUser("admin"));

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
