package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
 * CT-listImportExportTasks 契约测试 (operationId: listImportExportTasks)。
 *
 * <p>契约来源: routes.yaml operationIds: [listImportExportTasks],
 * GET /api/v1/import-export-tasks?page=1&size=20&taskType=...&status=...
 * <p>控制器: ImportExportTaskController.list(page, size, taskType, status)
 * - 无 @RequiresPermission (所有认证用户可访问)
 * <p>服务: ImportExportTaskService.pageTasks - 按 taskType 和 status 过滤, 按 createdTime DESC
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功分页查询 (200, code=0, records/total/page/size + traceId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 空结果 (不匹配 taskType, total=0)</li>
 *   <li>CT-4 过滤条件 taskType (精确匹配 IMPORT/EXPORT)</li>
 *   <li>CT-5 过滤条件 status (精确匹配 SUCCESS/FAILED)</li>
 *   <li>CT-6 排序 (createdTime DESC 非递增)</li>
 *   <li>CT-7 标准响应信封 (records/total/page/size 结构齐全)</li>
 *   <li>CT-8 traceId 透传 (响应头 + 响应体)</li>
 * </ol>
 *
 * <p>数据隔离: 通过 JdbcTemplate 直接插入 sys_import_export_task 测试数据, @AfterEach 物理清理。
 */
@DisplayName("CT-listImportExportTasks: GET /api/v1/import-export-tasks 契约测试")
class CTListImportExportTasksTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/import-export-tasks";
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
    private String createTaskAndTrack(String taskType, String status) {
        String id = "CTIETASK" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        jdbcTemplate.update(
                "INSERT INTO sys_import_export_task (id, tenant_id, task_type, biz_type, file_id, status, " +
                        "total_rows, success_rows, fail_rows, error_file_id, started_time, finished_time, " +
                        "error_message, created_by, created_time, updated_by, updated_time, deleted, version) " +
                        "VALUES (?, ?, ?, ?, NULL, ?, 100, 90, 10, NULL, now(), now(), NULL, ?, now(), ?, now(), false, 0)",
                id, TENANT_ID, taskType, "sample", status,
                "ct-task-user", "ct-task-user");
        createdIds.add(id);
        return id;
    }

    // ===== CT-1: 成功分页查询 =====

    @Test
    @DisplayName("CT-1: 成功分页查询 (admin 用户, 200, code=0, records/total/page/size + traceId)")
    void testListImportExportTasksSuccess() throws Exception {
        createTaskAndTrack("IMPORT", "SUCCESS");

        MvcResult result = performGet(API_PATH + "?page=1&size=10", mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");
        assertNotNull(data.get("records"), "records 不应为 null");
        assertTrue(data.get("records").isArray(), "records 应为数组");
        assertTrue(data.get("total").asLong() >= 1, "total 应 >= 1");
        assertEquals(1, data.get("page").asInt(), "page 应为 1");
        assertEquals(10, data.get("size").asInt(), "size 应为 10");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testListImportExportTasksUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 空结果 =====

    @Test
    @DisplayName("CT-3: 空结果 (不匹配 taskType, total=0, records 空数组)")
    void testListImportExportTasksEmptyResult() throws Exception {
        // 使用不存在的 taskType 查询
        MvcResult result = performGet(API_PATH + "?page=1&size=10&taskType=NOSUCHTYPE_CT", mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertEquals(0, data.get("total").asLong(), "不匹配的 taskType 应返回 total=0");
        assertEquals(0, data.get("records").size(), "records 应为空数组");
    }

    // ===== CT-4: 过滤条件 taskType =====

    @Test
    @DisplayName("CT-4: 过滤条件 taskType=EXPORT (精确匹配)")
    void testListImportExportTasksFilterByTaskType() throws Exception {
        createTaskAndTrack("EXPORT", "SUCCESS");

        MvcResult result = performGet(API_PATH + "?page=1&size=10&taskType=EXPORT", mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertTrue(data.get("total").asLong() >= 1, "taskType=EXPORT 精确匹配应返回 total>=1");

        ArrayNode records = (ArrayNode) data.get("records");
        for (JsonNode rec : records) {
            assertEquals("EXPORT", rec.get("taskType").asText(), "所有返回记录的 taskType 应为 EXPORT");
        }
    }

    // ===== CT-5: 过滤条件 status =====

    @Test
    @DisplayName("CT-5: 过滤条件 status=FAILED (精确匹配)")
    void testListImportExportTasksFilterByStatus() throws Exception {
        createTaskAndTrack("IMPORT", "FAILED");

        MvcResult result = performGet(API_PATH + "?page=1&size=10&status=FAILED", mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        ArrayNode records = (ArrayNode) parseResult(result).data().get("records");
        assertTrue(records.size() >= 1, "应至少返回 1 条 FAILED 记录");
        for (JsonNode rec : records) {
            assertEquals("FAILED", rec.get("status").asText(), "应仅返回 status=FAILED 记录");
        }
    }

    // ===== CT-6: 排序 (createdTime DESC) =====

    @Test
    @DisplayName("CT-6: 排序 (createdTime DESC 非递增)")
    void testListImportExportTasksSortOrder() throws Exception {
        createTaskAndTrack("IMPORT", "SUCCESS");
        Thread.sleep(10);
        createTaskAndTrack("IMPORT", "SUCCESS");

        MvcResult result = performGet(API_PATH + "?page=1&size=20&taskType=IMPORT&status=SUCCESS", mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        ArrayNode records = (ArrayNode) parseResult(result).data().get("records");
        assertTrue(records.size() >= 2, "应至少返回 2 条记录");

        // createdTime DESC (非递增)
        for (int i = 1; i < records.size(); i++) {
            String prev = records.get(i - 1).get("createdTime").asText();
            String curr = records.get(i).get("createdTime").asText();
            assertTrue(prev.compareTo(curr) >= 0,
                    "createdTime 应为 DESC 排序 (非递增), 但 " + prev + " < " + curr);
        }
    }

    // ===== CT-7: 标准响应信封 =====

    @Test
    @DisplayName("CT-7: 标准响应信封 (records/total/page/size 结构齐全)")
    void testListImportExportTasksResponseEnvelope() throws Exception {
        createTaskAndTrack("IMPORT", "SUCCESS");

        MvcResult result = performGet(API_PATH + "?page=1&size=5", mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        assertTraceIdPresent(result);
        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");
        assertNotNull(data.get("records"), "信封应包含 records 字段");
        assertTrue(data.get("records").isArray(), "records 应为数组");
        assertNotNull(data.get("total"), "信封应包含 total 字段");
        assertNotNull(data.get("page"), "信封应包含 page 字段");
        assertNotNull(data.get("size"), "信封应包含 size 字段");
        assertEquals(1, data.get("page").asInt(), "page 应为 1");
        assertEquals(5, data.get("size").asInt(), "size 应为 5");
    }

    // ===== CT-8: traceId 透传 =====

    @Test
    @DisplayName("CT-8: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)")
    void testListImportExportTasksTraceIdPropagation() throws Exception {
        createTaskAndTrack("IMPORT", "SUCCESS");

        MvcResult result = performGet(API_PATH + "?page=1&size=10", mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        ResultNode node = parseResult(result);
        assertNotNull(node.traceId(), "响应体应包含 traceId");
        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertNotNull(headerTraceId, "响应头应包含 X-Trace-Id");
        assertEquals(node.traceId(), headerTraceId, "响应头 X-Trace-Id 应与响应体 traceId 一致");
    }
}
