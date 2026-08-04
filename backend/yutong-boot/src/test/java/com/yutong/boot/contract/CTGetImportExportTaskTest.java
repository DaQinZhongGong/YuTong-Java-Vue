package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
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
 * CT-getImportExportTask 契约测试 (operationId: getImportExportTask)。
 *
 * <p>契约来源: routes.yaml operationIds: [getImportExportTask],
 * GET /api/v1/import-export-tasks/{id}
 * <p>控制器: ImportExportTaskController.get(id) - 无 @RequiresPermission (所有认证用户可访问)
 * <p>服务: ImportExportTaskService.getTask - 不存在时抛 ResourceNotFoundException (SYS-404001)
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功查询 (200, code=0, 返回 SysImportExportTask + traceId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 任务不存在 (404 SYS-404001)</li>
 *   <li>CT-4 字段完整性 (id/taskType/bizType/status/totalRows/successRows/failRows 全返回)</li>
 *   <li>CT-5 traceId 透传 (响应头 + 响应体)</li>
 * </ol>
 *
 * <p>数据隔离: 通过 JdbcTemplate 直接插入 sys_import_export_task 测试数据, @AfterEach 物理清理。
 */
@DisplayName("CT-getImportExportTask: GET /api/v1/import-export-tasks/{id} 契约测试")
class CTGetImportExportTaskTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/import-export-tasks";
    private static final String ERROR_CODE_NOT_FOUND = "SYS-404001";
    private static final String TENANT_ID = "default";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final java.util.Set<String> createdIds = ConcurrentHashMap.newKeySet();

    @AfterEach
    void cleanupTestData() {
        for (String id : createdIds) {
            jdbcTemplate.update("DELETE FROM sys_import_export_task WHERE id = ?", id);
        }
        createdIds.clear();
    }

    /** 通过 JdbcTemplate 插入导入导出任务测试数据, 返回生成的 id。 */
    private String createTaskAndTrack() {
        String id = "CTIETASK" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        jdbcTemplate.update(
                "INSERT INTO sys_import_export_task (id, tenant_id, task_type, biz_type, file_id, status, " +
                        "total_rows, success_rows, fail_rows, error_file_id, started_time, finished_time, " +
                        "error_message, created_by, created_time, updated_by, updated_time, deleted, version) " +
                        "VALUES (?, ?, ?, ?, NULL, ?, 100, 95, 5, NULL, now(), now(), NULL, ?, now(), ?, now(), false, 0)",
                id, TENANT_ID, "IMPORT", "sample", "SUCCESS",
                "ct-task-user", "ct-task-user");
        createdIds.add(id);
        return id;
    }

    // ===== CT-1: 成功查询 =====

    @Test
    @DisplayName("CT-1: 成功查询导入导出任务详情 (admin 用户, 200, code=0, 返回 SysImportExportTask + traceId)")
    void testGetImportExportTaskSuccess() throws Exception {
        String id = createTaskAndTrack();

        MvcResult result = performGet(API_PATH + "/" + id, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertEquals(id, data.get("id").asText(), "返回 id 应与请求 id 一致");
        assertEquals("IMPORT", data.get("taskType").asText());
        assertEquals("SUCCESS", data.get("status").asText());
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testGetImportExportTaskUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 任务不存在 =====

    @Test
    @DisplayName("CT-3: 导入导出任务不存在 (404 SYS-404001)")
    void testGetImportExportTaskNotFound() throws Exception {
        String nonExistentId = "NOSUCHTASK" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        MvcResult result = performGet(API_PATH + "/" + nonExistentId, mockUser("admin"));

        assertEquals(404, result.getResponse().getStatus(), "任务不存在, HTTP 应为 404");
        assertError(result, ERROR_CODE_NOT_FOUND);
        assertTraceIdPresent(result);
    }

    // ===== CT-4: 字段完整性 =====

    @Test
    @DisplayName("CT-4: 字段完整性 (id/taskType/bizType/status/totalRows/successRows/failRows 全返回)")
    void testGetImportExportTaskFieldCompleteness() throws Exception {
        String id = createTaskAndTrack();

        MvcResult result = performGet(API_PATH + "/" + id, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertNotNull(data.get("id"), "应包含 id 字段");
        assertNotNull(data.get("taskType"), "应包含 taskType 字段");
        assertNotNull(data.get("bizType"), "应包含 bizType 字段");
        assertNotNull(data.get("status"), "应包含 status 字段");
        assertNotNull(data.get("totalRows"), "应包含 totalRows 字段");
        assertNotNull(data.get("successRows"), "应包含 successRows 字段");
        assertNotNull(data.get("failRows"), "应包含 failRows 字段");
        assertNotNull(data.get("startedTime"), "应包含 startedTime 字段");
        assertNotNull(data.get("finishedTime"), "应包含 finishedTime 字段");
    }

    // ===== CT-5: traceId 透传 =====

    @Test
    @DisplayName("CT-5: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)")
    void testGetImportExportTaskTraceIdPropagation() throws Exception {
        String id = createTaskAndTrack();

        MvcResult result = performGet(API_PATH + "/" + id, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        ResultNode node = parseResult(result);
        assertNotNull(node.traceId(), "响应体应包含 traceId");
        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertNotNull(headerTraceId, "响应头应包含 X-Trace-Id");
        assertEquals(node.traceId(), headerTraceId, "响应头 X-Trace-Id 应与响应体 traceId 一致");
    }
}
