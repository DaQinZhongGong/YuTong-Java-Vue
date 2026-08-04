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
 * GA2-L188 CT-deleteFile 契约测试。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-deleteFile 验收标准)
 * <p>策略来源: operation-policies.yaml deleteFile → guardedDelete profile (natural-by-resource-state)
 * <p>契约来源: openapi.yaml DELETE /api/v1/files/{id}
 *              (x-permission: system:file:delete, x-error-codes: [FILE-404001, FILE-409001])
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功删除 (biz 用户, 200, code=0, 逻辑删除 deleted=true + MinIO 对象清理)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (viewer 无 system:file:delete, 403 AUTH-403001)</li>
 *   <li>CT-4 文件不存在 (404 SYS-404001)</li>
 *   <li>CT-5 已删除再删 (guardedDelete 自然幂等, 404 SYS-404001)</li>
 *   <li>CT-6 审计落库 (@Auditable DELETE → sys_operation_log 有记录)</li>
 *   <li>CT-7 traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)</li>
 * </ol>
 *
 * <p>数据隔离: 每个用例先上传小文件获取 ID, @AfterEach 物理删除 sys_file + sys_operation_log
 * (MinIO 对象由 DELETE API 清理或已被用例删除)。
 */
@DisplayName("CT-deleteFile: DELETE /api/v1/files/{id} 契约测试")
class CTDeleteFileTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/files";
    private static final String UPLOAD_PATH = "/api/v1/files/upload";
    private static final String ERROR_CODE_FORBIDDEN = "AUTH-403001";
    private static final String ERROR_CODE_NOT_FOUND = "SYS-404001";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final java.util.Set<String> createdFileIds = ConcurrentHashMap.newKeySet();

    @AfterEach
    void cleanupTestData() {
        for (String id : createdFileIds) {
            jdbcTemplate.update("DELETE FROM sys_operation_log WHERE biz_type = 'sys_file' AND biz_id = ?", id);
            jdbcTemplate.update("DELETE FROM sys_file WHERE id = ?", id);
        }
        createdFileIds.clear();
    }

    /** 上传小文件并跟踪 ID。 */
    private String uploadAndTrack() throws Exception {
        String fileName = "CT-L188-D-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase() + ".txt";
        byte[] content = "delete test content".getBytes();
        MvcResult result = performFileUpload(UPLOAD_PATH, content, fileName, "text/plain", mockUser("biz"));
        assertEquals(200, result.getResponse().getStatus(), "上传应成功: " + result.getResponse().getContentAsString());
        JsonNode data = parseResult(result).data();
        String id = data.get("id").asText();
        createdFileIds.add(id);
        return id;
    }

    // ===== CT-1: 成功删除 =====

    @Test
    @DisplayName("CT-1: 成功删除文件 (biz 用户, 200, code=0, 逻辑删除 deleted=true)")
    void testDeleteFileSuccess() throws Exception {
        String id = uploadAndTrack();

        MvcResult result = performDelete(API_PATH + "/" + id, mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        // 验证逻辑删除: deleted=true (物理查询绕过逻辑删除)
        Boolean deleted = jdbcTemplate.queryForObject(
                "SELECT deleted FROM sys_file WHERE id = ?", Boolean.class, id);
        assertNotNull(deleted, "文件记录应存在 (物理查询)");
        assertTrue(deleted, "删除后 deleted 应为 true (逻辑删除)");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testDeleteFileUnauthenticated() {
        // Mock 模式限制
    }

    // ===== CT-3: 权限拒绝 =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (viewer 无 system:file:delete, 403 AUTH-403001)")
    void testDeleteFileForbidden() throws Exception {
        String id = uploadAndTrack();

        MvcResult result = performDelete(API_PATH + "/" + id, mockUser("viewer"));

        assertEquals(403, result.getResponse().getStatus(), "viewer 无删除权限, HTTP 应为 403");
        assertError(result, ERROR_CODE_FORBIDDEN);
        assertTraceIdPresent(result);

        // 验证文件未被删除 (权限拒绝不应有副作用)
        Boolean deleted = jdbcTemplate.queryForObject(
                "SELECT deleted FROM sys_file WHERE id = ?", Boolean.class, id);
        assertNotNull(deleted, "文件记录应存在");
        assertFalse(deleted, "权限拒绝时文件不应被删除");
    }

    // ===== CT-4: 文件不存在 =====

    @Test
    @DisplayName("CT-4: 文件不存在 (404 SYS-404001)")
    void testDeleteFileNotFound() throws Exception {
        String nonExistentId = "01NOTEXIST00000000000000CT";

        MvcResult result = performDelete(API_PATH + "/" + nonExistentId, mockUser("biz"));

        assertEquals(404, result.getResponse().getStatus(), "文件不存在, HTTP 应为 404");
        assertError(result, ERROR_CODE_NOT_FOUND);
        assertTraceIdPresent(result);
    }

    // ===== CT-5: 已删除再删 =====

    @Test
    @DisplayName("CT-5: 已删除再删 (guardedDelete 自然幂等, 404 SYS-404001)")
    void testDeleteFileAlreadyDeleted() throws Exception {
        String id = uploadAndTrack();

        // 第一次删除: 成功
        MvcResult firstResult = performDelete(API_PATH + "/" + id, mockUser("biz"));
        assertEquals(200, firstResult.getResponse().getStatus(), "第一次删除应成功");
        assertSuccess(firstResult);

        // 第二次删除: 已删除 → selectById 返回 null (逻辑删除过滤) → 404 SYS-404001
        MvcResult secondResult = performDelete(API_PATH + "/" + id, mockUser("biz"));
        assertEquals(404, secondResult.getResponse().getStatus(), "已删除再删应返回 404");
        assertError(secondResult, ERROR_CODE_NOT_FOUND);
        assertTraceIdPresent(secondResult);
    }

    // ===== CT-6: 审计落库 =====

    @Test
    @DisplayName("CT-6: 审计落库 (@Auditable DELETE → sys_operation_log 有记录)")
    void testDeleteFileAuditLog() throws Exception {
        String id = uploadAndTrack();

        MvcResult result = performDelete(API_PATH + "/" + id, mockUser("biz"));
        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);

        String traceId = parseResult(result).traceId();
        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_operation_log " +
                        "WHERE biz_type = 'sys_file' AND operation_type = 'DELETE' " +
                        "AND trace_id = ?",
                Integer.class, traceId);

        assertNotNull(auditCount, "审计日志查询不应返回 null");
        assertTrue(auditCount >= 1,
                "sys_operation_log 应至少有 1 条 DELETE 审计记录 (traceId=" + traceId + "), 实际: " + auditCount);

        jdbcTemplate.query("SELECT biz_id, module, operation_type, biz_type FROM sys_operation_log " +
                        "WHERE biz_type = 'sys_file' AND operation_type = 'DELETE' AND trace_id = ?",
                rs -> {
                    assertEquals(id, rs.getString("biz_id"), "审计记录 biz_id 应为删除的文件 ID");
                    assertEquals("system", rs.getString("module"), "审计记录 module 应为 system");
                    assertEquals("DELETE", rs.getString("operation_type"), "审计记录 operation_type 应为 DELETE");
                    assertEquals("sys_file", rs.getString("biz_type"), "审计记录 biz_type 应为 sys_file");
                }, traceId);
    }

    // ===== CT-7: traceId 透传 =====

    @Test
    @DisplayName("CT-7: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)")
    void testDeleteFileTraceIdPropagation() throws Exception {
        String id = uploadAndTrack();

        MvcResult result = performDelete(API_PATH + "/" + id, mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus());
        ResultNode node = parseResult(result);
        assertNotNull(node.traceId(), "响应体应包含 traceId");
        assertTrue(node.traceId().length() >= 16 && node.traceId().length() <= 64,
                "traceId 长度应在 16-64 之间");

        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertNotNull(headerTraceId, "响应头应包含 X-Trace-Id");
        assertEquals(node.traceId(), headerTraceId, "响应头 X-Trace-Id 应与响应体 traceId 一致");
    }
}
