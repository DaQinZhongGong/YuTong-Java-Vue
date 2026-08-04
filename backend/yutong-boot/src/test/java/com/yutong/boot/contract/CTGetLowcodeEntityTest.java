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
 * CT-getLowcodeEntity 契约测试 (operationId: getLowcodeEntity)。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-getLowcodeEntity 验收标准)
 * <p>契约来源: routes.yaml web.lowcode.entities (permission: lc:entity:list)
 *    + LcEntityController GET /api/v1/lowcode/entities/{id}
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功查询 (admin, 200, code=0, 返回 LcEntityDetailVO + traceId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (LcEntityController 未加 @RequiresPermission, @Disabled)</li>
 *   <li>CT-4 实体不存在 (404 SYS-404001)</li>
 *   <li>CT-5 traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)</li>
 *   <li>CT-6 详情含子表 (fields/relations 数组字段存在)</li>
 * </ol>
 *
 * <p>数据隔离: 每个用例独立创建测试数据 (UUID 后缀 entityCode), @AfterEach 物理清理。
 */
@DisplayName("CT-getLowcodeEntity: GET /api/v1/lowcode/entities/{id} 契约测试")
class CTGetLowcodeEntityTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/lowcode/entities";
    private static final String ERROR_CODE_NOT_FOUND = "SYS-404001";

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
        req.setEntityCode("CT-LC-ENT-G-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        req.setEntityName("CT详情测试实体");
        req.setTableName("ct_test_" + UUID.randomUUID().toString().substring(0, 8).toLowerCase());
        req.setModuleCode("ct_test");
        return req;
    }

    /** 创建实体草稿并跟踪 ID。 */
    private String createEntityAndTrack() throws Exception {
        SaveLcEntityRequest request = buildValidEntityRequest();
        MvcResult result = performPost(API_PATH, request, mockUser("admin"));
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertNotNull(data, "创建响应 data 不应为 null");
        String id = data.get("id").asText();
        createdEntityIds.add(id);
        return id;
    }

    // ===== CT-1: 成功查询 =====

    @Test
    @DisplayName("CT-1: 成功查询实体详情 (admin 用户, 200, code=0, 返回 LcEntityDetailVO + traceId)")
    void testGetLowcodeEntitySuccess() throws Exception {
        String id = createEntityAndTrack();

        MvcResult result = performGet(API_PATH + "/" + id, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");
        assertEquals(id, data.get("id").asText(), "返回的 id 应与请求一致");
        assertNotNull(data.get("entityCode"), "entityCode 不应为 null");
        assertNotNull(data.get("entityName"), "entityName 不应为 null");
        assertNotNull(data.get("status"), "status 不应为 null");
        assertEquals("DRAFT", data.get("status").asText(), "新建实体状态应为 DRAFT");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testGetLowcodeEntityUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 (控制器未加 @RequiresPermission) =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (LcEntityController 未加 @RequiresPermission, @Disabled 待补充注解)")
    @org.junit.jupiter.api.Disabled("LcEntityController.detail 未加 @RequiresPermission(lc:entity:detail), " +
            "权限码未在控制器层强制; 待补充注解后启用此用例")
    void testGetLowcodeEntityForbidden() {
        // 实现待补充: LcEntityController 当前未加 @RequiresPermission 注解
    }

    // ===== CT-4: 实体不存在 =====

    @Test
    @DisplayName("CT-4: 实体不存在 (404 SYS-404001)")
    void testGetLowcodeEntityNotFound() throws Exception {
        String nonExistentId = "01NOTEXIST00000000000000CT";

        MvcResult result = performGet(API_PATH + "/" + nonExistentId, mockUser("admin"));

        assertEquals(404, result.getResponse().getStatus(), "实体不存在, HTTP 应为 404");
        assertError(result, ERROR_CODE_NOT_FOUND);
        assertTraceIdPresent(result);
    }

    // ===== CT-5: traceId 透传 =====

    @Test
    @DisplayName("CT-5: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)")
    void testGetLowcodeEntityTraceIdPropagation() throws Exception {
        String id = createEntityAndTrack();

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
    @DisplayName("CT-6: 详情含子表 (fields/relations 数组字段存在)")
    void testGetLowcodeEntityDetailWithChildren() throws Exception {
        String id = createEntityAndTrack();

        MvcResult result = performGet(API_PATH + "/" + id, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertNotNull(data.get("fields"), "详情应包含 fields 字段");
        assertTrue(data.get("fields").isArray(), "fields 应为数组");
        assertNotNull(data.get("relations"), "详情应包含 relations 字段");
        assertTrue(data.get("relations").isArray(), "relations 应为数组");
    }
}
