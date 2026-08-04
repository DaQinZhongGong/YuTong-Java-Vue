package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.yutong.sample.masterdata.domain.Product;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GA2-L186 CT-deleteProduct 契约测试。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-deleteProduct 验收标准)
 * <p>策略来源: operation-policies.yaml deleteProduct → guardedDelete profile (natural-by-resource-state)
 * <p>契约来源: openapi.yaml DELETE /api/v1/products/{id} (x-permission: biz:product:delete, x-error-codes: [BIZ-404003, BIZ-409008])
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功删除 (200, code=0, 逻辑删除 deleted=true)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (viewer 无 biz:product:delete, 403 AUTH-403001)</li>
 *   <li>CT-4 商品不存在 (404 BIZ-404003)</li>
 *   <li>CT-5 已删除再删 (404 BIZ-404003, guardedDelete 自然幂等)</li>
 *   <li>CT-6 审计落库 (sys_operation_log DELETE 记录)</li>
 *   <li>CT-7 traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)</li>
 * </ol>
 *
 * <p>数据隔离: 每个用例独立创建测试数据 (UUID 后缀 productCode), @AfterEach 物理清理。
 */
@DisplayName("CT-deleteProduct: DELETE /api/v1/products/{id} 契约测试")
class CTDeleteProductTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/products";
    private static final String ERROR_CODE_FORBIDDEN = "AUTH-403001";
    private static final String ERROR_CODE_NOT_FOUND = "BIZ-404003";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final java.util.Set<String> createdProductIds = ConcurrentHashMap.newKeySet();

    @AfterEach
    void cleanupTestData() {
        for (String id : createdProductIds) {
            jdbcTemplate.update("DELETE FROM sys_operation_log WHERE biz_type = 'biz_product' AND biz_id = ?", id);
        }
        for (String id : createdProductIds) {
            jdbcTemplate.update("DELETE FROM biz_product WHERE id = ?", id);
        }
        createdProductIds.clear();
    }

    /** 构造合法 Product 请求体 (唯一 code)。 */
    private Product buildValidProduct() {
        Product p = new Product();
        p.setProductCode("CT-L186-D-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        p.setProductName("CT删除测试商品");
        p.setUnit("个");
        p.setPrice(new BigDecimal("99.99"));
        p.setStatus("ENABLED");
        return p;
    }

    /** 创建商品并跟踪 ID。 */
    private String createProductAndTrack() throws Exception {
        Product request = buildValidProduct();
        MvcResult result = performPost(API_PATH, request, mockUser("biz"));
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertNotNull(data, "创建响应 data 不应为 null");
        String id = data.get("id").asText();
        createdProductIds.add(id);
        return id;
    }

    // ===== CT-1: 成功删除 =====

    @Test
    @DisplayName("CT-1: 成功删除商品 (biz 用户, 200, code=0, 逻辑删除 deleted=true)")
    void testDeleteProductSuccess() throws Exception {
        String id = createProductAndTrack();

        MvcResult result = performDelete(API_PATH + "/" + id, mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        // 验证逻辑删除: deleted=true (物理查询绕过逻辑删除)
        Boolean deleted = jdbcTemplate.queryForObject(
                "SELECT deleted FROM biz_product WHERE id = ?", Boolean.class, id);
        assertNotNull(deleted, "商品记录应存在 (物理查询)");
        assertTrue(deleted, "删除后 deleted 应为 true (逻辑删除)");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testDeleteProductUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (viewer 无 biz:product:delete, 403 AUTH-403001)")
    void testDeleteProductForbidden() throws Exception {
        String id = createProductAndTrack();

        MvcResult result = performDelete(API_PATH + "/" + id, mockUser("viewer"));

        assertEquals(403, result.getResponse().getStatus(), "viewer 无删除权限, HTTP 应为 403");
        assertError(result, ERROR_CODE_FORBIDDEN);
        assertTraceIdPresent(result);

        // 验证商品未被删除 (权限拒绝不应有副作用)
        Boolean deleted = jdbcTemplate.queryForObject(
                "SELECT deleted FROM biz_product WHERE id = ?", Boolean.class, id);
        assertNotNull(deleted, "商品记录应存在");
        assertFalse(deleted, "权限拒绝时商品不应被删除");
    }

    // ===== CT-4: 商品不存在 =====

    @Test
    @DisplayName("CT-4: 商品不存在 (404 BIZ-404003)")
    void testDeleteProductNotFound() throws Exception {
        String nonExistentId = "01NOTEXIST00000000000000CT";

        MvcResult result = performDelete(API_PATH + "/" + nonExistentId, mockUser("biz"));

        assertEquals(404, result.getResponse().getStatus(), "商品不存在, HTTP 应为 404");
        assertError(result, ERROR_CODE_NOT_FOUND);
        assertTraceIdPresent(result);
    }

    // ===== CT-5: 已删除再删 =====

    @Test
    @DisplayName("CT-5: 已删除再删 (guardedDelete 自然幂等, 404 BIZ-404003)")
    void testDeleteProductAlreadyDeleted() throws Exception {
        String id = createProductAndTrack();

        // 第一次删除: 成功
        MvcResult firstResult = performDelete(API_PATH + "/" + id, mockUser("biz"));
        assertEquals(200, firstResult.getResponse().getStatus(), "第一次删除应成功");
        assertSuccess(firstResult);

        // 第二次删除: 已删除 → selectById 返回 null → 404 BIZ-404003
        MvcResult secondResult = performDelete(API_PATH + "/" + id, mockUser("biz"));
        assertEquals(404, secondResult.getResponse().getStatus(), "已删除再删应返回 404");
        assertError(secondResult, ERROR_CODE_NOT_FOUND);
        assertTraceIdPresent(secondResult);
    }

    // ===== CT-6: 审计落库 =====

    @Test
    @DisplayName("CT-6: 审计落库 (@Auditable DELETE → sys_operation_log 有记录)")
    void testDeleteProductAuditLog() throws Exception {
        String id = createProductAndTrack();

        MvcResult result = performDelete(API_PATH + "/" + id, mockUser("biz"));
        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);

        String traceId = parseResult(result).traceId();
        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_operation_log " +
                        "WHERE biz_type = 'biz_product' AND operation_type = 'DELETE' " +
                        "AND trace_id = ?",
                Integer.class, traceId);

        assertNotNull(auditCount, "审计日志查询不应返回 null");
        assertTrue(auditCount >= 1,
                "sys_operation_log 应至少有 1 条 DELETE 审计记录 (traceId=" + traceId + "), 实际: " + auditCount);

        jdbcTemplate.query("SELECT biz_id, module, operation_type, biz_type FROM sys_operation_log " +
                        "WHERE biz_type = 'biz_product' AND operation_type = 'DELETE' AND trace_id = ?",
                rs -> {
                    assertEquals(id, rs.getString("biz_id"), "审计记录 biz_id 应为删除的商品 ID");
                    assertEquals("sample", rs.getString("module"), "审计记录 module 应为 sample");
                    assertEquals("DELETE", rs.getString("operation_type"), "审计记录 operation_type 应为 DELETE");
                    assertEquals("biz_product", rs.getString("biz_type"), "审计记录 biz_type 应为 biz_product");
                }, traceId);
    }

    // ===== CT-7: traceId 透传 =====

    @Test
    @DisplayName("CT-7: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)")
    void testDeleteProductTraceIdPropagation() throws Exception {
        String id = createProductAndTrack();

        MvcResult result = performDelete(API_PATH + "/" + id, mockUser("biz"));

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
