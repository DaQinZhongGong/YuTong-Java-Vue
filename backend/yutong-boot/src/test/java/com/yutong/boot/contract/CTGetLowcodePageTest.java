package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
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
 * CT-getLowcodePage 契约测试 (operationId: getLowcodePage)。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-getLowcodePage 验收标准)
 * <p>契约来源: routes.yaml web.lowcode.pages (permission: lc:page:list)
 *    + LcPageController GET /api/v1/lowcode/pages/{id}
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功查询 (admin, 200, code=0, 返回 LcPageDetailVO + traceId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (LcPageController 未加 @RequiresPermission, @Disabled)</li>
 *   <li>CT-4 页面不存在 (404 SYS-404001)</li>
 *   <li>CT-5 traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)</li>
 *   <li>CT-6 详情含子表 (components/actions 数组字段存在)</li>
 * </ol>
 *
 * <p>数据隔离: 每个用例独立创建测试数据 (UUID 后缀 pageCode), @AfterEach 物理清理。
 */
@DisplayName("CT-getLowcodePage: GET /api/v1/lowcode/pages/{id} 契约测试")
class CTGetLowcodePageTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/lowcode/pages";
    private static final String ERROR_CODE_NOT_FOUND = "SYS-404001";

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
    private SaveLcPageRequest buildValidPageRequest() {
        SaveLcPageRequest req = new SaveLcPageRequest();
        req.setPageCode("CT-LC-PG-G-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        req.setPageName("CT详情测试页面");
        req.setPageType("FORM");
        req.setLayoutJson("{\"type\":\"form\"}");
        req.setLayoutSchemaVersion("1.0");
        return req;
    }

    /** 创建页面草稿并跟踪 ID。 */
    private String createPageAndTrack() throws Exception {
        SaveLcPageRequest request = buildValidPageRequest();
        MvcResult result = performPost(API_PATH, request, mockUser("admin"));
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertNotNull(data, "创建响应 data 不应为 null");
        String id = data.get("id").asText();
        createdPageIds.add(id);
        return id;
    }

    // ===== CT-1: 成功查询 =====

    @Test
    @DisplayName("CT-1: 成功查询页面详情 (admin 用户, 200, code=0, 返回 LcPageDetailVO + traceId)")
    void testGetLowcodePageSuccess() throws Exception {
        String id = createPageAndTrack();

        MvcResult result = performGet(API_PATH + "/" + id, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");
        assertEquals(id, data.get("id").asText(), "返回的 id 应与请求一致");
        assertNotNull(data.get("pageCode"), "pageCode 不应为 null");
        assertNotNull(data.get("pageName"), "pageName 不应为 null");
        assertNotNull(data.get("status"), "status 不应为 null");
        assertEquals("DRAFT", data.get("status").asText(), "新建页面状态应为 DRAFT");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testGetLowcodePageUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 (控制器未加 @RequiresPermission) =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (LcPageController 未加 @RequiresPermission, @Disabled 待补充注解)")
    @org.junit.jupiter.api.Disabled("LcPageController.detail 未加 @RequiresPermission(lc:page:detail), " +
            "权限码未在控制器层强制; 待补充注解后启用此用例")
    void testGetLowcodePageForbidden() {
        // 实现待补充: LcPageController 当前未加 @RequiresPermission 注解
    }

    // ===== CT-4: 页面不存在 =====

    @Test
    @DisplayName("CT-4: 页面不存在 (404 SYS-404001)")
    void testGetLowcodePageNotFound() throws Exception {
        String nonExistentId = "01NOTEXIST00000000000000CT";

        MvcResult result = performGet(API_PATH + "/" + nonExistentId, mockUser("admin"));

        assertEquals(404, result.getResponse().getStatus(), "页面不存在, HTTP 应为 404");
        assertError(result, ERROR_CODE_NOT_FOUND);
        assertTraceIdPresent(result);
    }

    // ===== CT-5: traceId 透传 =====

    @Test
    @DisplayName("CT-5: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)")
    void testGetLowcodePageTraceIdPropagation() throws Exception {
        String id = createPageAndTrack();

        MvcResult result = performGet(API_PATH + "/" + id, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());

        ResultNode node = parseResult(result);
        assertNotNull(node.traceId(), "响应体应包含 traceId");
        assertTrue(node.traceId().length() >= 16 && node.traceId().length() <= 64,
                "traceId 长度应在 16-64 之间");

        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertNotNull(headerTraceId, "响应头应包含 X-Trace-Id");
        assertEquals(node.traceId(), headerTraceId,
                "响应头 X-Trace-Id 应与响应体 traceId 一致");
    }

    // ===== CT-6: 详情含子表 =====

    @Test
    @DisplayName("CT-6: 详情含子表 (components/actions 数组字段存在)")
    void testGetLowcodePageDetailWithChildren() throws Exception {
        String id = createPageAndTrack();

        MvcResult result = performGet(API_PATH + "/" + id, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertNotNull(data.get("components"), "详情应包含 components 字段");
        assertTrue(data.get("components").isArray(), "components 应为数组");
        assertNotNull(data.get("actions"), "详情应包含 actions 字段");
        assertTrue(data.get("actions").isArray(), "actions 应为数组");
    }
}
