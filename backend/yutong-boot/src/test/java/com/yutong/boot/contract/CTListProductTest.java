package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
 * GA2-L186 CT-listProduct 契约测试 (operationId: listProducts)。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-listProducts 验收标准)
 * <p>策略来源: operation-policies.yaml listProducts → read profile (queryPage)
 * <p>契约来源: openapi.yaml GET /api/v1/products (x-permission: biz:product:list)
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功分页查询 (200, code=0, records/total/page/size + traceId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (approver 无 biz:product:list, 403 AUTH-403001)</li>
 *   <li>CT-4 参数校验 - pageSize 越界 (0 → 钳制为 20, 200 优雅降级)</li>
 *   <li>CT-5 空结果 (keyword 不匹配, total=0)</li>
 *   <li>CT-6 过滤条件 keyword (唯一 code 精确匹配, total=1)</li>
 *   <li>CT-7 过滤条件 status (status=DISABLED 只返回禁用的)</li>
 *   <li>CT-8 排序 (createdTime DESC 非递增)</li>
 *   <li>CT-9 数据范围 (viewer TENANT 可见本租户数据)</li>
 * </ol>
 *
 * <p>数据隔离: 每个用例独立创建测试数据 (UUID 后缀 productCode), @AfterEach 物理清理。
 */
@DisplayName("CT-listProduct: GET /api/v1/products 契约测试")
class CTListProductTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/products";
    private static final String ERROR_CODE_FORBIDDEN = "AUTH-403001";

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

    /** 构造合法 Product (唯一 code, 指定前缀)。 */
    private Product buildValidProduct(String codePrefix) {
        Product p = new Product();
        p.setProductCode(codePrefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        p.setProductName("CT列表测试商品");
        p.setUnit("个");
        p.setPrice(new BigDecimal("99.99"));
        p.setStatus("ENABLED");
        return p;
    }

    /** 创建商品并跟踪 ID, 返回商品 code。 */
    private String createProductAndTrack(String codePrefix) throws Exception {
        Product request = buildValidProduct(codePrefix);
        MvcResult result = performPost(API_PATH, request, mockUser("biz"));
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertNotNull(data, "创建响应 data 不应为 null");
        String id = data.get("id").asText();
        createdProductIds.add(id);
        return request.getProductCode();
    }

    /** 创建商品 (指定 status) 并跟踪 ID, 返回商品 code。 */
    private String createProductWithStatus(String codePrefix, String status) throws Exception {
        Product request = buildValidProduct(codePrefix);
        request.setStatus(status);
        MvcResult result = performPost(API_PATH, request, mockUser("biz"));
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        String id = data.get("id").asText();
        createdProductIds.add(id);
        return request.getProductCode();
    }

    // ===== CT-1: 成功分页查询 =====

    @Test
    @DisplayName("CT-1: 成功分页查询 (biz 用户, 200, code=0, records/total/page/size + traceId)")
    void testListProductSuccess() throws Exception {
        String code = createProductAndTrack("CT-L186-L");

        MvcResult result = performGet(API_PATH + "?pageNo=1&pageSize=10&keyword=" + code, mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        ResultNode node = parseResult(result);
        JsonNode data = node.data();
        assertNotNull(data, "data 不应为 null");
        assertNotNull(data.get("records"), "records 不应为 null");
        assertTrue(data.get("records").isArray(), "records 应为数组");
        assertTrue(data.get("total").asLong() >= 1, "total 应 >= 1 (至少包含刚创建的商品)");
        assertEquals(1, data.get("page").asInt(), "page 应为 1");
        assertEquals(10, data.get("size").asInt(), "size 应为 10");

        // 验证刚创建的商品在结果中
        ArrayNode records = (ArrayNode) data.get("records");
        boolean found = false;
        for (JsonNode rec : records) {
            if (code.equals(rec.get("productCode").asText())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "结果中应包含刚创建的商品 (code=" + code + ")");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testListProductUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (approver 无 biz:product:list, 403 AUTH-403001)")
    void testListProductForbidden() throws Exception {
        MvcResult result = performGet(API_PATH + "?pageNo=1&pageSize=10", mockUser("approver"));

        assertEquals(403, result.getResponse().getStatus(), "approver 无列表权限, HTTP 应为 403");
        assertError(result, ERROR_CODE_FORBIDDEN);
        assertTraceIdPresent(result);
    }

    // ===== CT-4: 参数校验 - pageSize 越界 =====

    @Test
    @DisplayName("CT-4: 参数校验 - pageSize=0 越界 (PageRequest 钳制为 20, 200 优雅降级)")
    void testListProductPageSizeOutOfRange() throws Exception {
        MvcResult result = performGet(API_PATH + "?pageNo=1&pageSize=0", mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus(), "pageSize 越界应优雅降级, HTTP 200");
        assertSuccess(result);
        ResultNode node = parseResult(result);
        // PageRequest: size < 1 → 钳制为 20
        assertEquals(20, node.data().get("size").asInt(), "pageSize=0 应钳制为默认值 20");
    }

    // ===== CT-5: 空结果 =====

    @Test
    @DisplayName("CT-5: 空结果 (keyword 不匹配, total=0, records 空数组)")
    void testListProductEmptyResult() throws Exception {
        String nonExistentKeyword = "NOSUCHCODE-CT-L186-" + UUID.randomUUID().toString().substring(0, 8);

        MvcResult result = performGet(API_PATH + "?pageNo=1&pageSize=10&keyword=" + nonExistentKeyword, mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        ResultNode node = parseResult(result);
        assertEquals(0, node.data().get("total").asLong(), "不匹配的 keyword 应返回 total=0");
        assertTrue(node.data().get("records").isArray(), "records 应为数组");
        assertEquals(0, node.data().get("records").size(), "records 应为空数组");
    }

    // ===== CT-6: 过滤条件 keyword =====

    @Test
    @DisplayName("CT-6: 过滤条件 keyword (唯一 code 精确匹配, total=1)")
    void testListProductFilterByKeyword() throws Exception {
        String code = createProductAndTrack("CT-L186-L-KW");

        MvcResult result = performGet(API_PATH + "?pageNo=1&pageSize=10&keyword=" + code, mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        ResultNode node = parseResult(result);
        assertEquals(1, node.data().get("total").asLong(), "唯一 code 应匹配 total=1");
        ArrayNode records = (ArrayNode) node.data().get("records");
        assertEquals(1, records.size(), "records 应只有 1 条");
        assertEquals(code, records.get(0).get("productCode").asText(), "返回的商品 code 应匹配");
    }

    // ===== CT-7: 过滤条件 status =====

    @Test
    @DisplayName("CT-7: 过滤条件 status (status=DISABLED 只返回禁用商品)")
    void testListProductFilterByStatus() throws Exception {
        // 创建一个 ENABLED 和一个 DISABLED 商品, 使用共同前缀
        String codeEnabled = createProductWithStatus("CT-L186-L-ST", "ENABLED");
        String codeDisabled = createProductWithStatus("CT-L186-L-ST", "DISABLED");

        // 查询 status=DISABLED + keyword 前缀 → 只返回 DISABLED 商品
        MvcResult result = performGet(
                API_PATH + "?pageNo=1&pageSize=10&keyword=CT-L186-L-ST&status=DISABLED",
                mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        ResultNode node = parseResult(result);
        ArrayNode records = (ArrayNode) node.data().get("records");
        assertTrue(records.size() >= 1, "应至少返回 1 条 DISABLED 商品");

        // 验证所有返回的记录 status 均为 DISABLED
        for (JsonNode rec : records) {
            assertEquals("DISABLED", rec.get("status").asText(), "过滤 status=DISABLED 后所有记录 status 应为 DISABLED");
        }

        // 验证 codeDisabled 在结果中, codeEnabled 不在
        boolean foundDisabled = false;
        boolean foundEnabled = false;
        for (JsonNode rec : records) {
            String recCode = rec.get("productCode").asText();
            if (recCode.equals(codeDisabled)) foundDisabled = true;
            if (recCode.equals(codeEnabled)) foundEnabled = true;
        }
        assertTrue(foundDisabled, "DISABLED 商品应在结果中");
        assertFalse(foundEnabled, "ENABLED 商品不应在 status=DISABLED 结果中");
    }

    // ===== CT-8: 排序 =====

    @Test
    @DisplayName("CT-8: 排序 (createdTime DESC, 非递增)")
    void testListProductSortOrder() throws Exception {
        // 创建 2 个商品使用共同前缀, 按创建顺序
        String code1 = createProductAndTrack("CT-L186-L-SO");
        Thread.sleep(10); // 确保时间差
        String code2 = createProductAndTrack("CT-L186-L-SO");

        MvcResult result = performGet(
                API_PATH + "?pageNo=1&pageSize=50&keyword=CT-L186-L-SO", mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        ArrayNode records = (ArrayNode) parseResult(result).data().get("records");
        assertTrue(records.size() >= 2, "应至少返回 2 条记录");

        // 验证 createdTime DESC (非递增: 前一条 >= 后一条)
        for (int i = 1; i < records.size(); i++) {
            String prev = records.get(i - 1).get("createdTime").asText();
            String curr = records.get(i).get("createdTime").asText();
            assertTrue(prev.compareTo(curr) >= 0,
                    "createdTime 应为 DESC 排序 (非递增), 但 " + prev + " < " + curr);
        }
    }

    // ===== CT-9: 数据范围 (viewer TENANT) =====

    @Test
    @DisplayName("CT-9: 数据范围 (viewer TENANT 可见本租户数据)")
    void testListProductViewerDataScope() throws Exception {
        // biz 创建商品
        String code = createProductAndTrack("CT-L186-L-DS");

        // viewer (TENANT 范围) 查询, 应能看到同租户数据
        MvcResult result = performGet(API_PATH + "?pageNo=1&pageSize=10&keyword=" + code, mockUser("viewer"));

        assertEquals(200, result.getResponse().getStatus(), "viewer 应能查询 (有 biz:product:list)");
        assertSuccess(result);
        ResultNode node = parseResult(result);
        assertTrue(node.data().get("total").asLong() >= 1, "viewer TENANT 范围应可见本租户数据");

        ArrayNode records = (ArrayNode) node.data().get("records");
        boolean found = false;
        for (JsonNode rec : records) {
            if (code.equals(rec.get("productCode").asText())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "viewer 应能看到 biz 创建的商品 (TENANT 范围)");
    }
}
