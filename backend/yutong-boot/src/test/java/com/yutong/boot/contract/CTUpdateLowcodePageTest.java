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
 * CT-updateLowcodePage 契约测试 (operationId: updateLowcodePage)。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-updateLowcodePage 验收标准)
 * <p>契约来源: routes.yaml web.lowcode.pages (permission: lc:page:list)
 *    + LcPageController PUT /api/v1/lowcode/pages/{id}
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功更新 (admin, 200, code=0, pageName 已变更 + traceId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (LcPageController 未加 @RequiresPermission, @Disabled)</li>
 *   <li>CT-4 页面不存在 (404 SYS-404001)</li>
 *   <li>CT-5 版本号不匹配 (409 SYS-409004, 乐观锁校验)</li>
 *   <li>CT-6 更新非 DRAFT 页面 (409 SYS-409004, 状态机校验)</li>
 * </ol>
 *
 * <p>数据隔离: 每个用例独立创建测试数据 (UUID 后缀 pageCode), @AfterEach 物理清理。
 */
@DisplayName("CT-updateLowcodePage: PUT /api/v1/lowcode/pages/{id} 契约测试")
class CTUpdateLowcodePageTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/lowcode/pages";
    private static final String ERROR_CODE_NOT_FOUND = "SYS-404001";
    private static final String ERROR_CODE_CONFLICT = "SYS-409004";

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
        req.setPageCode("CT-LC-PG-U-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        req.setPageName("CT更新测试页面");
        req.setPageType("LIST");
        req.setLayoutJson("{\"type\":\"list\"}");
        req.setLayoutSchemaVersion("1.0");
        return req;
    }

    /** 创建页面草稿并跟踪 ID, 返回 id 和 version。 */
    private String[] createPageAndTrack() throws Exception {
        SaveLcPageRequest request = buildValidPageRequest();
        MvcResult result = performPost(API_PATH, request, mockUser("admin"));
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertNotNull(data, "创建响应 data 不应为 null");
        String id = data.get("id").asText();
        int version = data.get("version").asInt();
        createdPageIds.add(id);
        return new String[]{id, String.valueOf(version)};
    }

    // ===== CT-1: 成功更新 =====

    @Test
    @DisplayName("CT-1: 成功更新页面 (admin 用户, 200, code=0, pageName 已变更 + traceId)")
    void testUpdateLowcodePageSuccess() throws Exception {
        String[] idAndVersion = createPageAndTrack();
        String id = idAndVersion[0];
        int version = Integer.parseInt(idAndVersion[1]);

        SaveLcPageRequest updateReq = buildValidPageRequest();
        updateReq.setId(id);
        updateReq.setVersion(version);
        updateReq.setPageName("CT更新后页面名");

        MvcResult result = performPut(API_PATH + "/" + id, updateReq, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertEquals(id, data.get("id").asText(), "返回的 id 应与请求一致");
        assertEquals("CT更新后页面名", data.get("pageName").asText(), "pageName 应已更新");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testUpdateLowcodePageUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 (控制器未加 @RequiresPermission) =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (LcPageController 未加 @RequiresPermission, @Disabled 待补充注解)")
    @org.junit.jupiter.api.Disabled("LcPageController.update 未加 @RequiresPermission(lc:page:edit), " +
            "权限码未在控制器层强制; 待补充注解后启用此用例")
    void testUpdateLowcodePageForbidden() {
        // 实现待补充: LcPageController 当前未加 @RequiresPermission 注解
    }

    // ===== CT-4: 页面不存在 =====

    @Test
    @DisplayName("CT-4: 页面不存在 (404 SYS-404001)")
    void testUpdateLowcodePageNotFound() throws Exception {
        String nonExistentId = "01NOTEXIST00000000000000CT";
        SaveLcPageRequest updateReq = buildValidPageRequest();
        updateReq.setId(nonExistentId);
        updateReq.setVersion(0);

        MvcResult result = performPut(API_PATH + "/" + nonExistentId, updateReq, mockUser("admin"));

        assertEquals(404, result.getResponse().getStatus(), "页面不存在, HTTP 应为 404");
        assertError(result, ERROR_CODE_NOT_FOUND);
        assertTraceIdPresent(result);
    }

    // ===== CT-5: 版本号不匹配 =====

    @Test
    @DisplayName("CT-5: 版本号不匹配 (409 SYS-409004, 乐观锁校验)")
    void testUpdateLowcodePageVersionMismatch() throws Exception {
        String[] idAndVersion = createPageAndTrack();
        String id = idAndVersion[0];

        SaveLcPageRequest updateReq = buildValidPageRequest();
        updateReq.setId(id);
        updateReq.setVersion(999); // 错误版本号

        MvcResult result = performPut(API_PATH + "/" + id, updateReq, mockUser("admin"));

        assertEquals(409, result.getResponse().getStatus(), "版本号不匹配, HTTP 应为 409");
        assertError(result, ERROR_CODE_CONFLICT);
        assertTraceIdPresent(result);
    }

    // ===== CT-6: 更新非 DRAFT 页面 =====

    @Test
    @DisplayName("CT-6: 更新非 DRAFT 页面 (发布后更新, 409 SYS-409004, 状态机校验)")
    void testUpdateLowcodePageNonDraftStatus() throws Exception {
        String[] idAndVersion = createPageAndTrack();
        String id = idAndVersion[0];
        int version = Integer.parseInt(idAndVersion[1]);

        // 先发布页面: DRAFT → PUBLISHED
        MvcResult publishResult = performPost(
                API_PATH + "/" + id + "/publish?version=" + version, null, mockUser("admin"));
        assertEquals(200, publishResult.getResponse().getStatus(), "发布应成功");
        int publishedVersion = parseResult(publishResult).data().get("version").asInt();

        // 尝试更新已发布的页面: 应失败 (仅 DRAFT 可编辑)
        SaveLcPageRequest updateReq = buildValidPageRequest();
        updateReq.setId(id);
        updateReq.setVersion(publishedVersion);
        updateReq.setPageName("CT更新已发布页面");

        MvcResult result = performPut(API_PATH + "/" + id, updateReq, mockUser("admin"));

        assertEquals(409, result.getResponse().getStatus(), "更新非 DRAFT 页面, HTTP 应为 409");
        assertError(result, ERROR_CODE_CONFLICT);
        assertTraceIdPresent(result);
    }
}
