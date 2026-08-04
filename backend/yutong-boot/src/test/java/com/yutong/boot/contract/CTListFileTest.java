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
 * GA2-L188 CT-listFile 契约测试 (operationId: listFiles)。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-listFiles 验收标准)
 * <p>策略来源: operation-policies.yaml listFiles (read profile, queryPage)
 * <p>契约来源: openapi.yaml GET /api/v1/files
 *              (x-permissions-any-of: ["system:file:list", "mobile:upload:list"])
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功分页查询 (biz 用户, 200, code=0, records/total/page/size + traceId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (approver 无 system:file:list, 403 AUTH-403001)</li>
 *   <li>CT-4 参数校验 - size 越界 (0 → 钳制为 20, 200 优雅降级)</li>
 *   <li>CT-5 空结果 (fileName 不匹配, total=0)</li>
 *   <li>CT-6 过滤条件 fileName (唯一文件名精确匹配, total=1)</li>
 *   <li>CT-7 排序 (createdTime DESC, 非递增)</li>
 *   <li>CT-8 数据范围 (viewer TENANT 可见本租户文件)</li>
 *   <li>CT-9 traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)</li>
 * </ol>
 *
 * <p>数据隔离: 每个用例上传小文件 (UUID 后缀文件名), @AfterEach 调用 DELETE API
 * 清理 MinIO 对象 + 逻辑删除 sys_file, 再物理删除 sys_file + sys_operation_log。
 */
@DisplayName("CT-listFile: GET /api/v1/files 契约测试")
class CTListFileTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/files";
    private static final String UPLOAD_PATH = "/api/v1/files/upload";
    private static final String ERROR_CODE_FORBIDDEN = "AUTH-403001";

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

    /** 上传小文件并跟踪 ID, 返回文件名。 */
    private String uploadAndTrack(String namePrefix) throws Exception {
        String fileName = namePrefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase() + ".txt";
        byte[] content = "list test content".getBytes();
        MvcResult result = performFileUpload(UPLOAD_PATH, content, fileName, "text/plain", mockUser("biz"));
        assertEquals(200, result.getResponse().getStatus(), "上传应成功: " + result.getResponse().getContentAsString());
        JsonNode data = parseResult(result).data();
        String id = data.get("id").asText();
        createdFileIds.add(id);
        return fileName;
    }

    // ===== CT-1: 成功分页查询 =====

    @Test
    @DisplayName("CT-1: 成功分页查询 (biz 用户, 200, code=0, records/total/page/size + traceId)")
    void testListFileSuccess() throws Exception {
        String fileName = uploadAndTrack("CT-L188-L");

        MvcResult result = performGet(API_PATH + "?page=1&size=10&fileName=" + fileName, mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        ResultNode node = parseResult(result);
        JsonNode data = node.data();
        assertNotNull(data, "data 不应为 null");
        assertNotNull(data.get("records"), "records 不应为 null");
        assertTrue(data.get("records").isArray(), "records 应为数组");
        assertTrue(data.get("total").asLong() >= 1, "total 应 >= 1 (至少包含刚上传的文件)");
        assertEquals(1, data.get("page").asInt(), "page 应为 1");
        assertEquals(10, data.get("size").asInt(), "size 应为 10");

        // 验证刚上传的文件在结果中
        ArrayNode records = (ArrayNode) data.get("records");
        boolean found = false;
        for (JsonNode rec : records) {
            if (fileName.equals(rec.get("fileName").asText())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "结果中应包含刚上传的文件 (fileName=" + fileName + ")");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testListFileUnauthenticated() {
        // Mock 模式限制
    }

    // ===== CT-3: 权限拒绝 =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (approver 无 system:file:list, 403 AUTH-403001)")
    void testListFileForbidden() throws Exception {
        MvcResult result = performGet(API_PATH + "?page=1&size=10", mockUser("approver"));

        assertEquals(403, result.getResponse().getStatus(), "approver 无列表权限, HTTP 应为 403");
        assertError(result, ERROR_CODE_FORBIDDEN);
        assertTraceIdPresent(result);
    }

    // ===== CT-4: 参数校验 - size 越界 =====

    @Test
    @DisplayName("CT-4: 参数校验 - size=0 越界 (PageRequest 钳制为 20, 200 优雅降级)")
    void testListFileSizeOutOfRange() throws Exception {
        MvcResult result = performGet(API_PATH + "?page=1&size=0", mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus(), "size 越界应优雅降级, HTTP 200");
        assertSuccess(result);
        ResultNode node = parseResult(result);
        // PageRequest: size < 1 → 钳制为 20
        assertEquals(20, node.data().get("size").asInt(), "size=0 应钳制为默认值 20");
    }

    // ===== CT-5: 空结果 =====

    @Test
    @DisplayName("CT-5: 空结果 (fileName 不匹配, total=0, records 空数组)")
    void testListFileEmptyResult() throws Exception {
        String nonExistentName = "NOSUCHNAME-CT-L188-" + UUID.randomUUID().toString().substring(0, 8);

        MvcResult result = performGet(API_PATH + "?page=1&size=10&fileName=" + nonExistentName, mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        ResultNode node = parseResult(result);
        assertEquals(0, node.data().get("total").asLong(), "不匹配的 fileName 应返回 total=0");
        assertTrue(node.data().get("records").isArray(), "records 应为数组");
        assertEquals(0, node.data().get("records").size(), "records 应为空数组");
    }

    // ===== CT-6: 过滤条件 fileName =====

    @Test
    @DisplayName("CT-6: 过滤条件 fileName (唯一文件名精确匹配, total=1)")
    void testListFileFilterByFileName() throws Exception {
        String fileName = uploadAndTrack("CT-L188-L-KW");

        MvcResult result = performGet(API_PATH + "?page=1&size=10&fileName=" + fileName, mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        ResultNode node = parseResult(result);
        assertEquals(1, node.data().get("total").asLong(), "唯一文件名应匹配 total=1");
        ArrayNode records = (ArrayNode) node.data().get("records");
        assertEquals(1, records.size(), "records 应只有 1 条");
        assertEquals(fileName, records.get(0).get("fileName").asText(), "返回的 fileName 应匹配");
    }

    // ===== CT-7: 排序 =====

    @Test
    @DisplayName("CT-7: 排序 (createdTime DESC, 非递增)")
    void testListFileSortOrder() throws Exception {
        // 上传 2 个文件使用共同前缀, 按上传顺序
        String fileName1 = uploadAndTrack("CT-L188-L-SO");
        Thread.sleep(10); // 确保时间差
        String fileName2 = uploadAndTrack("CT-L188-L-SO");

        MvcResult result = performGet(API_PATH + "?page=1&size=50&fileName=CT-L188-L-SO", mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        ArrayNode records = (ArrayNode) parseResult(result).data().get("records");
        assertTrue(records.size() >= 2, "应至少返回 2 条记录");

        // 验证 createdTime DESC (非递增: 前一条 >= 后一条)
        for (int i = 1; i < records.size(); i++) {
            String prev = records.get(i - 1).get("createdTime").asText();
            String curr = records.get(i).get("createdTime").asText();
            assertTrue(prev.compareTo(curr) >= 0,
                    "createdTime 应为 DESC 排序 (非递增), 但 " + prev + " < " + curr);
        }
    }

    // ===== CT-8: 数据范围 (viewer TENANT) =====

    @Test
    @DisplayName("CT-8: 数据范围 (viewer TENANT 可见本租户文件)")
    void testListFileViewerDataScope() throws Exception {
        String fileName = uploadAndTrack("CT-L188-L-DS");

        // viewer (TENANT 范围) 查询, 应能看到同租户文件
        MvcResult result = performGet(API_PATH + "?page=1&size=10&fileName=" + fileName, mockUser("viewer"));

        assertEquals(200, result.getResponse().getStatus(), "viewer 应能查询 (有 system:file:list)");
        assertSuccess(result);
        ResultNode node = parseResult(result);
        assertTrue(node.data().get("total").asLong() >= 1, "viewer TENANT 范围应可见本租户文件");

        ArrayNode records = (ArrayNode) node.data().get("records");
        boolean found = false;
        for (JsonNode rec : records) {
            if (fileName.equals(rec.get("fileName").asText())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "viewer 应能看到 biz 上传的文件 (TENANT 范围)");
    }

    // ===== CT-9: traceId 透传 =====

    @Test
    @DisplayName("CT-9: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)")
    void testListFileTraceIdPropagation() throws Exception {
        MvcResult result = performGet(API_PATH + "?page=1&size=10", mockUser("biz"));

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
