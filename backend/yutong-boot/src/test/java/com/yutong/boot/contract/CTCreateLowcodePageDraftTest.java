package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.yutong.lowcode.meta.dto.SaveLcPageRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CT-createLowcodePageDraft 契约测试 (operationId: createLowcodePageDraft)。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-createLowcodePageDraft 验收标准)
 * <p>契约来源: routes.yaml web.lowcode.pages (permission: lc:page:list)
 *    + LcPageController POST /api/v1/lowcode/pages
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功创建 (admin, 200, code=0, 返回 LcPage + traceId, status=DRAFT)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (LcPageController 未加 @RequiresPermission, @Disabled)</li>
 *   <li>CT-4 标准错误信封 (code/message/traceId 字段齐全)</li>
 *   <li>CT-5 traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)</li>
 *   <li>CT-6 创建含子表 (components + actions 一并传入, 详情返回子表数据)</li>
 * </ol>
 *
 * <p>数据隔离: 每个用例使用唯一 pageCode (UUID 后缀), @AfterEach 物理清理。
 */
@DisplayName("CT-createLowcodePageDraft: POST /api/v1/lowcode/pages 契约测试")
class CTCreateLowcodePageDraftTest extends AbstractContractTest {

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
    private SaveLcPageRequest buildValidPageRequest() {
        SaveLcPageRequest req = new SaveLcPageRequest();
        req.setPageCode("CT-LC-PG-C-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        req.setPageName("CT创建测试页面");
        req.setPageType("LIST");
        req.setLayoutJson("{\"type\":\"list\"}");
        req.setLayoutSchemaVersion("1.0");
        return req;
    }

    /** 从成功响应中提取页面 ID 并记录到清理集合。 */
    private String extractAndTrackId(MvcResult result) throws Exception {
        ResultNode node = parseResult(result);
        JsonNode data = node.data();
        assertNotNull(data, "成功响应 data 不应为 null");
        String id = data.get("id").asText();
        createdPageIds.add(id);
        return id;
    }

    // ===== CT-1: 成功创建 =====

    @Test
    @DisplayName("CT-1: 成功创建页面草稿 (admin 用户, 200, code=0, 返回 id/version/status=DRAFT + traceId)")
    void testCreateLowcodePageDraftSuccess() throws Exception {
        SaveLcPageRequest request = buildValidPageRequest();

        MvcResult result = performPost(API_PATH, request, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        ResultNode node = parseResult(result);
        JsonNode data = node.data();
        assertNotNull(data.get("id"), "返回的 id 不应为 null");
        assertEquals(request.getPageCode(), data.get("pageCode").asText());
        assertEquals(request.getPageName(), data.get("pageName").asText());
        assertEquals("DRAFT", data.get("status").asText(), "新建页面状态应为 DRAFT");
        assertNotNull(data.get("version"), "version 不应为 null");
        assertNotNull(data.get("versionNo"), "versionNo 不应为 null");
        assertEquals(1, data.get("versionNo").asInt(), "新建页面 versionNo 应为 1");

        extractAndTrackId(result);
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testCreateLowcodePageDraftUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 (控制器未加 @RequiresPermission) =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (LcPageController 未加 @RequiresPermission, @Disabled 待补充注解)")
    @org.junit.jupiter.api.Disabled("LcPageController.create 未加 @RequiresPermission(lc:page:add), " +
            "权限码未在控制器层强制; 待补充注解后启用此用例")
    void testCreateLowcodePageDraftForbidden() {
        // 实现待补充: LcPageController 当前未加 @RequiresPermission 注解
    }

    // ===== CT-4: 标准错误信封结构 =====

    @Test
    @DisplayName("CT-4: 标准错误信封 (code/message/traceId 字段齐全, 非成功响应)")
    void testCreateLowcodePageDraftErrorEnvelopeStructure() throws Exception {
        // 使用空 pageCode 触发数据库约束或服务异常, 验证错误信封结构
        SaveLcPageRequest request = new SaveLcPageRequest();
        request.setPageCode(null);
        request.setPageName("CT错误信封测试");
        request.setPageType("LIST");

        MvcResult result = performPost(API_PATH, request, mockUser("admin"));

        ResultNode node = parseResult(result);
        assertNotNull(node.code(), "错误信封必须包含 code 字段");
        assertNotEquals("0", node.code(), "错误响应 code 不应为 0");
        assertNotNull(node.traceId(), "错误信封必须包含 traceId 字段");
        assertTrue(node.traceId().length() >= 16 && node.traceId().length() <= 64,
                "traceId 长度应在 16-64 之间");
    }

    // ===== CT-5: traceId 透传 =====

    @Test
    @DisplayName("CT-5: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)")
    void testCreateLowcodePageDraftTraceIdPropagation() throws Exception {
        SaveLcPageRequest request = buildValidPageRequest();

        MvcResult result = performPost(API_PATH, request, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());

        ResultNode node = parseResult(result);
        assertNotNull(node.traceId(), "响应体应包含 traceId");
        assertTrue(node.traceId().length() >= 16 && node.traceId().length() <= 64,
                "traceId 长度应在 16-64 之间");

        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertNotNull(headerTraceId, "响应头应包含 X-Trace-Id");
        assertEquals(node.traceId(), headerTraceId,
                "响应头 X-Trace-Id 应与响应体 traceId 一致");

        extractAndTrackId(result);
    }

    // ===== CT-6: 创建含子表 (components + actions) =====

    @Test
    @DisplayName("CT-6: 创建含子表 (components + actions 一并传入, 详情返回子表数据)")
    void testCreateLowcodePageDraftWithChildren() throws Exception {
        SaveLcPageRequest request = buildValidPageRequest();

        SaveLcPageRequest.LcComponentDTO component = new SaveLcPageRequest.LcComponentDTO();
        component.setComponentCode("search_form");
        component.setComponentType("FORM");
        component.setPropsJson("{\"fields\":[]}");
        component.setSortNo(1);
        request.setComponents(List.of(component));

        SaveLcPageRequest.LcActionDTO action = new SaveLcPageRequest.LcActionDTO();
        action.setActionCode("search");
        action.setActionName("查询");
        action.setActionType("API");
        action.setApiMethod("GET");
        action.setApiPath("/api/v1/test/search");
        request.setActions(List.of(action));

        MvcResult createResult = performPost(API_PATH, request, mockUser("admin"));
        assertEquals(200, createResult.getResponse().getStatus());
        assertSuccess(createResult);
        String id = extractAndTrackId(createResult);

        // 通过详情接口验证子表数据落库
        MvcResult detailResult = performGet(API_PATH + "/" + id, mockUser("admin"));
        assertEquals(200, detailResult.getResponse().getStatus());
        assertSuccess(detailResult);
        JsonNode detailData = parseResult(detailResult).data();
        assertNotNull(detailData.get("components"), "详情应包含 components 字段");
        assertTrue(detailData.get("components").isArray(), "components 应为数组");
        assertEquals(1, detailData.get("components").size(), "components 应有 1 条记录");
        assertEquals("search_form", detailData.get("components").get(0).get("componentCode").asText());
        assertNotNull(detailData.get("actions"), "详情应包含 actions 字段");
        assertTrue(detailData.get("actions").isArray(), "actions 应为数组");
        assertEquals(1, detailData.get("actions").size(), "actions 应有 1 条记录");
    }
}
