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
 * CT-retryImportExportTask 契约测试 (operationId: retryImportExportTask)。
 *
 * <p>契约来源: routes.yaml operationIds: [retryImportExportTask],
 * POST /api/v1/import-export-tasks/{id}/retry
 * <p>控制器: ImportExportTaskController.retry(id) - @RequiresPermission("system:task:retry")
 * <p>权限码: system:task:retry (仅 admin 通配 * 拥有; biz/approver/viewer 无此权限 → 403)
 * <p>服务: ImportExportTaskService.retryTask - 不存在时抛 ResourceNotFoundException (SYS-404001);
 * 更新 status=PENDING 并返回任务记录
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功重试 (200, code=0, 返回 SysImportExportTask + traceId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (viewer 无 system:task:retry, 403 AUTH-403001)</li>
 *   <li>CT-4 任务不存在 (404 SYS-404001)</li>
 *   <li>CT-5 traceId 透传 (响应头 + 响应体)</li>
 *   <li>CT-6 字段更新生效 (status=PENDING)</li>
 * </ol>
 *
 * <p>数据隔离: 通过 JdbcTemplate 直接插入 sys_import_export_task 测试数据, @AfterEach 物理清理。
 */
@DisplayName("CT-retryImportExportTask: POST /api/v1/import-export-tasks/{id}/retry 契约测试")
class CTRetryImportExportTaskTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/import-export-tasks";
    private static final String ERROR_CODE_FORBIDDEN = "AUTH-403001";
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
    private String createTaskAndTrack(String status) {
        String id = "CTIETASK" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        jdbcTemplate.update(
                "INSERT INTO sys_import_export_task (id, tenant_id, task_type, biz_type, file_id, status, " +
                        "total_rows, success_rows, fail_rows, error_file_id, started_time, finished_time, " +
                        "error_message, created_by, created_time, updated_by, updated_time, deleted, version) " +
                        "VALUES (?, ?, ?, ?, NULL, ?, 100, 0, 100, NULL, now(), now(), 'CT测试错误', ?, now(), ?, now(), false, 0)",
                id, TENANT_ID, "IMPORT", "sample", status,
                "ct-task-user", "ct-task-user");
        createdIds.add(id);
        return id;
    }

    // ===== CT-1: 成功重试 =====

    @Test
    @DisplayName("CT-1: 成功重试 (admin, 200, code=0, 返回 SysImportExportTask + traceId)")
    void testRetryImportExportTaskSuccess() throws Exception {
        String id = createTaskAndTrack("FAILED");

        MvcResult result = performPost(API_PATH + "/" + id + "/retry", null, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");
        assertEquals(id, data.get("id").asText(), "返回 id 应与请求 id 一致");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testRetryImportExportTaskUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (viewer 无 system:task:retry, 403 AUTH-403001)")
    void testRetryImportExportTaskForbidden() throws Exception {
        String id = createTaskAndTrack("FAILED");

        MvcResult result = performPost(API_PATH + "/" + id + "/retry", null, mockUser("viewer"));

        assertEquals(403, result.getResponse().getStatus(), "viewer 无重试权限, HTTP 应为 403");
        assertError(result, ERROR_CODE_FORBIDDEN);
        assertTraceIdPresent(result);
    }

    // ===== CT-4: 任务不存在 =====

    @Test
    @DisplayName("CT-4: 导入导出任务不存在 (404 SYS-404001)")
    void testRetryImportExportTaskNotFound() throws Exception {
        String nonExistentId = "NOSUCHTASK" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        MvcResult result = performPost(API_PATH + "/" + nonExistentId + "/retry", null, mockUser("admin"));

        assertEquals(404, result.getResponse().getStatus(), "任务不存在, HTTP 应为 404");
        assertError(result, ERROR_CODE_NOT_FOUND);
        assertTraceIdPresent(result);
    }

    // ===== CT-5: traceId 透传 =====

    @Test
    @DisplayName("CT-5: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)")
    void testRetryImportExportTaskTraceIdPropagation() throws Exception {
        String id = createTaskAndTrack("FAILED");

        MvcResult result = performPost(API_PATH + "/" + id + "/retry", null, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        ResultNode node = parseResult(result);
        assertNotNull(node.traceId(), "响应体应包含 traceId");
        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertNotNull(headerTraceId, "响应头应包含 X-Trace-Id");
        assertEquals(node.traceId(), headerTraceId, "响应头 X-Trace-Id 应与响应体 traceId 一致");
    }

    // ===== CT-6: 字段更新生效 =====

    @Test
    @DisplayName("CT-6: 字段更新生效 (status=PENDING)")
    void testRetryImportExportTaskFieldsEffective() throws Exception {
        String id = createTaskAndTrack("FAILED");

        MvcResult result = performPost(API_PATH + "/" + id + "/retry", null, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertEquals("PENDING", data.get("status").asText(), "重试后 status 应更新为 PENDING");
    }
}
