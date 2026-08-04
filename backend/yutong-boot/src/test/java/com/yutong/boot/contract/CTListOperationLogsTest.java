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
 * CT-listOperationLogs 契约测试 (operationId: listOperationLogs)。
 *
 * <p>契约来源: routes.yaml operationIds: [listOperationLogs],
 * GET /api/v1/operation-logs?page=1&size=20&operatorId=...&keyword=...&result=...&bizType=...&bizId=...
 * <p>控制器: OperationLogController.list(page, size, operatorId, keyword, result, bizType, bizId)
 * - 无 @RequiresPermission (所有认证用户可访问)
 * <p>服务: OperationLogService.pageLogs - 按 operatorId/bizType/bizId 过滤, keyword 模糊匹配 content,
 * 按 operatedTime DESC; 返回前对 beforeJson/afterJson 做深度递归脱敏
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功分页查询 (200, code=0, records/total/page/size + traceId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 空结果 (不匹配 keyword, total=0)</li>
 *   <li>CT-4 过滤条件 operatorId (精确匹配)</li>
 *   <li>CT-5 过滤条件 result (精确匹配 SUCCESS/FAILED)</li>
 *   <li>CT-6 过滤条件 bizType+bizId (精确匹配)</li>
 *   <li>CT-7 排序 (operatedTime DESC 非递增)</li>
 *   <li>CT-8 标准响应信封 (records/total/page/size 结构齐全)</li>
 *   <li>CT-9 traceId 透传 (响应头 + 响应体)</li>
 * </ol>
 *
 * <p>数据隔离: 通过 JdbcTemplate 直接插入 sys_operation_log 测试数据, @AfterEach 物理清理。
 */
@DisplayName("CT-listOperationLogs: GET /api/v1/operation-logs 契约测试")
class CTListOperationLogsTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/operation-logs";
    private static final String TENANT_ID = "default";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final java.util.Set<String> createdIds = ConcurrentHashMap.newKeySet();

    @AfterEach
    void cleanupTestData() {
        for (String id : createdIds) {
            jdbcTemplate.update("DELETE FROM sys_operation_log WHERE id = ?", id);
        }
        createdIds.clear();
    }

    /** 通过 JdbcTemplate 插入操作日志测试数据, 返回生成的 id。 */
    private String createLogAndTrack(String operatorId, String result, String bizType,
                                      String bizId, String content, String traceIdSuffix) {
        String id = "CTOPLOG" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String traceId = "ct-trace-" + traceIdSuffix + "-" + UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update(
                "INSERT INTO sys_operation_log (id, tenant_id, operation_type, module, biz_type, biz_id, " +
                        "content, before_json, after_json, result, error_code, trace_id, operator_id, " +
                        "operator_name, ip, user_agent, operated_time, created_by, created_time, " +
                        "updated_by, updated_time, deleted, version) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, NULL, ?, ?, ?, ?, ?, now(), ?, now(), ?, now(), false, 0)",
                id, TENANT_ID, "CREATE", "sample", bizType, bizId,
                content, "{}", "{}", result, traceId,
                operatorId, "CT测试用户", "127.0.0.1", "CT-Test-Agent",
                operatorId, operatorId);
        createdIds.add(id);
        return id;
    }

    // ===== CT-1: 成功分页查询 =====

    @Test
    @DisplayName("CT-1: 成功分页查询 (admin 用户, 200, code=0, records/total/page/size + traceId)")
    void testListOperationLogsSuccess() throws Exception {
        createLogAndTrack("ct-op-1", "SUCCESS", "biz_request", "ct-biz-1", "CT操作日志查询测试", "s1");

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
    void testListOperationLogsUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 空结果 =====

    @Test
    @DisplayName("CT-3: 空结果 (不匹配 keyword, total=0, records 空数组)")
    void testListOperationLogsEmptyResult() throws Exception {
        String nonExistentKeyword = "nosuchkeyword-ct-" + UUID.randomUUID().toString().substring(0, 8);

        MvcResult result = performGet(API_PATH + "?page=1&size=10&keyword=" + nonExistentKeyword, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertEquals(0, data.get("total").asLong(), "不匹配的 keyword 应返回 total=0");
        assertEquals(0, data.get("records").size(), "records 应为空数组");
    }

    // ===== CT-4: 过滤条件 operatorId =====

    @Test
    @DisplayName("CT-4: 过滤条件 operatorId (精确匹配)")
    void testListOperationLogsFilterByOperatorId() throws Exception {
        String uniqueOperator = "ct-op-filter-" + UUID.randomUUID().toString().substring(0, 8);
        createLogAndTrack(uniqueOperator, "SUCCESS", "biz_request", "ct-biz-op", "CT操作人过滤", "s4");

        MvcResult result = performGet(API_PATH + "?page=1&size=10&operatorId=" + uniqueOperator, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertTrue(data.get("total").asLong() >= 1, "operatorId 精确匹配应返回 total>=1");

        ArrayNode records = (ArrayNode) data.get("records");
        for (JsonNode rec : records) {
            assertEquals(uniqueOperator, rec.get("operatorId").asText(),
                    "所有返回记录的 operatorId 应为过滤值");
        }
    }

    // ===== CT-5: 过滤条件 result =====

    @Test
    @DisplayName("CT-5: 过滤条件 result=FAILED (精确匹配)")
    void testListOperationLogsFilterByResult() throws Exception {
        String uniqueContent = "CT结果过滤-" + UUID.randomUUID().toString().substring(0, 8);
        createLogAndTrack("ct-op-res", "FAILED", "biz_request", "ct-biz-res", uniqueContent, "s5");

        MvcResult result = performGet(API_PATH + "?page=1&size=10&keyword=" + uniqueContent + "&result=FAILED", mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        ArrayNode records = (ArrayNode) parseResult(result).data().get("records");
        assertTrue(records.size() >= 1, "应至少返回 1 条 FAILED 记录");
        for (JsonNode rec : records) {
            assertEquals("FAILED", rec.get("result").asText(), "应仅返回 result=FAILED 记录");
        }
    }

    // ===== CT-6: 过滤条件 bizType+bizId =====

    @Test
    @DisplayName("CT-6: 过滤条件 bizType+bizId (精确匹配)")
    void testListOperationLogsFilterByBizTypeAndBizId() throws Exception {
        String uniqueBizId = "ct-biz-" + UUID.randomUUID().toString().substring(0, 8);
        createLogAndTrack("ct-op-biz", "SUCCESS", "biz_request", uniqueBizId, "CT业务过滤测试", "s6");

        MvcResult result = performGet(
                API_PATH + "?page=1&size=10&bizType=biz_request&bizId=" + uniqueBizId,
                mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertTrue(data.get("total").asLong() >= 1, "bizType+bizId 精确匹配应返回 total>=1");

        ArrayNode records = (ArrayNode) data.get("records");
        for (JsonNode rec : records) {
            assertEquals("biz_request", rec.get("bizType").asText(), "bizType 应为过滤值");
            assertEquals(uniqueBizId, rec.get("bizId").asText(), "bizId 应为过滤值");
        }
    }

    // ===== CT-7: 排序 (operatedTime DESC) =====

    @Test
    @DisplayName("CT-7: 排序 (operatedTime DESC 非递增)")
    void testListOperationLogsSortOrder() throws Exception {
        String uniqueOp = "ct-op-sort-" + UUID.randomUUID().toString().substring(0, 8);
        createLogAndTrack(uniqueOp, "SUCCESS", "biz_request", "ct-biz-sort", "CT排序1", "s7a");
        Thread.sleep(10);
        createLogAndTrack(uniqueOp, "SUCCESS", "biz_request", "ct-biz-sort", "CT排序2", "s7b");

        MvcResult result = performGet(API_PATH + "?page=1&size=20&operatorId=" + uniqueOp, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        ArrayNode records = (ArrayNode) parseResult(result).data().get("records");
        assertTrue(records.size() >= 2, "应至少返回 2 条记录");

        // operatedTime DESC (非递增)
        for (int i = 1; i < records.size(); i++) {
            String prev = records.get(i - 1).get("operatedTime").asText();
            String curr = records.get(i).get("operatedTime").asText();
            assertTrue(prev.compareTo(curr) >= 0,
                    "operatedTime 应为 DESC 排序 (非递增), 但 " + prev + " < " + curr);
        }
    }

    // ===== CT-8: 标准响应信封 =====

    @Test
    @DisplayName("CT-8: 标准响应信封 (records/total/page/size 结构齐全)")
    void testListOperationLogsResponseEnvelope() throws Exception {
        createLogAndTrack("ct-op-env", "SUCCESS", "biz_request", "ct-biz-env", "CT信封测试", "s8");

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

    // ===== CT-9: traceId 透传 =====

    @Test
    @DisplayName("CT-9: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)")
    void testListOperationLogsTraceIdPropagation() throws Exception {
        createLogAndTrack("ct-op-trace", "SUCCESS", "biz_request", "ct-biz-trace", "CT traceId测试", "s9");

        MvcResult result = performGet(API_PATH + "?page=1&size=10", mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        ResultNode node = parseResult(result);
        assertNotNull(node.traceId(), "响应体应包含 traceId");
        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertNotNull(headerTraceId, "响应头应包含 X-Trace-Id");
        assertEquals(node.traceId(), headerTraceId, "响应头 X-Trace-Id 应与响应体 traceId 一致");
    }
}
