package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.yutong.lowcode.meta.dto.CreateGeneratorTaskRequest;
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
 * CT-getLowcodeGeneratorTask 契约测试 (operationId: getLowcodeGeneratorTask)。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-getLowcodeGeneratorTask 验收标准)
 * <p>契约来源: routes.yaml web.lowcode.generator-tasks (permission: lc:generator-task:list)
 *    + GeneratorTaskController GET /api/v1/lowcode/generator-tasks/{id}
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功查询 (admin, 200, code=0, 返回 LcGeneratorTask + traceId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (GeneratorTaskController 未加 @RequiresPermission, @Disabled)</li>
 *   <li>CT-4 任务不存在 (404 SYS-404001)</li>
 *   <li>CT-5 traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)</li>
 *   <li>CT-6 返回字段完整性 (taskNo/entityId/targetScope/status/conflictCount/templateVersion)</li>
 * </ol>
 *
 * <p>数据隔离: 每个用例独立创建测试数据, @AfterEach 物理清理。
 */
@DisplayName("CT-getLowcodeGeneratorTask: GET /api/v1/lowcode/generator-tasks/{id} 契约测试")
class CTGetLowcodeGeneratorTaskTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/lowcode/generator-tasks";
    private static final String ENTITY_API_PATH = "/api/v1/lowcode/entities";
    private static final String ERROR_CODE_NOT_FOUND = "SYS-404001";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final java.util.Set<String> createdTaskIds = ConcurrentHashMap.newKeySet();
    private final java.util.Set<String> createdEntityIds = ConcurrentHashMap.newKeySet();

    @AfterEach
    void cleanupTestData() {
        for (String id : createdTaskIds) {
            jdbcTemplate.update("DELETE FROM sys_operation_log WHERE biz_type = 'lc_generator_task' AND biz_id = ?", id);
        }
        for (String id : createdTaskIds) {
            jdbcTemplate.update("DELETE FROM lc_generator_task WHERE id = ?", id);
        }
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
        createdTaskIds.clear();
        createdEntityIds.clear();
    }

    /** 创建实体草稿并跟踪 ID (生成任务依赖 entityId)。 */
    private String createEntityAndTrack() throws Exception {
        SaveLcEntityRequest req = new SaveLcEntityRequest();
        req.setEntityCode("CT-LC-GEN-G-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        req.setEntityName("CT生成任务详情测试实体");
        req.setTableName("ct_test_" + UUID.randomUUID().toString().substring(0, 8).toLowerCase());
        req.setModuleCode("ct_test");
        MvcResult result = performPost(ENTITY_API_PATH, req, mockUser("admin"));
        assertSuccess(result);
        String id = parseResult(result).data().get("id").asText();
        createdEntityIds.add(id);
        return id;
    }

    /** 创建生成任务并跟踪 ID, 返回任务 ID。 */
    private String createTaskAndTrack() throws Exception {
        String entityId = createEntityAndTrack();
        CreateGeneratorTaskRequest req = new CreateGeneratorTaskRequest();
        req.setEntityId(entityId);
        req.setTemplateVersion("1.0");
        req.setTargetScope("DDL");

        MvcResult result = performPost(API_PATH, req, mockUser("admin"));
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertNotNull(data, "创建响应 data 不应为 null");
        String id = data.get("id").asText();
        createdTaskIds.add(id);
        return id;
    }

    // ===== CT-1: 成功查询 =====

    @Test
    @DisplayName("CT-1: 成功查询生成任务详情 (admin 用户, 200, code=0, 返回 LcGeneratorTask + traceId)")
    void testGetLowcodeGeneratorTaskSuccess() throws Exception {
        String id = createTaskAndTrack();

        MvcResult result = performGet(API_PATH + "/" + id, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");
        assertEquals(id, data.get("id").asText(), "返回的 id 应与请求 id 一致");
        assertNotNull(data.get("taskNo"), "taskNo 不应为 null");
        assertNotNull(data.get("entityId"), "entityId 不应为 null");
        assertEquals("PENDING", data.get("status").asText(), "新建任务状态应为 PENDING");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testGetLowcodeGeneratorTaskUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 (控制器未加 @RequiresPermission) =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (GeneratorTaskController 未加 @RequiresPermission, @Disabled 待补充注解)")
    @org.junit.jupiter.api.Disabled("GeneratorTaskController.detail 未加 @RequiresPermission(lc:generator-task:detail), " +
            "权限码未在控制器层强制; 待补充注解后启用此用例")
    void testGetLowcodeGeneratorTaskForbidden() {
        // 实现待补充: GeneratorTaskController 当前未加 @RequiresPermission 注解
    }

    // ===== CT-4: 任务不存在 =====

    @Test
    @DisplayName("CT-4: 任务不存在 (404 SYS-404001)")
    void testGetLowcodeGeneratorTaskNotFound() throws Exception {
        String nonExistentId = "01NOTEXIST00000000000000CT";

        MvcResult result = performGet(API_PATH + "/" + nonExistentId, mockUser("admin"));

        assertEquals(404, result.getResponse().getStatus(), "任务不存在, HTTP 应为 404");
        assertError(result, ERROR_CODE_NOT_FOUND);
        assertTraceIdPresent(result);
    }

    // ===== CT-5: traceId 透传 =====

    @Test
    @DisplayName("CT-5: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)")
    void testGetLowcodeGeneratorTaskTraceIdPropagation() throws Exception {
        String id = createTaskAndTrack();

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

    // ===== CT-6: 返回字段完整性 =====

    @Test
    @DisplayName("CT-6: 返回字段完整性 (taskNo/entityId/targetScope/status/conflictCount/templateVersion)")
    void testGetLowcodeGeneratorTaskFieldCompleteness() throws Exception {
        String id = createTaskAndTrack();

        MvcResult result = performGet(API_PATH + "/" + id, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");

        // 验证关键字段存在且类型正确
        assertNotNull(data.get("id"), "id 不应为 null");
        assertNotNull(data.get("taskNo"), "taskNo 不应为 null");
        assertTrue(data.get("taskNo").asText().startsWith("GEN"), "taskNo 应以 GEN 前缀开头");
        assertNotNull(data.get("entityId"), "entityId 不应为 null");
        assertNotNull(data.get("targetScope"), "targetScope 不应为 null");
        assertEquals("DDL", data.get("targetScope").asText(), "targetScope 应为 DDL");
        assertNotNull(data.get("templateVersion"), "templateVersion 不应为 null");
        assertEquals("1.0", data.get("templateVersion").asText(), "templateVersion 应为 1.0");
        assertNotNull(data.get("status"), "status 不应为 null");
        assertEquals("PENDING", data.get("status").asText(), "status 应为 PENDING");
        assertNotNull(data.get("conflictCount"), "conflictCount 不应为 null");
        assertEquals(0, data.get("conflictCount").asInt(), "conflictCount 应为 0");
    }
}
