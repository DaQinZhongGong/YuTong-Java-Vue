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
 * CT-getJobLog 契约测试 (operationId: getJobLog)。
 *
 * <p>契约来源: routes.yaml operationIds: [getJobLog],
 * GET /api/v1/job-logs/{id}
 * <p>控制器: JobLogController.get(id) - 无 @RequiresPermission (所有认证用户可访问)
 * <p>服务: JobLogService.getLog - 不存在时抛 ResourceNotFoundException (SYS-404001)
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功查询 (200, code=0, 返回 SysJobLog + traceId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 任务日志不存在 (404 SYS-404001)</li>
 *   <li>CT-4 字段完整性 (id/jobCode/jobName/status/triggerType/startTime/traceId 全返回)</li>
 *   <li>CT-5 traceId 透传 (响应头 + 响应体)</li>
 * </ol>
 *
 * <p>数据隔离: 通过 JdbcTemplate 直接插入 sys_job_log 测试数据, @AfterEach 物理清理。
 */
@DisplayName("CT-getJobLog: GET /api/v1/job-logs/{id} 契约测试")
class CTGetJobLogTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/job-logs";
    private static final String ERROR_CODE_NOT_FOUND = "SYS-404001";
    private static final String TENANT_ID = "default";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final java.util.Set<String> createdIds = ConcurrentHashMap.newKeySet();

    @AfterEach
    void cleanupTestData() {
        for (String id : createdIds) {
            jdbcTemplate.update("DELETE FROM sys_job_log WHERE id = ?", id);
        }
        createdIds.clear();
    }

    /** 通过 JdbcTemplate 插入任务日志测试数据, 返回生成的 id。 */
    private String createLogAndTrack() {
        String id = "CTJOBLOG" + UUID.randomUUID().toString().replace("-", "").substring(0, 19);
        jdbcTemplate.update(
                "INSERT INTO sys_job_log (id, tenant_id, job_code, job_name, biz_type, biz_id, " +
                        "trigger_type, status, start_time, end_time, duration_ms, error_message, trace_id, " +
                        "created_by, created_time, updated_by, updated_time, deleted, version) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, now(), now(), 200, NULL, ?, ?, now(), ?, now(), false, 0)",
                id, TENANT_ID, "CT-JOB-DETAIL", "CT任务日志详情测试", "sample", "ct-biz-detail",
                "CRON", "SUCCESS", "ct-trace-" + UUID.randomUUID().toString().substring(0, 16),
                "ct-job-user", "ct-job-user");
        createdIds.add(id);
        return id;
    }

    // ===== CT-1: 成功查询 =====

    @Test
    @DisplayName("CT-1: 成功查询任务日志详情 (admin 用户, 200, code=0, 返回 SysJobLog + traceId)")
    void testGetJobLogSuccess() throws Exception {
        String id = createLogAndTrack();

        MvcResult result = performGet(API_PATH + "/" + id, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertEquals(id, data.get("id").asText(), "返回 id 应与请求 id 一致");
        assertEquals("CT-JOB-DETAIL", data.get("jobCode").asText());
        assertEquals("SUCCESS", data.get("status").asText());
        assertEquals("CRON", data.get("triggerType").asText());
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testGetJobLogUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 任务日志不存在 =====

    @Test
    @DisplayName("CT-3: 任务日志不存在 (404 SYS-404001)")
    void testGetJobLogNotFound() throws Exception {
        String nonExistentId = "NOSUCHJOB" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        MvcResult result = performGet(API_PATH + "/" + nonExistentId, mockUser("admin"));

        assertEquals(404, result.getResponse().getStatus(), "任务日志不存在, HTTP 应为 404");
        assertError(result, ERROR_CODE_NOT_FOUND);
        assertTraceIdPresent(result);
    }

    // ===== CT-4: 字段完整性 =====

    @Test
    @DisplayName("CT-4: 字段完整性 (id/jobCode/jobName/status/triggerType/startTime/traceId 全返回)")
    void testGetJobLogFieldCompleteness() throws Exception {
        String id = createLogAndTrack();

        MvcResult result = performGet(API_PATH + "/" + id, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertNotNull(data.get("id"), "应包含 id 字段");
        assertNotNull(data.get("jobCode"), "应包含 jobCode 字段");
        assertNotNull(data.get("jobName"), "应包含 jobName 字段");
        assertNotNull(data.get("status"), "应包含 status 字段");
        assertNotNull(data.get("triggerType"), "应包含 triggerType 字段");
        assertNotNull(data.get("startTime"), "应包含 startTime 字段");
        assertNotNull(data.get("traceId"), "应包含 traceId 字段");
        assertNotNull(data.get("durationMs"), "应包含 durationMs 字段");
    }

    // ===== CT-5: traceId 透传 =====

    @Test
    @DisplayName("CT-5: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)")
    void testGetJobLogTraceIdPropagation() throws Exception {
        String id = createLogAndTrack();

        MvcResult result = performGet(API_PATH + "/" + id, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        ResultNode node = parseResult(result);
        assertNotNull(node.traceId(), "响应体应包含 traceId");
        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertNotNull(headerTraceId, "响应头应包含 X-Trace-Id");
        assertEquals(node.traceId(), headerTraceId, "响应头 X-Trace-Id 应与响应体 traceId 一致");
    }
}
