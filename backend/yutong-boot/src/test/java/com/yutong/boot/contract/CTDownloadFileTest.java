package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GA2-L188 CT-downloadFile 契约测试。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-downloadFile 验收标准)
 * <p>策略来源: operation-policies.yaml downloadFile (read profile, 无独立 profile 定义)
 * <p>契约来源: openapi.yaml GET /api/v1/files/{id}/download
 *              (x-permission: system:file:download, x-error-codes: [FILE-404001])
 *
 * <p>实现偏差说明 (DEV-L188-001): openapi.yaml 定义 GET /files/{id}/download 返回二进制流,
 * 但实际实现为 GET /files/{id}/download-url 返回 Result&lt;String&gt; (MinIO 预签名 URL, 30 分钟有效)。
 * 这是安全设计决策 (不流式传输二进制经过后端), 本测试验证实际实现的行为。
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功获取下载链接 (biz 用户, 200, code=0, 返回预签名 URL + traceId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (approver 无 system:file:download, 403 AUTH-403001)</li>
 *   <li>CT-4 文件不存在 (404 SYS-404001)</li>
 *   <li>CT-5 标准错误信封 (code/message/traceId 字段齐全)</li>
 *   <li>CT-6 traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)</li>
 *   <li>CT-7 viewer 可获取下载链接 (有 system:file:download, 200)</li>
 * </ol>
 *
 * <p>数据隔离: 每个用例先上传小文件获取 ID, @AfterEach 调用 DELETE API 清理 MinIO 对象 +
 * 逻辑删除 sys_file, 再物理删除 sys_file + sys_operation_log。
 */
@DisplayName("CT-downloadFile: GET /api/v1/files/{id}/download-url 契约测试")
class CTDownloadFileTest extends AbstractContractTest {

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

    /** 上传小文件并跟踪 ID, 返回文件 ID。 */
    private String uploadAndTrack() throws Exception {
        String fileName = "CT-L188-DL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase() + ".txt";
        byte[] content = "download test content".getBytes();
        MvcResult result = performFileUpload(UPLOAD_PATH, content, fileName, "text/plain", mockUser("biz"));
        assertEquals(200, result.getResponse().getStatus(), "上传应成功: " + result.getResponse().getContentAsString());
        JsonNode data = parseResult(result).data();
        String id = data.get("id").asText();
        createdFileIds.add(id);
        return id;
    }

    // ===== CT-1: 成功获取下载链接 =====

    @Test
    @DisplayName("CT-1: 成功获取下载链接 (biz 用户, 200, code=0, 返回预签名 URL + traceId)")
    void testDownloadFileSuccess() throws Exception {
        String fileId = uploadAndTrack();

        MvcResult result = performGet("/api/v1/files/" + fileId + "/download-url", mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        // 验证返回的预签名 URL
        ResultNode node = parseResult(result);
        JsonNode data = node.data();
        assertNotNull(data, "data 不应为 null");
        assertTrue(data.isTextual(), "data 应为文本类型 (预签名 URL 字符串)");
        String downloadUrl = data.asText();
        assertTrue(downloadUrl.startsWith("http"), "预签名 URL 应以 http 开头: " + downloadUrl);
        assertTrue(downloadUrl.contains("X-Amz-Signature") || downloadUrl.contains("signature"),
                "预签名 URL 应包含签名参数: " + downloadUrl);
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testDownloadFileUnauthenticated() {
        // Mock 模式限制
    }

    // ===== CT-3: 权限拒绝 =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (approver 无 system:file:download, 403 AUTH-403001)")
    void testDownloadFileForbidden() throws Exception {
        String fileId = uploadAndTrack();

        MvcResult result = performGet("/api/v1/files/" + fileId + "/download-url", mockUser("approver"));

        assertEquals(403, result.getResponse().getStatus(), "approver 无下载权限, HTTP 应为 403");
        assertError(result, ERROR_CODE_FORBIDDEN);
        assertTraceIdPresent(result);
    }

    // ===== CT-4: 文件不存在 =====

    @Test
    @DisplayName("CT-4: 文件不存在 (404 SYS-404001)")
    void testDownloadFileNotFound() throws Exception {
        String nonExistentId = "01NOTEXIST00000000000000CT";

        MvcResult result = performGet("/api/v1/files/" + nonExistentId + "/download-url", mockUser("biz"));

        assertEquals(404, result.getResponse().getStatus(), "文件不存在, HTTP 应为 404");
        assertError(result, ERROR_CODE_NOT_FOUND);
        assertTraceIdPresent(result);
    }

    // ===== CT-5: 标准错误信封结构 =====

    @Test
    @DisplayName("CT-5: 标准错误信封 (code/message/traceId 字段齐全, 非成功响应)")
    void testDownloadFileErrorEnvelopeStructure() throws Exception {
        String nonExistentId = "01NOTEXIST00000000000000CT";

        MvcResult result = performGet("/api/v1/files/" + nonExistentId + "/download-url", mockUser("biz"));

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
    void testDownloadFileTraceIdPropagation() throws Exception {
        String fileId = uploadAndTrack();

        MvcResult result = performGet("/api/v1/files/" + fileId + "/download-url", mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus());
        ResultNode node = parseResult(result);
        assertNotNull(node.traceId(), "响应体应包含 traceId");
        assertTrue(node.traceId().length() >= 16 && node.traceId().length() <= 64,
                "traceId 长度应在 16-64 之间");

        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertNotNull(headerTraceId, "响应头应包含 X-Trace-Id");
        assertEquals(node.traceId(), headerTraceId, "响应头 X-Trace-Id 应与响应体 traceId 一致");
    }

    // ===== CT-7: viewer 可获取下载链接 =====

    @Test
    @DisplayName("CT-7: viewer 可获取下载链接 (有 system:file:download, 200)")
    void testDownloadFileViewerCanDownload() throws Exception {
        String fileId = uploadAndTrack();

        MvcResult result = performGet("/api/v1/files/" + fileId + "/download-url", mockUser("viewer"));

        assertEquals(200, result.getResponse().getStatus(), "viewer 有下载权限, HTTP 应为 200");
        assertSuccess(result);
        ResultNode node = parseResult(result);
        assertTrue(node.data().isTextual(), "viewer 应能获取预签名 URL");
        assertTrue(node.data().asText().startsWith("http"), "预签名 URL 应以 http 开头");
    }
}
