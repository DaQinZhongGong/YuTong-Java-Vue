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
 * GA2-L181 CT-updateCustomer 契约测试。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-updateCustomer 验收标准)
 * <p>策略来源: operation-policies.yaml updateCustomer → versionedUpdate profile (optimistic-version)
 * <p>契约来源: openapi.yaml PUT /api/v1/customers/{id} (x-permission: biz:customer:edit, x-error-codes: [BIZ-404002, BIZ-409005])
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功更新 (200, code=0, 字段更新 + traceId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (viewer 无 biz:customer:edit, 403 AUTH-403001)</li>
 *   <li>CT-4a 参数校验失败 - customerName 空 (400)</li>
 *   <li>CT-4b 参数校验失败 - status 非法 (400)</li>
 *   <li>CT-5 客户不存在 (404 BIZ-404002)</li>
 *   <li>CT-6 版本冲突 (乐观锁 SYS-409001)</li>
 *   <li>CT-7 重复编码 (409 BIZ-409005)</li>
 *   <li>CT-8 审计落库 (sys_operation_log UPDATE 记录)</li>
 *   <li>CT-9 traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)</li>
 * </ol>
 *
 * <p>数据隔离: 每个用例独立创建测试数据 (UUID 后缀 customerCode), @AfterEach 清理。
 */
@DisplayName("CT-updateCustomer: PUT /api/v1/customers/{id} 契约测试")
class CTUpdateCustomerTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/customers";
    private static final String ERROR_CODE_FORBIDDEN = "AUTH-403001";
    private static final String ERROR_CODE_NOT_FOUND = "BIZ-404002";
    private static final String ERROR_CODE_DUPLICATE = "BIZ-409005";
    private static final String ERROR_CODE_VERSION_CONFLICT = "SYS-409001";

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
        c.setCustomerCode("CT-L181-U-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        c.setCustomerName("CT更新测试客户");
        c.setContactName("测试联系人");
        c.setContactPhone("13800138000");
        c.setAddress("测试地址");
        c.setStatus("ENABLED");
        return c;
    }

    /** 创建客户并跟踪 ID (用于后续更新/清理)。返回创建的客户 ID。 */
    private String createCustomerAndTrack() throws Exception {
        Customer request = buildValidCustomer();
        MvcResult result = performPost(API_PATH, request, mockUser("biz"));
        assertSuccess(result);
        ResultNode node = parseResult(result);
        JsonNode data = node.data();
        assertNotNull(data, "创建响应 data 不应为 null");
        String id = data.get("id").asText();
        createdCustomerIds.add(id);
        return id;
    }

    /** 从响应提取并跟踪客户 ID。 */
    private String extractAndTrackCustomerId(MvcResult result) throws Exception {
        ResultNode node = parseResult(result);
        JsonNode data = node.data();
        assertNotNull(data, "成功响应 data 不应为 null");
        String id = data.get("id").asText();
        createdCustomerIds.add(id);
        return id;
    }

    /** 构造更新请求体 (保持原 code, 修改 name)。 */
    private Customer buildUpdateRequest(String originalCode, int version) {
        Customer c = new Customer();
        c.setCustomerCode(originalCode);
        c.setCustomerName("CT更新后客户-" + UUID.randomUUID().toString().substring(0, 4));
        c.setContactName("更新联系人");
        c.setContactPhone("13900139000");
        c.setAddress("更新地址");
        c.setStatus("ENABLED");
        c.setVersion(version);
        return c;
    }

    // ===== CT-1: 成功更新 =====

    @Test
    @DisplayName("CT-1: 成功更新客户 (biz 用户, 200, code=0, 字段更新 + traceId)")
    void testUpdateCustomerSuccess() throws Exception {
        String id = createCustomerAndTrack();

        // 先 GET 获取创建时的原始 code (避免重新生成 code 导致重复校验)
        MvcResult getResult = performGet(API_PATH + "/" + id, mockUser("biz"));
        assertSuccess(getResult);
        String originalCode = parseResult(getResult).data().get("customerCode").asText();

        Customer updateReq = buildUpdateRequest(originalCode, 0);
        MvcResult result = performPut(API_PATH + "/" + id, updateReq, mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        ResultNode node = parseResult(result);
        JsonNode data = node.data();
        assertEquals(id, data.get("id").asText(), "返回 id 应与请求 id 一致");
        assertEquals(updateReq.getCustomerName(), data.get("customerName").asText(), "customerName 应已更新");
        assertEquals("ENABLED", data.get("status").asText());
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testUpdateCustomerUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (viewer 无 biz:customer:edit, 403 AUTH-403001)")
    void testUpdateCustomerForbidden() throws Exception {
        String id = createCustomerAndTrack();
        Customer update = buildUpdateRequest("ANYCODE", 0);

        MvcResult result = performPut(API_PATH + "/" + id, update, mockUser("viewer"));

        assertEquals(403, result.getResponse().getStatus(), "viewer 无写权限, HTTP 应为 403");
        assertError(result, ERROR_CODE_FORBIDDEN);
        assertTraceIdPresent(result);
    }

    // ===== CT-4: 参数校验失败 =====

    @Test
    @DisplayName("CT-4a: 参数校验失败 - customerName 为空 (400)")
    void testUpdateCustomerValidationBlankName() throws Exception {
        String id = createCustomerAndTrack();
        Customer update = buildUpdateRequest("ANYCODE", 0);
        update.setCustomerName(""); // @NotBlank 校验

        MvcResult result = performPut(API_PATH + "/" + id, update, mockUser("biz"));

        assertEquals(400, result.getResponse().getStatus(), "customerName 为空, HTTP 应为 400");
        ResultNode node = parseResult(result);
        assertNotEquals("0", node.code(), "校验失败不应返回 code=0");
        assertTraceIdPresent(result);
    }

    @Test
    @DisplayName("CT-4b: 参数校验失败 - status 非法值 (400)")
    void testUpdateCustomerValidationInvalidStatus() throws Exception {
        String id = createCustomerAndTrack();
        Customer update = buildUpdateRequest("ANYCODE", 0);
        update.setStatus("INVALID"); // @Pattern(ENABLED|DISABLED) 校验

        MvcResult result = performPut(API_PATH + "/" + id, update, mockUser("biz"));

        assertEquals(400, result.getResponse().getStatus(), "status 非法, HTTP 应为 400");
        ResultNode node = parseResult(result);
        assertNotEquals("0", node.code(), "校验失败不应返回 code=0");
        assertTraceIdPresent(result);
    }

    // ===== CT-5: 客户不存在 =====

    @Test
    @DisplayName("CT-5: 客户不存在 (404 BIZ-404002)")
    void testUpdateCustomerNotFound() throws Exception {
        Customer update = buildUpdateRequest("ANYCODE", 0);
        String nonExistentId = "01NOTEXIST00000000000000CT";

        MvcResult result = performPut(API_PATH + "/" + nonExistentId, update, mockUser("biz"));

        assertEquals(404, result.getResponse().getStatus(), "客户不存在, HTTP 应为 404");
        assertError(result, ERROR_CODE_NOT_FOUND);
        assertTraceIdPresent(result);
    }

    // ===== CT-6: 版本冲突 (乐观锁) =====

    @Test
    @DisplayName("CT-6: 版本冲突 (versionedUpdate 乐观锁, 旧 version → SYS-409001)")
    void testUpdateCustomerVersionConflict() throws Exception {
        String id = createCustomerAndTrack();
        // 新建客户 version=0, 先 GET 原始 code
        MvcResult getResult = performGet(API_PATH + "/" + id, mockUser("biz"));
        String originalCode = parseResult(getResult).data().get("customerCode").asText();

        // 第一次更新 version=0 → 成功 (DB version 0→1)
        Customer firstUpdate = buildUpdateRequest(originalCode, 0);
        MvcResult firstResult = performPut(API_PATH + "/" + id, firstUpdate, mockUser("biz"));
        assertEquals(200, firstResult.getResponse().getStatus(), "第一次更新应成功");
        assertSuccess(firstResult);

        // 第二次更新仍用 version=0 (stale, DB 已是 version=1) → 乐观锁冲突 SYS-409001
        Customer staleUpdate = buildUpdateRequest(originalCode, 0);
        staleUpdate.setCustomerName("冲突更新-应失败");
        MvcResult conflictResult = performPut(API_PATH + "/" + id, staleUpdate, mockUser("biz"));

        assertEquals(409, conflictResult.getResponse().getStatus(), "版本冲突 HTTP 应为 409");
        assertError(conflictResult, ERROR_CODE_VERSION_CONFLICT);
        assertTraceIdPresent(conflictResult);
    }

    // ===== CT-7: 重复编码 =====

    @Test
    @DisplayName("CT-7: 重复编码 (更新时改为已存在 code, 409 BIZ-409005)")
    void testUpdateCustomerDuplicateCode() throws Exception {
        // 创建客户 A
        String idA = createCustomerAndTrack();
        // 创建客户 B (独立 code)
        String idB = createCustomerAndTrack();

        // GET 客户 B 的 code
        MvcResult getB = performGet(API_PATH + "/" + idB, mockUser("biz"));
        String codeB = parseResult(getB).data().get("customerCode").asText();

        // 更新客户 A, 把 code 改为客户 B 的 code → 409 BIZ-409005
        Customer update = buildUpdateRequest(codeB, 0);
        update.setCustomerName("尝试占用B的编码");
        MvcResult result = performPut(API_PATH + "/" + idA, update, mockUser("biz"));

        assertEquals(409, result.getResponse().getStatus(), "重复编码 HTTP 应为 409");
        assertError(result, ERROR_CODE_DUPLICATE);
        assertTraceIdPresent(result);
    }

    // ===== CT-8: 审计落库 =====

    @Test
    @DisplayName("CT-8: 审计落库 (@Auditable UPDATE → sys_operation_log 有记录)")
    void testUpdateCustomerAuditLog() throws Exception {
        String id = createCustomerAndTrack();
        MvcResult getResult = performGet(API_PATH + "/" + id, mockUser("biz"));
        String originalCode = parseResult(getResult).data().get("customerCode").asText();

        Customer update = buildUpdateRequest(originalCode, 0);
        MvcResult result = performPut(API_PATH + "/" + id, update, mockUser("biz"));
        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);

        String traceId = parseResult(result).traceId();
        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_operation_log " +
                        "WHERE biz_type = 'biz_customer' AND operation_type = 'UPDATE' " +
                        "AND trace_id = ?",
                Integer.class, traceId);

        assertNotNull(auditCount, "审计日志查询不应返回 null");
        assertTrue(auditCount >= 1,
                "sys_operation_log 应至少有 1 条 UPDATE 审计记录 (traceId=" + traceId + "), 实际: " + auditCount);

        jdbcTemplate.query("SELECT biz_id, module, operation_type, biz_type FROM sys_operation_log " +
                        "WHERE biz_type = 'biz_customer' AND operation_type = 'UPDATE' AND trace_id = ?",
                rs -> {
                    assertEquals(id, rs.getString("biz_id"), "审计记录 biz_id 应为更新的客户 ID");
                    assertEquals("sample", rs.getString("module"), "审计记录 module 应为 sample");
                    assertEquals("UPDATE", rs.getString("operation_type"), "审计记录 operation_type 应为 UPDATE");
                    assertEquals("biz_customer", rs.getString("biz_type"), "审计记录 biz_type 应为 biz_customer");
                }, traceId);
    }

    // ===== CT-9: traceId 透传 =====

    @Test
    @DisplayName("CT-9: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)")
    void testUpdateCustomerTraceIdPropagation() throws Exception {
        String id = createCustomerAndTrack();
        MvcResult getResult = performGet(API_PATH + "/" + id, mockUser("biz"));
        String originalCode = parseResult(getResult).data().get("customerCode").asText();

        Customer update = buildUpdateRequest(originalCode, 0);
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
