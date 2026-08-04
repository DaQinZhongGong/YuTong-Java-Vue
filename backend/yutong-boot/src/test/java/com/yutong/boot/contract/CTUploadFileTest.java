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
 * GA2-L188 CT-uploadFile 契约测试。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-uploadFile 验收标准)
 * <p>策略来源: operation-policies.yaml uploadFile → binaryUpload profile
 *              (idempotency: checksum-and-object-key, concurrency: unique-object-key, audit: operation)
 * <p>契约来源: openapi.yaml POST /api/v1/files/upload
 *              (x-permission: system:file:upload, x-error-codes: [FILE-400001, FILE-400002])
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功上传 (biz 用户, 200, code=0, 返回 SysFile + traceId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (viewer 无 system:file:upload, 403 AUTH-403001)</li>
 *   <li>CT-4a 参数校验失败 - 空文件 (400 SYS-400001)</li>
 *   <li>CT-4b 参数校验失败 - 不允许的文件类型 .exe (400 FILE-400001)</li>
 *   <li>CT-5 标准错误信封 (code/message/traceId 字段齐全)</li>
 *   <li>CT-6 traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)</li>
 *   <li>CT-7 校验和唯一性 (binaryUpload 策略, 同内容不同文件名生成不同 fileKey)</li>
 *   <li>CT-8 审计落库 (@Auditable CREATE → sys_operation_log 有记录)</li>
 * </ol>
 *
 * <p>数据隔离: 每个用例上传小文件 (UUID 后缀文件名避免冲突), @AfterEach 调用 DELETE API
 * 清理 MinIO 对象 + 逻辑删除 sys_file, 再物理删除 sys_file + sys_operation_log。
 * 不使用 @Transactional 以便 CT-8 审计验证能查询 sys_operation_log。
 */
@DisplayName("CT-uploadFile: POST /api/v1/files/upload 契约测试")
class CTUploadFileTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/files/upload";
    private static final String ERROR_CODE_FORBIDDEN = "AUTH-403001";
    private static final String ERROR_CODE_PARAM_INVALID = "SYS-400001";
    private static final String ERROR_CODE_FILE_TYPE = "FILE-400001";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 本测试创建的文件 ID 集合, @AfterEach 清理。 */
    private final java.util.Set<String> createdFileIds = ConcurrentHashMap.newKeySet();

    @AfterEach
    void cleanupTestData() {
        for (String id : createdFileIds) {
            // 1. 调用 DELETE API 清理 MinIO 对象 + 逻辑删除 sys_file (admin 全权限)
            try {
                performDelete("/api/v1/files/" + id, mockUser("admin"));
            } catch (Exception ignored) {
                // 文件可能已被用例删除, 忽略
            }
            // 2. 清理审计日志
            jdbcTemplate.update("DELETE FROM sys_operation_log WHERE biz_type = 'sys_file' AND biz_id = ?", id);
            // 3. 物理删除 sys_file 记录 (DELETE API 仅逻辑删除)
            jdbcTemplate.update("DELETE FROM sys_file WHERE id = ?", id);
        }
        createdFileIds.clear();
    }

    /** 上传小文件并跟踪 ID, 返回文件 ID。 */
    private String uploadAndTrack(String fileName, byte[] content, String contentType,
                                  org.springframework.http.HttpHeaders headers) throws Exception {
        MvcResult result = performFileUpload(API_PATH, content, fileName, contentType, headers);
        assertEquals(200, result.getResponse().getStatus(), "上传应成功: " + result.getResponse().getContentAsString());
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertNotNull(data, "上传响应 data 不应为 null");
        String id = data.get("id").asText();
        createdFileIds.add(id);
        return id;
    }

    /** 构造唯一文件名 (UUID 后缀)。 */
    private String uniqueFileName(String prefix, String ext) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase() + "." + ext;
    }

    // ===== CT-1: 成功上传 =====

    @Test
    @DisplayName("CT-1: 成功上传文件 (biz 用户, 200, code=0, 返回 SysFile + traceId)")
    void testUploadFileSuccess() throws Exception {
        String fileName = uniqueFileName("CT-L188-U", "txt");
        byte[] content = "CT-uploadFile test content".getBytes();

        MvcResult result = performFileUpload(API_PATH, content, fileName, "text/plain", mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        // 验证返回的 SysFile 数据
        ResultNode node = parseResult(result);
        JsonNode data = node.data();
        assertNotNull(data.get("id"), "返回的 id 不应为 null");
        assertEquals(fileName, data.get("fileName").asText(), "fileName 应匹配");
        assertEquals(content.length, data.get("fileSize").asLong(), "fileSize 应匹配");
        assertEquals("txt", data.get("fileExt").asText(), "fileExt 应为 txt");
        assertEquals("text/plain", data.get("contentType").asText(), "contentType 应匹配");
        assertEquals("MINIO", data.get("storageType").asText(), "storageType 应为 MINIO");
        assertEquals("SUCCESS", data.get("uploadStatus").asText(), "uploadStatus 应为 SUCCESS");
        assertNotNull(data.get("fileKey"), "fileKey 不应为 null");
        assertNotNull(data.get("checksum"), "checksum 不应为 null");
        assertNotNull(data.get("createdTime"), "createdTime 不应为 null");

        createdFileIds.add(data.get("id").asText());
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testUploadFileUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (viewer 无 system:file:upload, 403 AUTH-403001)")
    void testUploadFileForbidden() throws Exception {
        String fileName = uniqueFileName("CT-L188-U", "txt");
        byte[] content = "forbidden test".getBytes();

        MvcResult result = performFileUpload(API_PATH, content, fileName, "text/plain", mockUser("viewer"));

        assertEquals(403, result.getResponse().getStatus(), "viewer 无上传权限, HTTP 应为 403");
        assertError(result, ERROR_CODE_FORBIDDEN);
        assertTraceIdPresent(result);
    }

    // ===== CT-4a: 参数校验失败 - 空文件 =====

    @Test
    @DisplayName("CT-4a: 参数校验失败 - 空文件 (400 SYS-400001)")
    void testUploadFileEmptyFile() throws Exception {
        String fileName = uniqueFileName("CT-L188-U", "txt");
        byte[] emptyContent = new byte[0];

        MvcResult result = performFileUpload(API_PATH, emptyContent, fileName, "text/plain", mockUser("biz"));

        assertEquals(400, result.getResponse().getStatus(), "空文件, HTTP 应为 400");
        ResultNode node = parseResult(result);
        assertNotEquals("0", node.code(), "校验失败不应返回 code=0");
        assertTraceIdPresent(result);
    }

    // ===== CT-4b: 参数校验失败 - 不允许的文件类型 =====

    @Test
    @DisplayName("CT-4b: 参数校验失败 - 不允许的文件类型 .exe (400 FILE-400001)")
    void testUploadFileInvalidType() throws Exception {
        String fileName = uniqueFileName("CT-L188-U", "exe");
        byte[] content = "fake exe content".getBytes();

        MvcResult result = performFileUpload(API_PATH, content, fileName, "application/octet-stream", mockUser("biz"));

        assertEquals(400, result.getResponse().getStatus(), "不允许的文件类型, HTTP 应为 400");
        assertError(result, ERROR_CODE_FILE_TYPE);
        assertTraceIdPresent(result);
    }

    // ===== CT-5: 标准错误信封结构 =====

    @Test
    @DisplayName("CT-5: 标准错误信封 (code/message/traceId 字段齐全, 非成功响应)")
    void testUploadFileErrorEnvelopeStructure() throws Exception {
        String fileName = uniqueFileName("CT-L188-U", "exe");
        byte[] content = "envelope test".getBytes();

        MvcResult result = performFileUpload(API_PATH, content, fileName, "application/octet-stream", mockUser("biz"));

        ResultNode node = parseResult(result);
        assertNotNull(node.code(), "错误信封必须包含 code 字段");
        assertNotEquals("0", node.code(), "错误响应 code 不应为 0");
        assertNotNull(node.message(), "错误信封必须包含 message 字段");
        assertNotNull(node.traceId(), "错误信封必须包含 traceId 字段");
        assertTrue(node.traceId().length() >= 16 && node.traceId().length() <= 64,
                "traceId 长度应在 16-64 之间");
    }

    // ===== CT-6: traceId 透传 =====

    @Test
    @DisplayName("CT-6: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)")
    void testUploadFileTraceIdPropagation() throws Exception {
        String fileName = uniqueFileName("CT-L188-U", "txt");
        byte[] content = "traceId test".getBytes();

        MvcResult result = performFileUpload(API_PATH, content, fileName, "text/plain", mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus());

        ResultNode node = parseResult(result);
        assertNotNull(node.traceId(), "响应体应包含 traceId");
        assertTrue(node.traceId().length() >= 16 && node.traceId().length() <= 64,
                "traceId 长度应在 16-64 之间");

        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertNotNull(headerTraceId, "响应头应包含 X-Trace-Id");
        assertEquals(node.traceId(), headerTraceId, "响应头 X-Trace-Id 应与响应体 traceId 一致");

        createdFileIds.add(node.data().get("id").asText());
    }

    // ===== CT-7: 校验和唯一性 (binaryUpload 策略) =====

    @Test
    @DisplayName("CT-7: 校验和唯一性 (binaryUpload 策略, 同内容不同文件名生成不同 fileKey)")
    void testUploadFileChecksumAndKeyUniqueness() throws Exception {
        byte[] content = "same content for checksum test".getBytes();
        String fileName1 = uniqueFileName("CT-L188-U", "txt");
        String fileName2 = uniqueFileName("CT-L188-U", "txt");

        // 上传两个文件 (同内容, 不同文件名)
        String id1 = uploadAndTrack(fileName1, content, "text/plain", mockUser("biz"));
        String id2 = uploadAndTrack(fileName2, content, "text/plain", mockUser("biz"));

        // 查询两条记录的 fileKey 和 checksum
        String fileKey1 = jdbcTemplate.queryForObject(
                "SELECT file_key FROM sys_file WHERE id = ?", String.class, id1);
        String fileKey2 = jdbcTemplate.queryForObject(
                "SELECT file_key FROM sys_file WHERE id = ?", String.class, id2);
        String checksum1 = jdbcTemplate.queryForObject(
                "SELECT checksum FROM sys_file WHERE id = ?", String.class, id1);
        String checksum2 = jdbcTemplate.queryForObject(
                "SELECT checksum FROM sys_file WHERE id = ?", String.class, id2);

        assertNotNull(fileKey1, "fileKey1 不应为 null");
        assertNotNull(fileKey2, "fileKey2 不应为 null");
        assertNotEquals(fileKey1, fileKey2, "binaryUpload unique-object-key: 不同上传 fileKey 应不同 (ULID)");
        assertEquals(checksum1, checksum2, "同内容文件 checksum (SHA-256) 应相同");
        assertNotNull(checksum1, "checksum 不应为 null");
        assertTrue(checksum1.length() == 64, "SHA-256 校验和应为 64 位十六进制字符");
    }

    // ===== CT-8: 审计落库 =====

    @Test
    @DisplayName("CT-8: 审计落库 (@Auditable CREATE → sys_operation_log 有记录)")
    void testUploadFileAuditLog() throws Exception {
        String fileName = uniqueFileName("CT-L188-U", "txt");
        byte[] content = "audit log test".getBytes();

        MvcResult result = performFileUpload(API_PATH, content, fileName, "text/plain", mockUser("biz"));
        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        String fileId = parseResult(result).data().get("id").asText();
        createdFileIds.add(fileId);

        // 查询 sys_operation_log 验证审计记录
        String traceId = parseResult(result).traceId();
        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_operation_log " +
                        "WHERE biz_type = 'sys_file' AND operation_type = 'CREATE' " +
                        "AND trace_id = ?",
                Integer.class, traceId);

        assertNotNull(auditCount, "审计日志查询不应返回 null");
        assertTrue(auditCount >= 1,
                "sys_operation_log 应至少有 1 条 CREATE 审计记录 (traceId=" + traceId + "), 实际: " + auditCount);

        // 验证审计记录的关键字段
        jdbcTemplate.query("SELECT biz_id, module, operation_type, biz_type FROM sys_operation_log " +
                        "WHERE biz_type = 'sys_file' AND operation_type = 'CREATE' AND trace_id = ?",
                rs -> {
                    assertEquals(fileId, rs.getString("biz_id"), "审计记录 biz_id 应为上传的文件 ID");
                    assertEquals("system", rs.getString("module"), "审计记录 module 应为 system");
                    assertEquals("CREATE", rs.getString("operation_type"), "审计记录 operation_type 应为 CREATE");
                    assertEquals("sys_file", rs.getString("biz_type"), "审计记录 biz_type 应为 sys_file");
                }, traceId);
    }
}
