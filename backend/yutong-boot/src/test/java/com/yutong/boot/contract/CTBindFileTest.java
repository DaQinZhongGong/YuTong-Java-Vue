package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CT-bindFile 契约测试。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-bindFile 验收标准)
 * <p>契约来源: openapi.yaml POST /api/v1/files/bind
 *              (x-permission: system:file:bind, application/json BindFileRequest)
 *
 * <p>当前实现: FileService.bind 校验文件存在 → 检查重复绑定 → 插入 biz_file_rel 记录。
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功绑定 (admin, 200, code=0, 返回 BizFileRel + biz_file_rel 落库)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (viewer 无 system:file:bind, 403 AUTH-403001)</li>
 *   <li>CT-4 权限拒绝 (biz 无 system:file:bind, 403 AUTH-403001)</li>
 *   <li>CT-5 重复绑定 (同一 tenant+bizType+bizId+fileId, 409 SYS-409004)</li>
 *   <li>CT-6 traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)</li>
 * </ol>
 *
 * <p>权限矩阵: admin (*) 有 system:file:bind; biz 有 system:file:upload 但无 system:file:bind;
 *   viewer/approver 无 system:file:bind。
 *
 * <p>数据隔离: 每个用例上传小文件获取 fileId, @AfterEach 物理清理 biz_file_rel + sys_file。
 */
@DisplayName("CT-bindFile: POST /api/v1/files/bind 契约测试")
class CTBindFileTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/files/bind";
    private static final String UPLOAD_PATH = "/api/v1/files/upload";
    private static final String ERROR_CODE_FORBIDDEN = "AUTH-403001";
    private static final String ERROR_CODE_BUSINESS_CONFLICT = "SYS-409004";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final java.util.Set<String> createdFileIds = ConcurrentHashMap.newKeySet();
    private final java.util.Set<String> createdRelIds = ConcurrentHashMap.newKeySet();

    @AfterEach
    void cleanupTestData() {
        for (String relId : createdRelIds) {
            jdbcTemplate.update("DELETE FROM biz_file_rel WHERE id = ?", relId);
        }
        for (String fileId : createdFileIds) {
            jdbcTemplate.update("DELETE FROM sys_operation_log WHERE biz_type = 'sys_file' AND biz_id = ?", fileId);
            jdbcTemplate.update("DELETE FROM sys_file WHERE id = ?", fileId);
        }
        createdRelIds.clear();
        createdFileIds.clear();
    }

    /** 上传小文件并跟踪 ID (biz 用户有 system:file:upload)。 */
    private String uploadAndTrack() throws Exception {
        String fileName = "CT-BIND-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase() + ".txt";
        byte[] content = "bind test content".getBytes();
        MvcResult result = performFileUpload(UPLOAD_PATH, content, fileName, "text/plain", mockUser("biz"));
        assertEquals(200, result.getResponse().getStatus(), "上传应成功: " + result.getResponse().getContentAsString());
        JsonNode data = parseResult(result).data();
        String id = data.get("id").asText();
        createdFileIds.add(id);
        return id;
    }

    /** 构造绑定请求体。 */
    private Map<String, Object> buildBindBody(String fileId, String bizId) {
        Map<String, Object> body = new HashMap<>();
        body.put("bizType", "biz_request");
        body.put("bizId", bizId);
        body.put("fileId", fileId);
        body.put("relType", "ATTACHMENT");
        body.put("sortNo", 0);
        return body;
    }

    // ===== CT-1: 成功绑定 =====

    @Test
    @DisplayName("CT-1: 成功绑定文件到业务对象 (admin, 200, code=0, 返回 BizFileRel)")
    void testBindFileSuccess() throws Exception {
        String fileId = uploadAndTrack();
        String bizId = "01CTBIZREQ0000000000000BIND";
        Map<String, Object> body = buildBindBody(fileId, bizId);

        MvcResult result = performPost(API_PATH, body, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");
        assertNotNull(data.get("id"), "返回应包含 id");
        assertEquals(fileId, data.get("fileId").asText(), "返回 fileId 应与请求一致");
        assertEquals("biz_request", data.get("bizType").asText(), "返回 bizType 应为 biz_request");
        createdRelIds.add(data.get("id").asText());
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testBindFileUnauthenticated() {
        // Mock 模式限制
    }

    // ===== CT-3: 权限拒绝 (viewer) =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (viewer 无 system:file:bind, 403 AUTH-403001)")
    void testBindFileForbiddenViewer() throws Exception {
        String fileId = uploadAndTrack();
        Map<String, Object> body = buildBindBody(fileId, "01CTBIZREQ0000000000000VFB");

        MvcResult result = performPost(API_PATH, body, mockUser("viewer"));

        assertEquals(403, result.getResponse().getStatus(), "viewer 无绑定权限, HTTP 应为 403");
        assertError(result, ERROR_CODE_FORBIDDEN);
        assertTraceIdPresent(result);
    }

    // ===== CT-4: 权限拒绝 (biz) =====

    @Test
    @DisplayName("CT-4: 权限拒绝 (biz 无 system:file:bind, 403 AUTH-403001)")
    void testBindFileForbiddenBiz() throws Exception {
        String fileId = uploadAndTrack();
        Map<String, Object> body = buildBindBody(fileId, "01CTBIZREQ0000000000000BFB");

        MvcResult result = performPost(API_PATH, body, mockUser("biz"));

        assertEquals(403, result.getResponse().getStatus(), "biz 无绑定权限, HTTP 应为 403");
        assertError(result, ERROR_CODE_FORBIDDEN);
        assertTraceIdPresent(result);
    }

    // ===== CT-5: 重复绑定 =====

    @Test
    @DisplayName("CT-5: 重复绑定 (同一 tenant+bizType+bizId+fileId, 409 SYS-409004)")
    void testBindFileDuplicate() throws Exception {
        String fileId = uploadAndTrack();
        String bizId = "01CTBIZREQ0000000000000DUP";
        Map<String, Object> body = buildBindBody(fileId, bizId);

        // 第一次绑定: 成功
        MvcResult firstResult = performPost(API_PATH, body, mockUser("admin"));
        assertEquals(200, firstResult.getResponse().getStatus(), "第一次绑定应成功");
        assertSuccess(firstResult);
        createdRelIds.add(parseResult(firstResult).data().get("id").asText());

        // 第二次绑定: 重复 → 业务冲突
        MvcResult secondResult = performPost(API_PATH, body, mockUser("admin"));
        assertEquals(409, secondResult.getResponse().getStatus(), "重复绑定应返回 409");
        assertError(secondResult, ERROR_CODE_BUSINESS_CONFLICT);
        assertTraceIdPresent(secondResult);
    }

    // ===== CT-6: traceId 透传 =====

    @Test
    @DisplayName("CT-6: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)")
    void testBindFileTraceIdPropagation() throws Exception {
        String fileId = uploadAndTrack();
        Map<String, Object> body = buildBindBody(fileId, "01CTBIZREQ0000000000000TRC");

        MvcResult result = performPost(API_PATH, body, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        ResultNode node = parseResult(result);
        assertNotNull(node.traceId(), "响应体应包含 traceId");
        assertTrue(node.traceId().length() >= 16 && node.traceId().length() <= 64,
                "traceId 长度应在 16-64 之间");

        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertNotNull(headerTraceId, "响应头应包含 X-Trace-Id");
        assertEquals(node.traceId(), headerTraceId, "响应头 X-Trace-Id 应与响应体 traceId 一致");

        createdRelIds.add(node.data().get("id").asText());
    }
}
