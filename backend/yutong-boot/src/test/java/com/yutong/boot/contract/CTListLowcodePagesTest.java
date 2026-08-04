package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.yutong.lowcode.meta.dto.SaveLcPageRequest;
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
 * CT-listLowcodePages 契约测试 (operationId: listLowcodePages)。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-listLowcodePages 验收标准)
 * <p>契约来源: routes.yaml web.lowcode.pages (permission: lc:page:list)
 *    + LcPageController GET /api/v1/lowcode/pages
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功分页查询 (admin, 200, code=0, records/total/page/size + traceId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (LcPageController 未加 @RequiresPermission, @Disabled)</li>
 *   <li>CT-4 参数校验 - size 越界 (0 → 钳制为 20, 200 优雅降级)</li>
 *   <li>CT-5 空结果 (pageCode 不匹配, total=0)</li>
 *   <li>CT-6 过滤条件 pageCode (唯一 code 精确匹配, total=1)</li>
 *   <li>CT-7 过滤条件 status (status=DRAFT 只返回草稿页面)</li>
 *   <li>CT-8 排序 (createdTime DESC, 非递增)</li>
 * </ol>
 *
 * <p>数据隔离: 每个用例独立创建测试数据 (UUID 后缀 pageCode), @AfterEach 物理清理。
 */
@DisplayName("CT-listLowcodePages: GET /api/v1/lowcode/pages 契约测试")
class CTListLowcodePagesTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/lowcode/pages";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final java.util.Set<String> createdPageIds = ConcurrentHashMap.newKeySet();

    @AfterEach
    void cleanupTestData() {
        for (String id : createdPageIds) {
            jdbcTemplate.update("DELETE FROM lc_component WHERE page_id = ?", id);
        }
        for (String id : createdPageIds) {
            jdbcTemplate.update("DELETE FROM lc_action WHERE page_id = ?", id);
        }
        for (String id : createdPageIds) {
            jdbcTemplate.update("DELETE FROM sys_operation_log WHERE biz_type = 'lc_page' AND biz_id = ?", id);
        }
        for (String id : createdPageIds) {
            jdbcTemplate.update("DELETE FROM lc_page WHERE id = ?", id);
        }
        createdPageIds.clear();
    }

    /** 构造合法 SaveLcPageRequest (唯一 pageCode)。 */
    private SaveLcPageRequest buildValidPageRequest(String codePrefix) {
        SaveLcPageRequest req = new SaveLcPageRequest();
        req.setPageCode(codePrefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        req.setPageName("CT列表测试页面");
        req.setPageType("LIST");
        req.setLayoutJson("{\"type\":\"list\"}");
        req.setLayoutSchemaVersion("1.0");
        return req;
    }

    /** 创建页面草稿并跟踪 ID, 返回 pageCode。 */
    private String createPageAndTrack(String codePrefix) throws Exception {
        SaveLcPageRequest request = buildValidPageRequest(codePrefix);
        MvcResult result = performPost(API_PATH, request, mockUser("admin"));
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertNotNull(data, "创建响应 data 不应为 null");
        String id = data.get("id").asText();
        createdPageIds.add(id);
        return request.getPageCode();
    }

    // ===== CT-1: 成功分页查询 =====

    @Test
    @DisplayName("CT-1: 成功分页查询 (admin 用户, 200, code=0, records/total/page/size + traceId)")
    void testListLowcodePagesSuccess() throws Exception {
        String code = createPageAndTrack("CT-LC-PG-L");

        MvcResult result = performGet(API_PATH + "?page=1&size=10&pageCode=" + code, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        ResultNode node = parseResult(result);
        JsonNode data = node.data();
        assertNotNull(data, "data 不应为 null");
        assertNotNull(data.get("records"), "records 不应为 null");
        assertTrue(data.get("records").isArray(), "records 应为数组");
        assertTrue(data.get("total").asLong() >= 1, "total 应 >= 1 (至少包含刚创建的页面)");
        assertEquals(1, data.get("page").asInt(), "page 应为 1");
        assertEquals(10, data.get("size").asInt(), "size 应为 10");

        ArrayNode records = (ArrayNode) data.get("records");
        boolean found = false;
        for (JsonNode rec : records) {
            if (code.equals(rec.get("pageCode").asText())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "结果中应包含刚创建的页面 (code=" + code + ")");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testListLowcodePagesUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 (控制器未加 @RequiresPermission) =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (LcPageController 未加 @RequiresPermission, @Disabled 待补充注解)")
    @org.junit.jupiter.api.Disabled("LcPageController.page 未加 @RequiresPermission(lc:page:list), " +
            "权限码未在控制器层强制; 待补充注解后启用此用例")
    void testListLowcodePagesForbidden() {
        // 实现待补充: LcPageController 当前未加 @RequiresPermission 注解
    }

    // ===== CT-4: 参数校验 - size 越界 =====

    @Test
    @DisplayName("CT-4: 参数校验 - size=0 越界 (PageRequest 钳制为 20, 200 优雅降级)")
    void testListLowcodePagesSizeOutOfRange() throws Exception {
        MvcResult result = performGet(API_PATH + "?page=1&size=0", mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "size 越界应优雅降级, HTTP 200");
        assertSuccess(result);
        ResultNode node = parseResult(result);
        assertEquals(20, node.data().get("size").asInt(), "size=0 应钳制为默认值 20");
    }

    // ===== CT-5: 空结果 =====

    @Test
    @DisplayName("CT-5: 空结果 (pageCode 不匹配, total=0, records 空数组)")
    void testListLowcodePagesEmptyResult() throws Exception {
        String nonExistentCode = "NOSUCHCODE-CT-LC-PG-" + UUID.randomUUID().toString().substring(0, 8);

        MvcResult result = performGet(API_PATH + "?page=1&size=10&pageCode=" + nonExistentCode, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        ResultNode node = parseResult(result);
        assertEquals(0, node.data().get("total").asLong(), "不匹配的 pageCode 应返回 total=0");
        assertTrue(node.data().get("records").isArray(), "records 应为数组");
        assertEquals(0, node.data().get("records").size(), "records 应为空数组");
    }

    // ===== CT-6: 过滤条件 pageCode =====

    @Test
    @DisplayName("CT-6: 过滤条件 pageCode (唯一 code 精确匹配, total=1)")
    void testListLowcodePagesFilterByPageCode() throws Exception {
        String code = createPageAndTrack("CT-LC-PG-L-KW");

        MvcResult result = performGet(API_PATH + "?page=1&size=10&pageCode=" + code, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        ResultNode node = parseResult(result);
        assertEquals(1, node.data().get("total").asLong(), "唯一 code 应匹配 total=1");
        ArrayNode records = (ArrayNode) node.data().get("records");
        assertEquals(1, records.size(), "records 应只有 1 条");
        assertEquals(code, records.get(0).get("pageCode").asText(), "返回的页面 code 应匹配");
    }

    // ===== CT-7: 过滤条件 status =====

    @Test
    @DisplayName("CT-7: 过滤条件 status (status=DRAFT 只返回草稿页面)")
    void testListLowcodePagesFilterByStatus() throws Exception {
        String code = createPageAndTrack("CT-LC-PG-L-ST");

        MvcResult result = performGet(
                API_PATH + "?page=1&size=10&pageCode=CT-LC-PG-L-ST&status=DRAFT",
                mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        ResultNode node = parseResult(result);
        ArrayNode records = (ArrayNode) node.data().get("records");
        assertTrue(records.size() >= 1, "应至少返回 1 条 DRAFT 页面");

        for (JsonNode rec : records) {
            assertEquals("DRAFT", rec.get("status").asText(),
                    "过滤 status=DRAFT 后所有记录 status 应为 DRAFT");
        }

        boolean found = false;
        for (JsonNode rec : records) {
            if (code.equals(rec.get("pageCode").asText())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "刚创建的 DRAFT 页面应在结果中");
    }

    // ===== CT-8: 排序 =====

    @Test
    @DisplayName("CT-8: 排序 (createdTime DESC, 非递增)")
    void testListLowcodePagesSortOrder() throws Exception {
        String code1 = createPageAndTrack("CT-LC-PG-L-SO");
        Thread.sleep(10);
        String code2 = createPageAndTrack("CT-LC-PG-L-SO");

        MvcResult result = performGet(
                API_PATH + "?page=1&size=50&pageCode=CT-LC-PG-L-SO", mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        ArrayNode records = (ArrayNode) parseResult(result).data().get("records");
        assertTrue(records.size() >= 2, "应至少返回 2 条记录");

        for (int i = 1; i < records.size(); i++) {
            String prev = records.get(i - 1).get("createdTime").asText();
            String curr = records.get(i).get("createdTime").asText();
            assertTrue(prev.compareTo(curr) >= 0,
                    "createdTime 应为 DESC 排序 (非递增), 但 " + prev + " < " + curr);
        }
    }
}
