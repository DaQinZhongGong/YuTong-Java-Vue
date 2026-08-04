package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.yutong.sample.masterdata.domain.Customer;
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
 * GA2-L181 CT-getCustomer 契约测试 (operationId: getCustomer)。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-getCustomer 验收标准)
 * <p>策略来源: operation-policies.yaml getCustomer → read profile (queryOne)
 * <p>契约来源: openapi.yaml GET /api/v1/customers/{id} (x-permission: biz:customer:detail, x-error-codes: [BIZ-404002])
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功查询 (biz 用户, 200, code=0, 返回 Customer + traceId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (approver 无 biz:customer:detail, 403 AUTH-403001)</li>
 *   <li>CT-4 客户不存在 (404 BIZ-404002)</li>
 *   <li>CT-5 已删除客户 (404 BIZ-404002, selectById 过滤逻辑删除)</li>
 *   <li>CT-6 敏感字段脱敏 (viewer 无 biz:customer:phone:view, contactPhone 脱敏)</li>
 *   <li>CT-7 敏感字段不脱敏 (biz 有 biz:customer:phone:view, contactPhone 原文)</li>
 *   <li>CT-8 traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)</li>
 * </ol>
 *
 * <p>数据隔离: 每个用例独立创建测试数据 (UUID 后缀 customerCode), @AfterEach 物理清理。
 */
@DisplayName("CT-getCustomer: GET /api/v1/customers/{id} 契约测试")
class CTGetCustomerTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/customers";
    private static final String ERROR_CODE_FORBIDDEN = "AUTH-403001";
    private static final String ERROR_CODE_NOT_FOUND = "BIZ-404002";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final java.util.Set<String> createdCustomerIds = ConcurrentHashMap.newKeySet();

    @AfterEach
    void cleanupTestData() {
        for (String id : createdCustomerIds) {
            jdbcTemplate.update("DELETE FROM sys_operation_log WHERE biz_type = 'biz_customer' AND biz_id = ?", id);
        }
        for (String id : createdCustomerIds) {
            jdbcTemplate.update("DELETE FROM biz_customer WHERE id = ?", id);
        }
        createdCustomerIds.clear();
    }

    /** 构造合法 Customer 请求体 (唯一 code)。 */
    private Customer buildValidCustomer() {
        Customer c = new Customer();
        c.setCustomerCode("CT-L181-G-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        c.setCustomerName("CT详情测试客户");
        c.setContactName("测试联系人");
        c.setContactPhone("13800138000");
        c.setAddress("测试地址");
        c.setStatus("ENABLED");
        return c;
    }

    /** 创建客户并跟踪 ID。返回创建的客户 ID。 */
    private String createCustomerAndTrack() throws Exception {
        Customer request = buildValidCustomer();
        MvcResult result = performPost(API_PATH, request, mockUser("biz"));
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertNotNull(data, "创建响应 data 不应为 null");
        String id = data.get("id").asText();
        createdCustomerIds.add(id);
        return id;
    }

    // ===== CT-1: 成功查询 =====

    @Test
    @DisplayName("CT-1: 成功查询客户详情 (biz 用户, 200, code=0, 返回 Customer + traceId)")
    void testGetCustomerSuccess() throws Exception {
        String id = createCustomerAndTrack();

        MvcResult result = performGet(API_PATH + "/" + id, mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        ResultNode node = parseResult(result);
        JsonNode data = node.data();
        assertNotNull(data, "data 不应为 null");
        assertEquals(id, data.get("id").asText(), "返回 id 应与请求 id 一致");
        assertNotNull(data.get("customerCode"), "customerCode 不应为 null");
        assertNotNull(data.get("customerName"), "customerName 不应为 null");
        assertNotNull(data.get("status"), "status 不应为 null");
        assertEquals("ENABLED", data.get("status").asText(), "status 应为创建时的 ENABLED");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testGetCustomerUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (approver 无 biz:customer:detail, 403 AUTH-403001)")
    void testGetCustomerForbidden() throws Exception {
        String id = createCustomerAndTrack();

        MvcResult result = performGet(API_PATH + "/" + id, mockUser("approver"));

        assertEquals(403, result.getResponse().getStatus(), "approver 无详情权限, HTTP 应为 403");
        assertError(result, ERROR_CODE_FORBIDDEN);
        assertTraceIdPresent(result);
    }

    // ===== CT-4: 客户不存在 =====

    @Test
    @DisplayName("CT-4: 客户不存在 (404 BIZ-404002)")
    void testGetCustomerNotFound() throws Exception {
        String nonExistentId = "01NOTEXIST00000000000000CT";

        MvcResult result = performGet(API_PATH + "/" + nonExistentId, mockUser("biz"));

        assertEquals(404, result.getResponse().getStatus(), "客户不存在, HTTP 应为 404");
        assertError(result, ERROR_CODE_NOT_FOUND);
        assertTraceIdPresent(result);
    }

    // ===== CT-5: 已删除客户 =====

    @Test
    @DisplayName("CT-5: 已删除客户 (selectById 过滤逻辑删除, 404 BIZ-404002)")
    void testGetCustomerDeleted() throws Exception {
        String id = createCustomerAndTrack();

        // 先删除 (逻辑删除)
        MvcResult deleteResult = performDelete(API_PATH + "/" + id, mockUser("biz"));
        assertEquals(200, deleteResult.getResponse().getStatus(), "删除应成功");
        assertSuccess(deleteResult);

        // 再 GET: selectById 过滤 deleted=true → 返回 null → 404 BIZ-404002
        MvcResult result = performGet(API_PATH + "/" + id, mockUser("biz"));

        assertEquals(404, result.getResponse().getStatus(), "已删除客户查询应返回 404");
        assertError(result, ERROR_CODE_NOT_FOUND);
        assertTraceIdPresent(result);
    }

    // ===== CT-6: 敏感字段脱敏 (viewer) =====

    @Test
    @DisplayName("CT-6: 敏感字段脱敏 (viewer 无 biz:customer:phone:view, contactPhone 脱敏为 138****8000)")
    void testGetCustomerMaskedForViewer() throws Exception {
        String id = createCustomerAndTrack();

        MvcResult result = performGet(API_PATH + "/" + id, mockUser("viewer"));

        assertEquals(200, result.getResponse().getStatus(), "viewer 有 biz:customer:detail, HTTP 应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");
        assertEquals(id, data.get("id").asText(), "返回 id 应与请求 id 一致");

        // viewer 无 biz:customer:phone:view → contactPhone 脱敏
        String phone = data.get("contactPhone").asText();
        assertNotNull(phone, "contactPhone 不应为 null");
        assertTrue(phone.contains("****"), "viewer 查询 contactPhone 应脱敏: " + phone);
        assertNotEquals("13800138000", phone, "viewer 不应看到原文 phone");
    }

    // ===== CT-7: 敏感字段不脱敏 (biz 有 phone:view) =====

    @Test
    @DisplayName("CT-7: 敏感字段不脱敏 (biz 有 biz:customer:phone:view, contactPhone 原文)")
    void testGetCustomerUnmaskedForBiz() throws Exception {
        String id = createCustomerAndTrack();

        MvcResult result = performGet(API_PATH + "/" + id, mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus(), "biz 用户 HTTP 应为 200");
        assertSuccess(result);

        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");

        // biz 有 biz:customer:phone:view → contactPhone 不脱敏
        String phone = data.get("contactPhone").asText();
        assertNotNull(phone, "contactPhone 不应为 null");
        assertEquals("13800138000", phone, "biz 用户应看到原文 contactPhone (无脱敏)");
        assertFalse(phone.contains("****"), "biz 用户 contactPhone 不应脱敏");
    }

    // ===== CT-8: traceId 透传 =====

    @Test
    @DisplayName("CT-8: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)")
    void testGetCustomerTraceIdPropagation() throws Exception {
        String id = createCustomerAndTrack();

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
