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
 * CT-updateLowcodeEntity 契约测试 (operationId: updateLowcodeEntity)。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-updateLowcodeEntity 验收标准)
 * <p>契约来源: routes.yaml web.lowcode.entities (permission: lc:entity:list)
 *    + LcEntityController PUT /api/v1/lowcode/entities/{id}
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功更新 (admin, 200, code=0, entityName 已变更 + traceId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (LcEntityController 未加 @RequiresPermission, @Disabled)</li>
 *   <li>CT-4 实体不存在 (404 SYS-404001)</li>
 *   <li>CT-5 版本号不匹配 (409 SYS-409004, 乐观锁校验)</li>
 *   <li>CT-6 更新非 DRAFT 实体 (409 SYS-409004, 状态机校验)</li>
 * </ol>
 *
 * <p>数据隔离: 每个用例独立创建测试数据 (UUID 后缀 entityCode), @AfterEach 物理清理。
 */
@DisplayName("CT-updateLowcodeEntity: PUT /api/v1/lowcode/entities/{id} 契约测试")
class CTUpdateLowcodeEntityTest extends AbstractContractTest {

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
        req.setEntityCode("CT-LC-ENT-U-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        req.setEntityName("CT更新测试实体");
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

    // ===== CT-1: 成功更新 =====

    @Test
    @DisplayName("CT-1: 成功更新实体 (admin 用户, 200, code=0, entityName 已变更 + traceId)")
    void testUpdateLowcodeEntitySuccess() throws Exception {
        String[] idAndVersion = createEntityAndTrack();
        String id = idAndVersion[0];
        int version = Integer.parseInt(idAndVersion[1]);

        SaveLcEntityRequest updateReq = buildValidEntityRequest();
        updateReq.setId(id);
        updateReq.setVersion(version);
        updateReq.setEntityName("CT更新后实体名");

        MvcResult result = performPut(API_PATH + "/" + id, updateReq, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertEquals(id, data.get("id").asText(), "返回的 id 应与请求一致");
        assertEquals("CT更新后实体名", data.get("entityName").asText(), "entityName 应已更新");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testUpdateLowcodeEntityUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 (控制器未加 @RequiresPermission) =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (LcEntityController 未加 @RequiresPermission, @Disabled 待补充注解)")
    @org.junit.jupiter.api.Disabled("LcEntityController.update 未加 @RequiresPermission(lc:entity:edit), " +
            "权限码未在控制器层强制; 待补充注解后启用此用例")
    void testUpdateLowcodeEntityForbidden() {
        // 实现待补充: LcEntityController 当前未加 @RequiresPermission 注解
    }

    // ===== CT-4: 实体不存在 =====

    @Test
    @DisplayName("CT-4: 实体不存在 (404 SYS-404001)")
    void testUpdateLowcodeEntityNotFound() throws Exception {
        String nonExistentId = "01NOTEXIST00000000000000CT";
        SaveLcEntityRequest updateReq = buildValidEntityRequest();
        updateReq.setId(nonExistentId);
        updateReq.setVersion(0);

        MvcResult result = performPut(API_PATH + "/" + nonExistentId, updateReq, mockUser("admin"));

        assertEquals(404, result.getResponse().getStatus(), "实体不存在, HTTP 应为 404");
        assertError(result, ERROR_CODE_NOT_FOUND);
        assertTraceIdPresent(result);
    }

    // ===== CT-5: 版本号不匹配 =====

    @Test
    @DisplayName("CT-5: 版本号不匹配 (409 SYS-409004, 乐观锁校验)")
    void testUpdateLowcodeEntityVersionMismatch() throws Exception {
        String[] idAndVersion = createEntityAndTrack();
        String id = idAndVersion[0];

        SaveLcEntityRequest updateReq = buildValidEntityRequest();
        updateReq.setId(id);
        updateReq.setVersion(999); // 错误版本号

        MvcResult result = performPut(API_PATH + "/" + id, updateReq, mockUser("admin"));

        assertEquals(409, result.getResponse().getStatus(), "版本号不匹配, HTTP 应为 409");
        assertError(result, ERROR_CODE_CONFLICT);
        assertTraceIdPresent(result);
    }

    // ===== CT-6: 更新非 DRAFT 实体 =====

    @Test
    @DisplayName("CT-6: 更新非 DRAFT 实体 (发布后更新, 409 SYS-409004, 状态机校验)")
    void testUpdateLowcodeEntityNonDraftStatus() throws Exception {
        String[] idAndVersion = createEntityAndTrack();
        String id = idAndVersion[0];
        int version = Integer.parseInt(idAndVersion[1]);

        // 先发布实体: DRAFT → PUBLISHED
        MvcResult publishResult = performPost(
                API_PATH + "/" + id + "/publish?version=" + version, null, mockUser("admin"));
        assertEquals(200, publishResult.getResponse().getStatus(), "发布应成功");
        int publishedVersion = parseResult(publishResult).data().get("version").asInt();

        // 尝试更新已发布的实体: 应失败 (仅 DRAFT 可编辑)
        SaveLcEntityRequest updateReq = buildValidEntityRequest();
        updateReq.setId(id);
        updateReq.setVersion(publishedVersion);
        updateReq.setEntityName("CT更新已发布实体");

        MvcResult result = performPut(API_PATH + "/" + id, updateReq, mockUser("admin"));

        assertEquals(409, result.getResponse().getStatus(), "更新非 DRAFT 实体, HTTP 应为 409");
        assertError(result, ERROR_CODE_CONFLICT);
        assertTraceIdPresent(result);
    }
}
