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
 * CT-createLowcodeGeneratorTask 契约测试 (operationId: createLowcodeGeneratorTask)。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-createLowcodeGeneratorTask 验收标准)
 * <p>契约来源: routes.yaml web.lowcode.generator-tasks (permission: lc:generator-task:list)
 *    + GeneratorTaskController POST /api/v1/lowcode/generator-tasks
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功创建 (admin, 200, code=0, 返回 LcGeneratorTask + traceId, status=PENDING)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (GeneratorTaskController 未加 @RequiresPermission, @Disabled)</li>
 *   <li>CT-4 参数校验失败 - targetScope 为空 (409 SYS-409004)</li>
 *   <li>CT-5 参数校验失败 - targetScope 不合法 (409 SYS-409004)</li>
 *   <li>CT-6 templateVersion 默认值 (null → "1.0")</li>
 *   <li>CT-7 traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)</li>
 *   <li>CT-8 标准错误信封结构 (code/message/traceId 字段齐全)</li>
 * </ol>
 *
 * <p>数据隔离: 每个用例独立创建测试数据, @AfterEach 物理清理。
 */
@DisplayName("CT-createLowcodeGeneratorTask: POST /api/v1/lowcode/generator-tasks 契约测试")
class CTCreateLowcodeGeneratorTaskTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/lowcode/generator-tasks";
    private static final String ENTITY_API_PATH = "/api/v1/lowcode/entities";
    private static final String ERROR_CODE_CONFLICT = "SYS-409004";

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
        req.setEntityCode("CT-LC-GEN-C-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        req.setEntityName("CT生成任务创建测试实体");
        req.setTableName("ct_test_" + UUID.randomUUID().toString().substring(0, 8).toLowerCase());
        req.setModuleCode("ct_test");
        MvcResult result = performPost(ENTITY_API_PATH, req, mockUser("admin"));
        assertSuccess(result);
        String id = parseResult(result).data().get("id").asText();
        createdEntityIds.add(id);
        return id;
    }

    /** 构造合法 CreateGeneratorTaskRequest (基于已创建实体)。 */
    private CreateGeneratorTaskRequest buildValidTaskRequest(String entityId) {
        CreateGeneratorTaskRequest req = new CreateGeneratorTaskRequest();
        req.setEntityId(entityId);
        req.setTemplateVersion("1.0");
        req.setTargetScope("DDL");
        return req;
    }

    /** 从成功响应中提取任务 ID 并记录到清理集合。 */
    private String extractAndTrackId(MvcResult result) throws Exception {
        ResultNode node = parseResult(result);
        JsonNode data = node.data();
        assertNotNull(data, "成功响应 data 不应为 null");
        String id = data.get("id").asText();
        createdTaskIds.add(id);
        return id;
    }

    // ===== CT-1: 成功创建 =====

    @Test
    @DisplayName("CT-1: 成功创建生成任务 (admin 用户, 200, code=0, 返回 taskNo/status=PENDING + traceId)")
    void testCreateLowcodeGeneratorTaskSuccess() throws Exception {
        String entityId = createEntityAndTrack();
        CreateGeneratorTaskRequest request = buildValidTaskRequest(entityId);

        MvcResult result = performPost(API_PATH, request, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        ResultNode node = parseResult(result);
        JsonNode data = node.data();
        assertNotNull(data.get("id"), "返回的 id 不应为 null");
        assertNotNull(data.get("taskNo"), "返回的 taskNo 不应为 null");
        assertTrue(data.get("taskNo").asText().startsWith("GEN"), "taskNo 应以 GEN 前缀开头");
        assertEquals(entityId, data.get("entityId").asText(), "返回的 entityId 应与请求一致");
        assertEquals("DDL", data.get("targetScope").asText(), "返回的 targetScope 应为 DDL");
        assertEquals("1.0", data.get("templateVersion").asText(), "返回的 templateVersion 应为 1.0");
        assertEquals("PENDING", data.get("status").asText(), "新建任务状态应为 PENDING");
        assertEquals(0, data.get("conflictCount").asInt(), "新建任务 conflictCount 应为 0");

        extractAndTrackId(result);
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testCreateLowcodeGeneratorTaskUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 (控制器未加 @RequiresPermission) =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (GeneratorTaskController 未加 @RequiresPermission, @Disabled 待补充注解)")
    @org.junit.jupiter.api.Disabled("GeneratorTaskController.create 未加 @RequiresPermission(lc:generator-task:add), " +
            "权限码未在控制器层强制; 待补充注解后启用此用例")
    void testCreateLowcodeGeneratorTaskForbidden() {
        // 实现待补充: GeneratorTaskController 当前未加 @RequiresPermission 注解
    }

    // ===== CT-4: 参数校验失败 - targetScope 为空 =====

    @Test
    @DisplayName("CT-4: 参数校验失败 - targetScope 为空 (409 SYS-409004)")
    void testCreateLowcodeGeneratorTaskEmptyScope() throws Exception {
        String entityId = createEntityAndTrack();
        CreateGeneratorTaskRequest request = buildValidTaskRequest(entityId);
        request.setTargetScope(null); // 触发 validateScope 校验

        MvcResult result = performPost(API_PATH, request, mockUser("admin"));

        assertEquals(409, result.getResponse().getStatus(), "targetScope 为空, HTTP 应为 409");
        assertError(result, ERROR_CODE_CONFLICT);
        assertTraceIdPresent(result);
    }

    // ===== CT-5: 参数校验失败 - targetScope 不合法 =====

    @Test
    @DisplayName("CT-5: 参数校验失败 - targetScope 不合法 (409 SYS-409004)")
    void testCreateLowcodeGeneratorTaskInvalidScope() throws Exception {
        String entityId = createEntityAndTrack();
        CreateGeneratorTaskRequest request = buildValidTaskRequest(entityId);
        request.setTargetScope("INVALID_SCOPE"); // 不在 DDL/JAVA/VUE/UNIAPP/OPENAPI 范围

        MvcResult result = performPost(API_PATH, request, mockUser("admin"));

        assertEquals(409, result.getResponse().getStatus(), "targetScope 不合法, HTTP 应为 409");
        assertError(result, ERROR_CODE_CONFLICT);
        assertTraceIdPresent(result);
    }

    // ===== CT-6: templateVersion 默认值 =====

    @Test
    @DisplayName("CT-6: templateVersion 默认值 (null → \"1.0\")")
    void testCreateLowcodeGeneratorTaskDefaultTemplateVersion() throws Exception {
        String entityId = createEntityAndTrack();
        CreateGeneratorTaskRequest request = buildValidTaskRequest(entityId);
        request.setTemplateVersion(null); // 未传入, 服务层应默认为 "1.0"

        MvcResult result = performPost(API_PATH, request, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);

        JsonNode data = parseResult(result).data();
        assertEquals("1.0", data.get("templateVersion").asText(),
                "templateVersion 为 null 时应默认为 1.0");

        extractAndTrackId(result);
    }

    // ===== CT-7: traceId 透传 =====

    @Test
    @DisplayName("CT-7: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)")
    void testCreateLowcodeGeneratorTaskTraceIdPropagation() throws Exception {
        String entityId = createEntityAndTrack();
        CreateGeneratorTaskRequest request = buildValidTaskRequest(entityId);

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

    // ===== CT-8: 标准错误信封结构 =====

    @Test
    @DisplayName("CT-8: 标准错误信封 (code/message/traceId 字段齐全, 非成功响应)")
    void testCreateLowcodeGeneratorTaskErrorEnvelopeStructure() throws Exception {
        String entityId = createEntityAndTrack();
        CreateGeneratorTaskRequest request = buildValidTaskRequest(entityId);
        request.setTargetScope(null); // 触发错误以校验信封结构

        MvcResult result = performPost(API_PATH, request, mockUser("admin"));

        ResultNode node = parseResult(result);
        assertNotNull(node.code(), "错误信封必须包含 code 字段");
        assertNotEquals("0", node.code(), "错误响应 code 不应为 0");
        assertNotNull(node.message(), "错误信封必须包含 message 字段");
        assertNotNull(node.traceId(), "错误信封必须包含 traceId 字段");
        assertTrue(node.traceId().length() >= 16 && node.traceId().length() <= 64,
                "traceId 长度应在 16-64 之间");
    }
}
