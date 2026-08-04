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
 * GA2-L186 CT-getProduct 契约测试 (operationId: getProduct)。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-getProduct 验收标准)
 * <p>策略来源: operation-policies.yaml getProduct → read profile (queryOne)
 * <p>契约来源: openapi.yaml GET /api/v1/products/{id} (x-permission: biz:product:detail, x-error-codes: [BIZ-404003])
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功查询 (biz 用户, 200, code=0, 返回 Product + traceId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (approver 无 biz:product:detail, 403 AUTH-403001)</li>
 *   <li>CT-4 商品不存在 (404 BIZ-404003)</li>
 *   <li>CT-5 已删除商品 (404 BIZ-404003, selectById 过滤逻辑删除)</li>
 *   <li>CT-6 字段完整性 (返回 productCode/productName/unit/price/status 全字段)</li>
 *   <li>CT-7 viewer 可查询 (TENANT 范围, 有 biz:product:detail)</li>
 *   <li>CT-8 traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)</li>
 * </ol>
 *
 * <p>数据隔离: 每个用例独立创建测试数据 (UUID 后缀 productCode), @AfterEach 物理清理。
 */
@DisplayName("CT-getProduct: GET /api/v1/products/{id} 契约测试")
class CTGetProductTest extends AbstractContractTest {

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
        p.setProductCode("CT-L186-G-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        p.setProductName("CT详情测试商品");
        p.setUnit("个");
        p.setPrice(new BigDecimal("99.99"));
        p.setStatus("ENABLED");
        return p;
    }

    /** 创建商品并跟踪 ID。返回创建的商品 ID。 */
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

    // ===== CT-1: 成功查询 =====

    @Test
    @DisplayName("CT-1: 成功查询商品详情 (biz 用户, 200, code=0, 返回 Product + traceId)")
    void testGetProductSuccess() throws Exception {
        String id = createProductAndTrack();

        MvcResult result = performGet(API_PATH + "/" + id, mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        ResultNode node = parseResult(result);
        JsonNode data = node.data();
        assertNotNull(data, "data 不应为 null");
        assertEquals(id, data.get("id").asText(), "返回 id 应与请求 id 一致");
        assertNotNull(data.get("productCode"), "productCode 不应为 null");
        assertNotNull(data.get("productName"), "productName 不应为 null");
        assertNotNull(data.get("status"), "status 不应为 null");
        assertEquals("ENABLED", data.get("status").asText(), "status 应为创建时的 ENABLED");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testGetProductUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (approver 无 biz:product:detail, 403 AUTH-403001)")
    void testGetProductForbidden() throws Exception {
        String id = createProductAndTrack();

        MvcResult result = performGet(API_PATH + "/" + id, mockUser("approver"));

        assertEquals(403, result.getResponse().getStatus(), "approver 无详情权限, HTTP 应为 403");
        assertError(result, ERROR_CODE_FORBIDDEN);
        assertTraceIdPresent(result);
    }

    // ===== CT-4: 商品不存在 =====

    @Test
    @DisplayName("CT-4: 商品不存在 (404 BIZ-404003)")
    void testGetProductNotFound() throws Exception {
        String nonExistentId = "01NOTEXIST00000000000000CT";

        MvcResult result = performGet(API_PATH + "/" + nonExistentId, mockUser("biz"));

        assertEquals(404, result.getResponse().getStatus(), "商品不存在, HTTP 应为 404");
        assertError(result, ERROR_CODE_NOT_FOUND);
        assertTraceIdPresent(result);
    }

    // ===== CT-5: 已删除商品 =====

    @Test
    @DisplayName("CT-5: 已删除商品 (selectById 过滤逻辑删除, 404 BIZ-404003)")
    void testGetProductDeleted() throws Exception {
        String id = createProductAndTrack();

        // 先删除 (逻辑删除)
        MvcResult deleteResult = performDelete(API_PATH + "/" + id, mockUser("biz"));
        assertEquals(200, deleteResult.getResponse().getStatus(), "删除应成功");
        assertSuccess(deleteResult);

        // 再 GET: selectById 过滤 deleted=true → 返回 null → 404 BIZ-404003
        MvcResult result = performGet(API_PATH + "/" + id, mockUser("biz"));

        assertEquals(404, result.getResponse().getStatus(), "已删除商品查询应返回 404");
        assertError(result, ERROR_CODE_NOT_FOUND);
        assertTraceIdPresent(result);
    }

    // ===== CT-6: 字段完整性 =====

    @Test
    @DisplayName("CT-6: 字段完整性 (返回 productCode/productName/unit/price/status 全字段)")
    void testGetProductFieldCompleteness() throws Exception {
        Product request = buildValidProduct();
        MvcResult createResult = performPost(API_PATH, request, mockUser("biz"));
        assertSuccess(createResult);
        String id = parseResult(createResult).data().get("id").asText();
        createdProductIds.add(id);

        MvcResult result = performGet(API_PATH + "/" + id, mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");

        // 验证全字段返回且值与创建时一致
        assertEquals(id, data.get("id").asText(), "id 应一致");
        assertEquals(request.getProductCode(), data.get("productCode").asText(), "productCode 应一致");
        assertEquals(request.getProductName(), data.get("productName").asText(), "productName 应一致");
        assertEquals(request.getUnit(), data.get("unit").asText(), "unit 应一致");
        assertEquals(0, new BigDecimal(data.get("price").asText()).compareTo(request.getPrice()),
                "price 应一致");
        assertEquals(request.getStatus(), data.get("status").asText(), "status 应一致");
        assertNotNull(data.get("version"), "version 不应为 null");
        assertNotNull(data.get("createdTime"), "createdTime 不应为 null");
        assertNotNull(data.get("updatedTime"), "updatedTime 不应为 null");
    }

    // ===== CT-7: viewer 可查询 (TENANT 范围) =====

    @Test
    @DisplayName("CT-7: viewer 可查询 (TENANT 范围, 有 biz:product:detail, 200)")
    void testGetProductByViewer() throws Exception {
        String id = createProductAndTrack();

        MvcResult result = performGet(API_PATH + "/" + id, mockUser("viewer"));

        assertEquals(200, result.getResponse().getStatus(), "viewer 有 biz:product:detail, HTTP 应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");
        assertEquals(id, data.get("id").asText(), "返回 id 应与请求 id 一致");
        assertNotNull(data.get("productCode"), "viewer 应能读取 productCode");
        assertNotNull(data.get("productName"), "viewer 应能读取 productName");
    }

    // ===== CT-8: traceId 透传 =====

    @Test
    @DisplayName("CT-8: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)")
    void testGetProductTraceIdPropagation() throws Exception {
        String id = createProductAndTrack();

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
