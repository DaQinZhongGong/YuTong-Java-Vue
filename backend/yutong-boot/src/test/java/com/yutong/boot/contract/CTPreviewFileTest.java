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
 * GA2-L188 CT-previewFile 契约测试 (GET /api/v1/files/{id} 文件详情)。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-previewFile 验收标准)
 * <p>策略来源: operation-policies.yaml previewFile (read profile, queryOne)
 * <p>契约来源: openapi.yaml GET /api/v1/files/{id}/preview
 *              (x-permissions-any-of: ["system:file:preview", "mobile:file:preview"])
 *
 * <p>实现偏差说明 (DEV-L188-002): openapi.yaml 定义 GET /files/{id}/preview,
 * 但实际实现为 GET /files/{id} (无 /preview 后缀), operationId=previewFile。
 * 本测试验证实际实现的行为 (GET /{id} 返回 SysFile 元数据)。
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功查询 (biz 用户, 200, code=0, 返回 SysFile + traceId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (approver 无 system:file:preview, 403 AUTH-403001)</li>
 *   <li>CT-4 文件不存在 (404 SYS-404001)</li>
 *   <li>CT-5 已删除文件 (selectById 过滤逻辑删除, 404 SYS-404001)</li>
 *   <li>CT-6 字段完整性 (id/fileName/fileKey/fileSize/contentType/fileExt/storageType/checksum/uploadStatus 全返回)</li>
 *   <li>CT-7 viewer 可查询 (有 system:file:preview, 200 返回详情)</li>
 *   <li>CT-8 traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)</li>
 * </ol>
 *
 * <p>数据隔离: 每个用例先上传小文件获取 ID, @AfterEach 调用 DELETE API
 * 清理 MinIO 对象 + 逻辑删除 sys_file, 再物理删除 sys_file + sys_operation_log。
 */
@DisplayName("CT-previewFile: GET /api/v1/files/{id} 契约测试")
class CTPreviewFileTest extends AbstractContractTest {

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
            try {
                performDelete("/api/v1/files/" + id, mockUser("admin"));
            } catch (Exception ignored) {
                // 文件可能已被用例删除
            }
            jdbcTemplate.update("DELETE FROM sys_operation_log WHERE biz_type = 'sys_file' AND biz_id = ?", id);
            jdbcTemplate.update("DELETE FROM sys_file WHERE id = ?", id);
        }
        createdFileIds.clear();
    }

    /** 上传小文件并跟踪 ID。 */
    private String uploadAndTrack() throws Exception {
        String fileName = "CT-L188-P-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase() + ".txt";
        byte[] content = "preview test content".getBytes();
        MvcResult result = performFileUpload(UPLOAD_PATH, content, fileName, "text/plain", mockUser("biz"));
        assertEquals(200, result.getResponse().getStatus(), "上传应成功: " + result.getResponse().getContentAsString());
        JsonNode data = parseResult(result).data();
        String id = data.get("id").asText();
        createdFileIds.add(id);
        return id;
    }

    // ===== CT-1: 成功查询 =====

    @Test
    @DisplayName("CT-1: 成功查询文件详情 (biz 用户, 200, code=0, 返回 SysFile + traceId)")
    void testPreviewFileSuccess() throws Exception {
        String id = uploadAndTrack();

        MvcResult result = performGet(API_PATH + "/" + id, mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        ResultNode node = parseResult(result);
        JsonNode data = node.data();
        assertNotNull(data, "data 不应为 null");
        assertEquals(id, data.get("id").asText(), "返回的 id 应匹配");
        assertNotNull(data.get("fileName"), "fileName 不应为 null");
        assertNotNull(data.get("fileKey"), "fileKey 不应为 null");
        assertNotNull(data.get("fileSize"), "fileSize 不应为 null");
        assertNotNull(data.get("contentType"), "contentType 不应为 null");
        assertNotNull(data.get("storageType"), "storageType 不应为 null");
        assertEquals("SUCCESS", data.get("uploadStatus").asText(), "uploadStatus 应为 SUCCESS");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testPreviewFileUnauthenticated() {
        // Mock 模式限制
    }

    // ===== CT-3: 权限拒绝 =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (approver 无 system:file:preview, 403 AUTH-403001)")
    void testPreviewFileForbidden() throws Exception {
        String id = uploadAndTrack();

        MvcResult result = performGet(API_PATH + "/" + id, mockUser("approver"));

        assertEquals(403, result.getResponse().getStatus(), "approver 无预览权限, HTTP 应为 403");
        assertError(result, ERROR_CODE_FORBIDDEN);
        assertTraceIdPresent(result);
    }

    // ===== CT-4: 文件不存在 =====

    @Test
    @DisplayName("CT-4: 文件不存在 (404 SYS-404001)")
    void testPreviewFileNotFound() throws Exception {
        String nonExistentId = "01NOTEXIST00000000000000CT";

        MvcResult result = performGet(API_PATH + "/" + nonExistentId, mockUser("biz"));

        assertEquals(404, result.getResponse().getStatus(), "文件不存在, HTTP 应为 404");
        assertError(result, ERROR_CODE_NOT_FOUND);
        assertTraceIdPresent(result);
    }

    // ===== CT-5: 已删除文件 =====

    @Test
    @DisplayName("CT-5: 已删除文件 (selectById 过滤逻辑删除, 404 SYS-404001)")
    void testPreviewFileDeleted() throws Exception {
        String id = uploadAndTrack();

        // 先删除文件
        MvcResult deleteResult = performDelete(API_PATH + "/" + id, mockUser("biz"));
        assertEquals(200, deleteResult.getResponse().getStatus(), "删除应成功");

        // 再查询: 逻辑删除过滤 → 404
        MvcResult result = performGet(API_PATH + "/" + id, mockUser("biz"));
        assertEquals(404, result.getResponse().getStatus(), "已删除文件查询应返回 404");
        assertError(result, ERROR_CODE_NOT_FOUND);
        assertTraceIdPresent(result);
    }

    // ===== CT-6: 字段完整性 =====

    @Test
    @DisplayName("CT-6: 字段完整性 (id/fileName/fileKey/fileSize/contentType/fileExt/storageType/checksum/uploadStatus 全返回)")
    void testPreviewFileFieldCompleteness() throws Exception {
        String fileName = "CT-L188-P-FIELD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase() + ".txt";
        byte[] content = "field completeness test".getBytes();
        MvcResult uploadResult = performFileUpload(UPLOAD_PATH, content, fileName, "text/plain", mockUser("biz"));
        assertEquals(200, uploadResult.getResponse().getStatus());
        String id = parseResult(uploadResult).data().get("id").asText();
        createdFileIds.add(id);

        MvcResult result = performGet(API_PATH + "/" + id, mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertEquals(id, data.get("id").asText(), "id 应匹配");
        assertEquals(fileName, data.get("fileName").asText(), "fileName 应匹配");
        assertNotNull(data.get("fileKey").asText(), "fileKey 不应为 null");
        assertEquals(content.length, data.get("fileSize").asLong(), "fileSize 应匹配");
        assertEquals("text/plain", data.get("contentType").asText(), "contentType 应匹配");
        assertEquals("txt", data.get("fileExt").asText(), "fileExt 应为 txt");
        assertEquals("MINIO", data.get("storageType").asText(), "storageType 应为 MINIO");
        assertNotNull(data.get("checksum"), "checksum 不应为 null");
        assertEquals("SUCCESS", data.get("uploadStatus").asText(), "uploadStatus 应为 SUCCESS");
        assertNotNull(data.get("createdTime"), "createdTime 不应为 null");
    }

    // ===== CT-7: viewer 可查询 =====

    @Test
    @DisplayName("CT-7: viewer 可查询 (有 system:file:preview, 200 返回详情)")
    void testPreviewFileViewerCanQuery() throws Exception {
        String id = uploadAndTrack();

        MvcResult result = performGet(API_PATH + "/" + id, mockUser("viewer"));

        assertEquals(200, result.getResponse().getStatus(), "viewer 有预览权限, HTTP 应为 200");
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertEquals(id, data.get("id").asText(), "viewer 查询应返回正确的文件 id");
    }

    // ===== CT-8: traceId 透传 =====

    @Test
    @DisplayName("CT-8: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)")
    void testPreviewFileTraceIdPropagation() throws Exception {
        String id = uploadAndTrack();

        MvcResult result = performGet(API_PATH + "/" + id, mockUser("biz"));

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
