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

import static org.junit.jupiter.api.Assertions.*;

/**
 * GA2-L186 CT-createProduct 契约测试。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-createProduct 验收标准)
 * <p>策略来源: operation-policies.yaml createProduct → uniqueCreate profile (database-unique-constraint)
 * <p>契约来源: openapi.yaml POST /api/v1/products (x-permission: biz:product:add, x-error-codes: [BIZ-409006])
 *
 * <p>覆盖 58 号文档第 13 行通用覆盖矩阵 9 类场景:
 * <ol>
 *   <li>CT-1 成功创建 (200, code=0, 返回 Product + traceId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (viewer 无 biz:product:add, 403 AUTH-403001)</li>
 *   <li>CT-4 参数校验失败 (productName 空/status 非法/productCode 缺失, 400)</li>
 *   <li>CT-5 重复编码 (409 BIZ-409006)</li>
 *   <li>CT-6 标准错误信封 (code/message/traceId 字段齐全)</li>
 *   <li>CT-7 traceId 透传 (响应头 + 响应体)</li>
 *   <li>CT-8 幂等+并发 (重复提交同 code, 第二次 409)</li>
 *   <li>CT-9 审计落库 (sys_operation_log 有 CREATE 记录)</li>
 * </ol>
 *
 * <p>数据隔离: 每个用例使用唯一 productCode (UUID 后缀), @AfterEach 清理创建的数据。
 * 不使用 @Transactional 以便 CT-9 审计验证能查询 sys_operation_log。
 */
@DisplayName("CT-createProduct: POST /api/v1/products 契约测试")
class CTCreateProductTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/products";
    private static final String ERROR_CODE_FORBIDDEN = "AUTH-403001";
    private static final String ERROR_CODE_DUPLICATE = "BIZ-409006";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 本测试创建的商品 ID 集合, @AfterEach 清理。 */
    private final java.util.Set<String> createdProductIds = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @AfterEach
    void cleanupTestData() {
        // 清理本测试产生的审计日志 (按 biz_id 精确清理, sys_operation_log 无 detail_json 列, 实际列为 before_json/after_json)
        for (String id : createdProductIds) {
            jdbcTemplate.update("DELETE FROM sys_operation_log WHERE biz_type = 'biz_product' AND biz_id = ?", id);
        }
        // 清理本测试创建的商品数据 (物理删除, 避免逻辑删除残留影响唯一索引)
        for (String id : createdProductIds) {
            jdbcTemplate.update("DELETE FROM biz_product WHERE id = ?", id);
        }
        createdProductIds.clear();
    }

    /** 构造合法 Product 请求体 (唯一 code)。 */
    private Product buildValidProduct() {
        Product p = new Product();
        p.setProductCode("CT-L186-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        p.setProductName("CT测试商品");
        p.setUnit("个");
        p.setPrice(new BigDecimal("99.99"));
        p.setStatus("ENABLED");
        return p;
    }

    /** 从成功响应中提取商品 ID 并记录到清理集合。 */
    private String extractAndTrackProductId(MvcResult result) throws Exception {
        ResultNode node = parseResult(result);
        JsonNode data = node.data();
        assertNotNull(data, "成功响应 data 不应为 null");
        String id = data.get("id").asText();
        createdProductIds.add(id);
        return id;
    }

    // ===== CT-1: 成功创建 =====

    @Test
    @DisplayName("CT-1: 成功创建商品 (biz 用户, 200, code=0, 返回 id/version/traceId)")
    void testCreateProductSuccess() throws Exception {
        Product request = buildValidProduct();

        MvcResult result = performPost(API_PATH, request, mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        // 验证返回的 Product 数据
        ResultNode node = parseResult(result);
        JsonNode data = node.data();
        assertNotNull(data.get("id"), "返回的 id 不应为 null");
        assertEquals(request.getProductCode(), data.get("productCode").asText());
        assertEquals(request.getProductName(), data.get("productName").asText());
        assertEquals("ENABLED", data.get("status").asText());
        assertNotNull(data.get("version"), "version 不应为 null");
        assertNotNull(data.get("updatedTime"), "updatedTime 不应为 null");

        extractAndTrackProductId(result);
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testCreateProductUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
        // 真实 AuthAdapter 接入后, 此用例应验证:
        // 1. 不带 Authorization 头 → 401 SYS-401001/AUTH-401002
        // 2. 标准错误信封结构
    }

    // ===== CT-3: 权限拒绝 =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (viewer 无 biz:product:add, 403 AUTH-403001)")
    void testCreateProductForbidden() throws Exception {
        Product request = buildValidProduct();

        MvcResult result = performPost(API_PATH, request, mockUser("viewer"));

        assertEquals(403, result.getResponse().getStatus(), "viewer 无写权限, HTTP 应为 403");
        assertError(result, ERROR_CODE_FORBIDDEN);
        assertTraceIdPresent(result);
    }

    // ===== CT-4: 参数校验失败 =====

    @Test
    @DisplayName("CT-4a: 参数校验失败 - productName 为空 (400)")
    void testCreateProductValidationBlankName() throws Exception {
        Product request = buildValidProduct();
        request.setProductName(""); // @NotBlank 校验

        MvcResult result = performPost(API_PATH, request, mockUser("biz"));

        assertEquals(400, result.getResponse().getStatus(), "productName 为空, HTTP 应为 400");
        ResultNode node = parseResult(result);
        assertNotEquals("0", node.code(), "校验失败不应返回 code=0");
        assertTraceIdPresent(result);
    }

    @Test
    @DisplayName("CT-4b: 参数校验失败 - status 非法值 (400)")
    void testCreateProductValidationInvalidStatus() throws Exception {
        Product request = buildValidProduct();
        request.setStatus("INVALID"); // @Pattern(ENABLED|DISABLED) 校验

        MvcResult result = performPost(API_PATH, request, mockUser("biz"));

        assertEquals(400, result.getResponse().getStatus(), "status 非法, HTTP 应为 400");
        ResultNode node = parseResult(result);
        assertNotEquals("0", node.code(), "校验失败不应返回 code=0");
        assertTraceIdPresent(result);
    }

    @Test
    @DisplayName("CT-4c: 参数校验失败 - productCode 缺失 (400)")
    void testCreateProductValidationMissingCode() throws Exception {
        Product request = buildValidProduct();
        request.setProductCode(null); // @NotBlank 校验

        MvcResult result = performPost(API_PATH, request, mockUser("biz"));

        assertEquals(400, result.getResponse().getStatus(), "productCode 缺失, HTTP 应为 400");
        ResultNode node = parseResult(result);
        assertNotEquals("0", node.code(), "校验失败不应返回 code=0");
        assertTraceIdPresent(result);
    }

    // ===== CT-5: 重复编码 =====

    @Test
    @DisplayName("CT-5: 重复编码 (同租户同 code, 409 BIZ-409006)")
    void testCreateProductDuplicateCode() throws Exception {
        Product request = buildValidProduct();

        // 第一次创建: 成功
        MvcResult firstResult = performPost(API_PATH, request, mockUser("biz"));
        assertEquals(200, firstResult.getResponse().getStatus());
        assertSuccess(firstResult);
        extractAndTrackProductId(firstResult);

        // 第二次创建同 code: 应失败 409 BIZ-409006
        MvcResult secondResult = performPost(API_PATH, request, mockUser("biz"));
        assertEquals(409, secondResult.getResponse().getStatus(), "重复编码 HTTP 应为 409");
        assertError(secondResult, ERROR_CODE_DUPLICATE);
        assertTraceIdPresent(secondResult);
    }

    // ===== CT-6: 标准错误信封结构 =====

    @Test
    @DisplayName("CT-6: 标准错误信封 (code/message/traceId 字段齐全, 非成功响应)")
    void testCreateProductErrorEnvelopeStructure() throws Exception {
        Product request = buildValidProduct();
        request.setProductName(""); // 触发 400

        MvcResult result = performPost(API_PATH, request, mockUser("biz"));

        ResultNode node = parseResult(result);
        // 标准错误信封必须包含: code (非 0), message, traceId
        assertNotNull(node.code(), "错误信封必须包含 code 字段");
        assertNotEquals("0", node.code(), "错误响应 code 不应为 0");
        assertNotNull(node.message(), "错误信封必须包含 message 字段");
        assertNotNull(node.traceId(), "错误信封必须包含 traceId 字段");
        assertTrue(node.traceId().length() >= 16 && node.traceId().length() <= 64,
                "traceId 长度应在 16-64 之间");
    }

    // ===== CT-7: traceId 透传 =====

    @Test
    @DisplayName("CT-7: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)")
    void testCreateProductTraceIdPropagation() throws Exception {
        Product request = buildValidProduct();

        MvcResult result = performPost(API_PATH, request, mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus());

        // 验证响应体 traceId
        ResultNode node = parseResult(result);
        assertNotNull(node.traceId(), "响应体应包含 traceId");
        assertTrue(node.traceId().length() >= 16 && node.traceId().length() <= 64,
                "traceId 长度应在 16-64 之间");

        // 验证响应头 X-Trace-Id (TraceAuthFilter 设置)
        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertNotNull(headerTraceId, "响应头应包含 X-Trace-Id");
        assertEquals(node.traceId(), headerTraceId,
                "响应头 X-Trace-Id 应与响应体 traceId 一致");

        extractAndTrackProductId(result);
    }

    // ===== CT-8: 幂等 (重复提交同 code 触发唯一约束) =====

    @Test
    @DisplayName("CT-8: 幂等-重复提交同 code (uniqueCreate 策略, 第二次 409 BIZ-409006)")
    void testCreateProductIdempotency() throws Exception {
        Product request = buildValidProduct();

        // 第一次: 成功
        MvcResult first = performPost(API_PATH, request, mockUser("biz"));
        assertEquals(200, first.getResponse().getStatus());
        assertSuccess(first);
        String firstId = extractAndTrackProductId(first);

        // 第二次同 code: 幂等校验 → 409 (uniqueCreate 策略: database-unique-constraint)
        MvcResult second = performPost(API_PATH, request, mockUser("biz"));
        assertEquals(409, second.getResponse().getStatus());
        assertError(second, ERROR_CODE_DUPLICATE);

        // 验证不会产生重复数据: 查询同 code 只有一条
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM biz_product WHERE product_code = ? AND deleted = false",
                Integer.class, request.getProductCode());
        assertNotNull(count);
        assertEquals(1, count, "幂等校验后同 code 应只有 1 条记录");
        assertEquals(firstId, jdbcTemplate.queryForObject(
                "SELECT id FROM biz_product WHERE product_code = ? AND deleted = false",
                String.class, request.getProductCode()), "应保留第一次创建的记录 id");
    }

    // ===== CT-9: 审计落库 =====

    @Test
    @DisplayName("CT-9: 审计落库 (@Auditable CREATE → sys_operation_log 有记录)")
    void testCreateProductAuditLog() throws Exception {
        Product request = buildValidProduct();
        // 使用特殊 code 前缀便于审计日志清理
        request.setProductCode("CT-L186-AUDIT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());

        MvcResult result = performPost(API_PATH, request, mockUser("biz"));
        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        String productId = extractAndTrackProductId(result);

        // 等待 AOP 切面写入审计日志 (AuditableAspect @AfterReturning 同步写入)
        // 查询 sys_operation_log 表验证审计记录
        String traceId = parseResult(result).traceId();
        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_operation_log " +
                        "WHERE biz_type = 'biz_product' AND operation_type = 'CREATE' " +
                        "AND trace_id = ?",
                Integer.class, traceId);

        assertNotNull(auditCount, "审计日志查询不应返回 null");
        assertTrue(auditCount >= 1,
                "sys_operation_log 应至少有 1 条 CREATE 审计记录 (traceId=" + traceId + "), 实际: " + auditCount);

        // 验证审计记录的关键字段
        jdbcTemplate.query("SELECT biz_id, module, operation_type, biz_type FROM sys_operation_log " +
                        "WHERE biz_type = 'biz_product' AND operation_type = 'CREATE' AND trace_id = ?",
                rs -> {
                    String bizId = rs.getString("biz_id");
                    String module = rs.getString("module");
                    String opType = rs.getString("operation_type");
                    String bizType = rs.getString("biz_type");
                    assertEquals(productId, bizId, "审计记录 biz_id 应为创建的商品 ID");
                    assertEquals("sample", module, "审计记录 module 应为 sample");
                    assertEquals("CREATE", opType, "审计记录 operation_type 应为 CREATE");
                    assertEquals("biz_product", bizType, "审计记录 biz_type 应为 biz_product");
                }, traceId);
    }
}
