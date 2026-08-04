package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
 * CT-listAiToolCallLogs 契约测试 (operationId: listAiToolCallLogs)。
 *
 * <p>契约来源: routes.yaml web.ai.assistant (permission: ai:assistant:use),
 *   AiToolCallLogController GET /api/v1/ai/tool-call-logs?page=&size=&userId=&toolName=&result=
 * <p>策略来源: queryPage profile
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功分页查询 (admin, 200, code=0, records/total/page/size + traceId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 过滤条件 result=SUCCESS (先 chat 触发工具调用, 查 result=SUCCESS 验证)</li>
 *   <li>CT-4 过滤条件 toolName (查 toolName=query_meta_model 验证)</li>
 *   <li>CT-5 空结果 (不存在的 toolName, total=0)</li>
 * </ol>
 *
 * <p>数据隔离: 通过 chat (scenario=PLATFORM_QA → query_meta_model 工具) 触发工具调用审计日志,
 * @AfterEach 按 conversationId/traceId 物理清理。
 *
 * <p>注意: listAiToolCallLogs 按 tenantId 过滤 (同租户可见), 无 userId 强制隔离。
 * AI 控制器无 @RequiresPermission, 无路由级权限校验。
 */
@DisplayName("CT-listAiToolCallLogs: GET /api/v1/ai/tool-call-logs 契约测试")
class CTListAiToolCallLogsTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/ai/tool-call-logs";
    private static final String CHAT_PATH = "/api/v1/ai/chat";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final java.util.Set<String> createdConversationIds = ConcurrentHashMap.newKeySet();
    private final java.util.Set<String> traceIds = ConcurrentHashMap.newKeySet();

    @AfterEach
    void cleanupTestData() {
        for (String convId : createdConversationIds) {
            jdbcTemplate.update("DELETE FROM ai_message WHERE conversation_id = ?", convId);
        }
        for (String convId : createdConversationIds) {
            jdbcTemplate.update("DELETE FROM ai_cost_log WHERE conversation_id = ?", convId);
        }
        for (String tid : traceIds) {
            jdbcTemplate.update("DELETE FROM ai_tool_call_log WHERE trace_id = ?", tid);
        }
        for (String convId : createdConversationIds) {
            jdbcTemplate.update("DELETE FROM ai_conversation WHERE id = ?", convId);
        }
        for (String tid : traceIds) {
            jdbcTemplate.update("DELETE FROM sys_operation_log WHERE trace_id = ?", tid);
        }
        createdConversationIds.clear();
        traceIds.clear();
    }

    /** 通过 chat (scenario=PLATFORM_QA → query_meta_model 工具) 触发工具调用审计日志。
     * 返回 traceId (用于关联工具调用日志)。 */
    private String triggerToolCallAndTrack(String userType) throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("message", "CT-toolLog-" + UUID.randomUUID());
        req.put("scenario", "PLATFORM_QA");
        req.put("idempotencyKey", "ct-toollog-" + UUID.randomUUID());

        MvcResult result = performPost(CHAT_PATH, req, mockUser(userType));
        assertSuccess(result);
        ResultNode node = parseResult(result);
        traceIds.add(node.traceId());
        createdConversationIds.add(node.data().get("conversationId").asText());
        return node.traceId();
    }

    // ===== CT-1: 成功分页查询 =====

    @Test
    @DisplayName("CT-1: 成功分页查询 (admin, 200, code=0, records/total/page/size + traceId)")
    void testListAiToolCallLogsSuccess() throws Exception {
        String traceId = triggerToolCallAndTrack("admin");

        MvcResult result = performGet(API_PATH + "?page=1&size=10", mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");
        assertNotNull(data.get("records"), "records 不应为 null");
        assertTrue(data.get("records").isArray(), "records 应为数组");
        assertTrue(data.get("total").asLong() >= 1, "total 应 >= 1 (至少包含刚触发的工具调用)");
        assertEquals(1, data.get("page").asInt(), "page 应为 1");
        assertEquals(10, data.get("size").asInt(), "size 应为 10");

        // 验证刚触发的工具调用日志在结果中 (按 traceId 匹配)
        ArrayNode records = (ArrayNode) data.get("records");
        boolean found = false;
        for (JsonNode rec : records) {
            if (traceId.equals(rec.get("traceId").asText())) {
                found = true;
                assertNotNull(rec.get("toolName"), "toolName 不应为 null");
                assertNotNull(rec.get("riskLevel"), "riskLevel 不应为 null");
                assertNotNull(rec.get("userId"), "userId 不应为 null");
                assertNotNull(rec.get("result"), "result 不应为 null");
                assertNotNull(rec.get("latencyMs"), "latencyMs 不应为 null");
                break;
            }
        }
        assertTrue(found, "结果中应包含刚触发的工具调用日志 (traceId=" + traceId + ")");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testListAiToolCallLogsUnauthenticated() {
        // Mock 模式限制
    }

    // ===== CT-3: 过滤条件 result=SUCCESS =====

    @Test
    @DisplayName("CT-3: 过滤条件 result=SUCCESS (所有返回记录 result 均为 SUCCESS)")
    void testListAiToolCallLogsFilterByResult() throws Exception {
        triggerToolCallAndTrack("admin");

        MvcResult result = performGet(API_PATH + "?page=1&size=50&result=SUCCESS", mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        ArrayNode records = (ArrayNode) parseResult(result).data().get("records");
        assertTrue(records.size() >= 1, "应至少返回 1 条 SUCCESS 记录");

        // 验证所有返回的记录 result 均为 SUCCESS
        for (JsonNode rec : records) {
            assertEquals("SUCCESS", rec.get("result").asText(),
                    "过滤 result=SUCCESS 后所有记录 result 应为 SUCCESS");
        }
    }

    // ===== CT-4: 过滤条件 toolName =====

    @Test
    @DisplayName("CT-4: 过滤条件 toolName=query_meta_model (所有返回记录 toolName 均为 query_meta_model)")
    void testListAiToolCallLogsFilterByToolName() throws Exception {
        triggerToolCallAndTrack("admin");

        MvcResult result = performGet(API_PATH + "?page=1&size=50&toolName=query_meta_model", mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        ArrayNode records = (ArrayNode) parseResult(result).data().get("records");
        assertTrue(records.size() >= 1, "应至少返回 1 条 query_meta_model 记录");

        // 验证所有返回的记录 toolName 均为 query_meta_model
        for (JsonNode rec : records) {
            assertEquals("query_meta_model", rec.get("toolName").asText(),
                    "过滤 toolName=query_meta_model 后所有记录 toolName 应为 query_meta_model");
        }
    }

    // ===== CT-5: 空结果 =====

    @Test
    @DisplayName("CT-5: 空结果 (不存在的 toolName, total=0, records 空数组)")
    void testListAiToolCallLogsEmptyResult() throws Exception {
        MvcResult result = performGet(API_PATH + "?page=1&size=10&toolName=NOSUCH_TOOL_CT", mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertEquals(0, data.get("total").asLong(), "不存在的 toolName 应返回 total=0");
        assertTrue(data.get("records").isArray(), "records 应为数组");
        assertEquals(0, data.get("records").size(), "records 应为空数组");
    }
}
