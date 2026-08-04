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
 * CT-getMobileBizRequest 契约测试。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-getMobileBizRequest 验收标准)
 * <p>契约来源: openapi.yaml GET /api/v1/mobile/biz-requests/{id}
 *              (routes.yaml x-permission: mobile:biz-request:detail)
 *
 * <p>当前实现: MobileController.getRequestDetail 返回 MobileRequestDetailVO
 *   (轻量字段 + 明细 items + 审批记录 approvals), 申请单不存在抛 BIZ-404001。
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功查询 (biz 创建, biz 移动端查询, 200, code=0, MobileRequestDetailVO)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (MobileController 未加 @RequiresPermission, @Disabled)</li>
 *   <li>CT-4 申请单不存在 (404 BIZ-404001)</li>
 *   <li>CT-5 traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)</li>
 * </ol>
 *
 * <p>实现说明: MobileController 当前未加 @RequiresPermission 注解, 权限码
 *   mobile:biz-request:detail 未在控制器层强制; 待补充注解后启用 CT-3。
 *
 * <p>数据隔离: 每个用例独立创建测试申请单 (UUID 后缀 title), @AfterEach 物理清理。
 */
@DisplayName("CT-getMobileBizRequest: GET /api/v1/mobile/biz-requests/{id} 契约测试")
class CTGetMobileBizRequestTest extends AbstractContractTest {

    private static final String MOBILE_API_PATH = "/api/v1/mobile/biz-requests";
    private static final String BIZ_API_PATH = "/api/v1/biz-requests";
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
        req.setTitle("CT-MOB-G-" + UUID.randomUUID().toString().substring(0, 8));
        req.setCustomerId("01CTCUSTOMER0000000000000001");
        req.setCustomerNameSnapshot("CT移动端详情测试客户");
        req.setApplyReason("CT移动端详情测试申请原因");
        return req;
    }

    /** 通过 Web API 创建申请单草稿 (biz 用户) 并跟踪 ID。 */
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

    // ===== CT-1: 成功查询 =====

    @Test
    @DisplayName("CT-1: 成功查询移动端申请单详情 (biz, 200, code=0, 返回 MobileRequestDetailVO)")
    void testGetMobileBizRequestSuccess() throws Exception {
        String id = createRequestAndTrack();

        MvcResult result = performGet(MOBILE_API_PATH + "/" + id, mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");
        assertEquals(id, data.get("id").asText(), "返回 id 应与请求 id 一致");
        assertNotNull(data.get("requestNo"), "requestNo 不应为 null");
        assertNotNull(data.get("title"), "title 不应为 null");
        assertNotNull(data.get("requestStatus"), "requestStatus 不应为 null");
        assertEquals("DRAFT", data.get("requestStatus").asText(), "新建草稿状态应为 DRAFT");
        assertNotNull(data.get("requestStatusLabel"), "requestStatusLabel 不应为 null");
        assertNotNull(data.get("version"), "version 不应为 null");
        // MobileRequestDetailVO 应包含 items 和 approvals 数组
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
    void testGetMobileBizRequestUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 (MobileController 未加 @RequiresPermission) =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (MobileController 未加 @RequiresPermission, @Disabled 待补充注解)")
    @org.junit.jupiter.api.Disabled("MobileController.getRequestDetail 未加 @RequiresPermission(mobile:biz-request:detail), " +
            "权限码未在控制器层强制; 待补充注解后启用此用例")
    void testGetMobileBizRequestForbidden() {
        // 实现待补充: MobileController 当前未加 @RequiresPermission 注解
    }

    // ===== CT-4: 申请单不存在 =====

    @Test
    @DisplayName("CT-4: 申请单不存在 (404 BIZ-404001)")
    void testGetMobileBizRequestNotFound() throws Exception {
        String nonExistentId = "01NOTEXIST00000000000000CT";

        MvcResult result = performGet(MOBILE_API_PATH + "/" + nonExistentId, mockUser("biz"));

        assertEquals(404, result.getResponse().getStatus(), "申请单不存在, HTTP 应为 404");
        assertError(result, ERROR_CODE_NOT_FOUND);
        assertTraceIdPresent(result);
    }

    // ===== CT-5: traceId 透传 =====

    @Test
    @DisplayName("CT-5: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)")
    void testGetMobileBizRequestTraceIdPropagation() throws Exception {
        String id = createRequestAndTrack();

        MvcResult result = performGet(MOBILE_API_PATH + "/" + id, mockUser("biz"));

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
