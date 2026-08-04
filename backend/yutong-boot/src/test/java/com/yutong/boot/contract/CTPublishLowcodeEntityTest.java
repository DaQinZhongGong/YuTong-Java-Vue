package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.yutong.lowcode.meta.dto.SaveLcEntityRequest;
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
 * CT-publishLowcodeEntity 契约测试 (operationId: publishLowcodeEntity)。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-publishLowcodeEntity 验收标准)
 * <p>契约来源: routes.yaml web.lowcode.entities (permission: lc:entity:list)
 *    + LcEntityController POST /api/v1/lowcode/entities/{id}/publish
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功发布 (admin, DRAFT→PUBLISHED, 200, code=0, status=PUBLISHED + traceId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (LcEntityController 未加 @RequiresPermission, @Disabled)</li>
 *   <li>CT-4 实体不存在 (404 SYS-404001)</li>
 *   <li>CT-5 版本号不匹配 (409 SYS-409004, 乐观锁校验)</li>
 *   <li>CT-6 幂等发布 (已 PUBLISHED 再发布, 200 直接返回当前实体)</li>
 *   <li>CT-7 禁用后发布 (DISABLED → PUBLISH 不允许, 409 SYS-409004)</li>
 * </ol>
 *
 * <p>数据隔离: 每个用例独立创建测试数据 (UUID 后缀 entityCode), @AfterEach 物理清理。
 */
@DisplayName("CT-publishLowcodeEntity: POST /api/v1/lowcode/entities/{id}/publish 契约测试")
class CTPublishLowcodeEntityTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/lowcode/entities";
    private static final String ERROR_CODE_NOT_FOUND = "SYS-404001";
    private static final String ERROR_CODE_CONFLICT = "SYS-409004";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final java.util.Set<String> createdEntityIds = ConcurrentHashMap.newKeySet();

    @AfterEach
    void cleanupTestData() {
        for (String id : createdEntityIds) {
            jdbcTemplate.update("DELETE FROM lc_field WHERE entity_id = ?", id);
        }
        for (String id : createdEntityIds) {
            jdbcTemplate.update("DELETE FROM lc_relation WHERE source_entity_id = ?", id);
        }
        for (String id : createdEntityIds) {
            jdbcTemplate.update("DELETE FROM sys_operation_log WHERE biz_type = 'lc_entity' AND biz_id = ?", id);
        }
        for (String id : createdEntityIds) {
            jdbcTemplate.update("DELETE FROM lc_entity WHERE id = ?", id);
        }
        createdEntityIds.clear();
    }

    /** 构造合法 SaveLcEntityRequest (唯一 entityCode)。 */
    private SaveLcEntityRequest buildValidEntityRequest() {
        SaveLcEntityRequest req = new SaveLcEntityRequest();
        req.setEntityCode("CT-LC-ENT-P-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        req.setEntityName("CT发布测试实体");
        req.setTableName("ct_test_" + UUID.randomUUID().toString().substring(0, 8).toLowerCase());
        req.setModuleCode("ct_test");
        return req;
    }

    /** 创建实体草稿并跟踪 ID, 返回 id 和 version。 */
    private String[] createEntityAndTrack() throws Exception {
        SaveLcEntityRequest request = buildValidEntityRequest();
        MvcResult result = performPost(API_PATH, request, mockUser("admin"));
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertNotNull(data, "创建响应 data 不应为 null");
        String id = data.get("id").asText();
        int version = data.get("version").asInt();
        createdEntityIds.add(id);
        return new String[]{id, String.valueOf(version)};
    }

    // ===== CT-1: 成功发布 =====

    @Test
    @DisplayName("CT-1: 成功发布实体 (admin 用户, DRAFT→PUBLISHED, 200, code=0, status=PUBLISHED + traceId)")
    void testPublishLowcodeEntitySuccess() throws Exception {
        String[] idAndVersion = createEntityAndTrack();
        String id = idAndVersion[0];
        int version = Integer.parseInt(idAndVersion[1]);

        MvcResult result = performPost(
                API_PATH + "/" + id + "/publish?version=" + version, null, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertEquals(id, data.get("id").asText(), "返回的 id 应与请求一致");
        assertEquals("PUBLISHED", data.get("status").asText(), "发布后 status 应为 PUBLISHED");
        assertNotNull(data.get("configHash"), "发布后 configHash 不应为 null");
        assertTrue(data.get("versionNo").asInt() >= 2, "发布后 versionNo 应自增 (>= 2)");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testPublishLowcodeEntityUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 (控制器未加 @RequiresPermission) =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (LcEntityController 未加 @RequiresPermission, @Disabled 待补充注解)")
    @org.junit.jupiter.api.Disabled("LcEntityController.publish 未加 @RequiresPermission(lc:entity:publish), " +
            "权限码未在控制器层强制; 待补充注解后启用此用例")
    void testPublishLowcodeEntityForbidden() {
        // 实现待补充: LcEntityController 当前未加 @RequiresPermission 注解
    }

    // ===== CT-4: 实体不存在 =====

    @Test
    @DisplayName("CT-4: 实体不存在 (404 SYS-404001)")
    void testPublishLowcodeEntityNotFound() throws Exception {
        String nonExistentId = "01NOTEXIST00000000000000CT";

        MvcResult result = performPost(
                API_PATH + "/" + nonExistentId + "/publish?version=0", null, mockUser("admin"));

        assertEquals(404, result.getResponse().getStatus(), "实体不存在, HTTP 应为 404");
        assertError(result, ERROR_CODE_NOT_FOUND);
        assertTraceIdPresent(result);
    }

    // ===== CT-5: 版本号不匹配 =====

    @Test
    @DisplayName("CT-5: 版本号不匹配 (409 SYS-409004, 乐观锁校验)")
    void testPublishLowcodeEntityVersionMismatch() throws Exception {
        String[] idAndVersion = createEntityAndTrack();
        String id = idAndVersion[0];

        MvcResult result = performPost(
                API_PATH + "/" + id + "/publish?version=999", null, mockUser("admin"));

        assertEquals(409, result.getResponse().getStatus(), "版本号不匹配, HTTP 应为 409");
        assertError(result, ERROR_CODE_CONFLICT);
        assertTraceIdPresent(result);
    }

    // ===== CT-6: 幂等发布 (已 PUBLISHED 再发布) =====

    @Test
    @DisplayName("CT-6: 幂等发布 (已 PUBLISHED 再发布, 200 直接返回当前实体)")
    void testPublishLowcodeEntityIdempotent() throws Exception {
        String[] idAndVersion = createEntityAndTrack();
        String id = idAndVersion[0];
        int version = Integer.parseInt(idAndVersion[1]);

        // 第一次发布: DRAFT → PUBLISHED
        MvcResult firstPublish = performPost(
                API_PATH + "/" + id + "/publish?version=" + version, null, mockUser("admin"));
        assertEquals(200, firstPublish.getResponse().getStatus(), "第一次发布应成功");
        assertSuccess(firstPublish);
        int publishedVersion = parseResult(firstPublish).data().get("version").asInt();

        // 第二次发布: 已 PUBLISHED, 幂等返回
        MvcResult secondPublish = performPost(
                API_PATH + "/" + id + "/publish?version=" + publishedVersion, null, mockUser("admin"));
        assertEquals(200, secondPublish.getResponse().getStatus(), "幂等发布应返回 200");
        assertSuccess(secondPublish);
        assertTraceIdPresent(secondPublish);

        JsonNode data = parseResult(secondPublish).data();
        assertEquals("PUBLISHED", data.get("status").asText(), "幂等发布后 status 仍为 PUBLISHED");
    }

    // ===== CT-7: 禁用后发布 (DISABLED → PUBLISH 不允许) =====

    @Test
    @DisplayName("CT-7: 禁用后发布 (DISABLED→PUBLISH 不允许, 409 SYS-409004)")
    void testPublishLowcodeEntityFromDisabled() throws Exception {
        String[] idAndVersion = createEntityAndTrack();
        String id = idAndVersion[0];
        int version = Integer.parseInt(idAndVersion[1]);

        // 先禁用: DRAFT → DISABLED
        MvcResult disableResult = performPost(
                API_PATH + "/" + id + "/disable?version=" + version, null, mockUser("admin"));
        assertEquals(200, disableResult.getResponse().getStatus(), "禁用应成功");
        int disabledVersion = parseResult(disableResult).data().get("version").asInt();

        // 尝试发布已禁用的实体: DISABLED → PUBLISH 不允许 (状态机: PUBLISH 仅允许 DRAFT)
        MvcResult result = performPost(
                API_PATH + "/" + id + "/publish?version=" + disabledVersion, null, mockUser("admin"));

        assertEquals(409, result.getResponse().getStatus(), "禁用后发布, HTTP 应为 409");
        assertError(result, ERROR_CODE_CONFLICT);
        assertTraceIdPresent(result);
    }
}
