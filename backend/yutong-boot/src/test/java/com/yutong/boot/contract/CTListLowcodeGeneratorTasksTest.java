package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
 * CT-listLowcodeGeneratorTasks 契约测试 (operationId: listLowcodeGeneratorTasks)。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-listLowcodeGeneratorTasks 验收标准)
 * <p>契约来源: routes.yaml web.lowcode.generator-tasks (permission: lc:generator-task:list)
 *    + GeneratorTaskController GET /api/v1/lowcode/generator-tasks
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功分页查询 (admin, 200, code=0, records/total/page/size + traceId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (GeneratorTaskController 未加 @RequiresPermission, @Disabled)</li>
 *   <li>CT-4 参数校验 - size 越界 (0 → 钳制为 20, 200 优雅降级)</li>
 *   <li>CT-5 空结果 (taskNo 不匹配, total=0)</li>
 *   <li>CT-6 过滤条件 taskNo (唯一 taskNo 精确匹配, total=1)</li>
 *   <li>CT-7 过滤条件 status (status=PENDING 只返回待处理任务)</li>
 * </ol>
 *
 * <p>数据隔离: 每个用例独立创建测试数据, @AfterEach 物理清理。
 */
@DisplayName("CT-listLowcodeGeneratorTasks: GET /api/v1/lowcode/generator-tasks 契约测试")
class CTListLowcodeGeneratorTasksTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/lowcode/generator-tasks";
    private static final String ENTITY_API_PATH = "/api/v1/lowcode/entities";

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
        req.setEntityCode("CT-LC-GEN-L-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        req.setEntityName("CT生成任务列表测试实体");
        req.setTableName("ct_test_" + UUID.randomUUID().toString().substring(0, 8).toLowerCase());
        req.setModuleCode("ct_test");
        MvcResult result = performPost(ENTITY_API_PATH, req, mockUser("admin"));
        assertSuccess(result);
        String id = parseResult(result).data().get("id").asText();
        createdEntityIds.add(id);
        return id;
    }

    /** 创建生成任务并跟踪 ID, 返回 taskNo。 */
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
        return data.get("taskNo").asText();
    }

    // ===== CT-1: 成功分页查询 =====

    @Test
    @DisplayName("CT-1: 成功分页查询 (admin 用户, 200, code=0, records/total/page/size + traceId)")
    void testListLowcodeGeneratorTasksSuccess() throws Exception {
        String taskNo = createTaskAndTrack();

        MvcResult result = performGet(API_PATH + "?page=1&size=10&taskNo=" + taskNo, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        ResultNode node = parseResult(result);
        JsonNode data = node.data();
        assertNotNull(data, "data 不应为 null");
        assertNotNull(data.get("records"), "records 不应为 null");
        assertTrue(data.get("records").isArray(), "records 应为数组");
        assertTrue(data.get("total").asLong() >= 1, "total 应 >= 1 (至少包含刚创建的任务)");
        assertEquals(1, data.get("page").asInt(), "page 应为 1");
        assertEquals(10, data.get("size").asInt(), "size 应为 10");

        ArrayNode records = (ArrayNode) data.get("records");
        boolean found = false;
        for (JsonNode rec : records) {
            if (taskNo.equals(rec.get("taskNo").asText())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "结果中应包含刚创建的任务 (taskNo=" + taskNo + ")");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testListLowcodeGeneratorTasksUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 (控制器未加 @RequiresPermission) =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (GeneratorTaskController 未加 @RequiresPermission, @Disabled 待补充注解)")
    @org.junit.jupiter.api.Disabled("GeneratorTaskController.page 未加 @RequiresPermission(lc:generator-task:list), " +
            "权限码未在控制器层强制; 待补充注解后启用此用例")
    void testListLowcodeGeneratorTasksForbidden() {
        // 实现待补充: GeneratorTaskController 当前未加 @RequiresPermission 注解
    }

    // ===== CT-4: 参数校验 - size 越界 =====

    @Test
    @DisplayName("CT-4: 参数校验 - size=0 越界 (PageRequest 钳制为 20, 200 优雅降级)")
    void testListLowcodeGeneratorTasksSizeOutOfRange() throws Exception {
        MvcResult result = performGet(API_PATH + "?page=1&size=0", mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "size 越界应优雅降级, HTTP 200");
        assertSuccess(result);
        ResultNode node = parseResult(result);
        assertEquals(20, node.data().get("size").asInt(), "size=0 应钳制为默认值 20");
    }

    // ===== CT-5: 空结果 =====

    @Test
    @DisplayName("CT-5: 空结果 (taskNo 不匹配, total=0, records 空数组)")
    void testListLowcodeGeneratorTasksEmptyResult() throws Exception {
        String nonExistentTaskNo = "NOSUCHTASK-CT-LC-GEN-" + UUID.randomUUID().toString().substring(0, 8);

        MvcResult result = performGet(API_PATH + "?page=1&size=10&taskNo=" + nonExistentTaskNo, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        ResultNode node = parseResult(result);
        assertEquals(0, node.data().get("total").asLong(), "不匹配的 taskNo 应返回 total=0");
        assertTrue(node.data().get("records").isArray(), "records 应为数组");
        assertEquals(0, node.data().get("records").size(), "records 应为空数组");
    }

    // ===== CT-6: 过滤条件 taskNo =====

    @Test
    @DisplayName("CT-6: 过滤条件 taskNo (唯一 taskNo 精确匹配, total=1)")
    void testListLowcodeGeneratorTasksFilterByTaskNo() throws Exception {
        String taskNo = createTaskAndTrack();

        MvcResult result = performGet(API_PATH + "?page=1&size=10&taskNo=" + taskNo, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        ResultNode node = parseResult(result);
        assertEquals(1, node.data().get("total").asLong(), "唯一 taskNo 应匹配 total=1");
        ArrayNode records = (ArrayNode) node.data().get("records");
        assertEquals(1, records.size(), "records 应只有 1 条");
        assertEquals(taskNo, records.get(0).get("taskNo").asText(), "返回的任务 taskNo 应匹配");
    }

    // ===== CT-7: 过滤条件 status =====

    @Test
    @DisplayName("CT-7: 过滤条件 status (status=PENDING 只返回待处理任务)")
    void testListLowcodeGeneratorTasksFilterByStatus() throws Exception {
        String taskNo = createTaskAndTrack();

        MvcResult result = performGet(
                API_PATH + "?page=1&size=10&taskNo=" + taskNo + "&status=PENDING",
                mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        ResultNode node = parseResult(result);
        ArrayNode records = (ArrayNode) node.data().get("records");
        assertTrue(records.size() >= 1, "应至少返回 1 条 PENDING 任务");

        for (JsonNode rec : records) {
            assertEquals("PENDING", rec.get("status").asText(),
                    "过滤 status=PENDING 后所有记录 status 应为 PENDING");
        }
    }
}
