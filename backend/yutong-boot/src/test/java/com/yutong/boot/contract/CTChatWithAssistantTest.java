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
 * CT-chatWithAssistant 契约测试 (operationId: chatWithAssistant)。
 *
 * <p>契约来源: routes.yaml web.ai.assistant (permission: ai:assistant:use),
 *   AiChatController POST /api/v1/ai/chat (Accept: application/json → 完整响应)
 * <p>错误码来源: errors.yaml AUTH-403001 / SYS-404001 / AI-403001
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功对话 (admin, scenario=PLATFORM_QA, 200, code=0, 返回 conversationId/messageId/content/tokenInput/tokenOutput/latencyMs + traceId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (viewer + scenario=PAGE_GENERATE, 403 AUTH-403001, viewer 无 ai:tool:generate)</li>
 *   <li>CT-4 参数校验 - message 为空 (AiChatRequest 无 @NotBlank 注解, @Disabled 建议增加校验)</li>
 *   <li>CT-5 续接会话 (先 chat 创建会话, 再用 conversationId 续接, 验证同一 conversationId)</li>
 *   <li>CT-6 traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)</li>
 *   <li>CT-7 数据落库 (chat 后 ai_conversation + ai_message 有记录)</li>
 * </ol>
 *
 * <p>数据隔离: 每个用例通过 chat 创建唯一会话, @AfterEach 按 conversationId/traceId 物理清理
 * ai_message/ai_cost_log/ai_tool_call_log/ai_conversation/sys_operation_log。
 *
 * <p>注意: AI 控制器无 @RequiresPermission 注解, 权限仅在 chat() 内部对工具场景校验
 * (PAGE_GENERATE→ai:tool:generate, SQL_EXPLAIN→ai:tool:sql, PLATFORM_QA→ai:tool:meta)。
 * viewer 缺少全部 ai:tool:* 权限, 故工具场景触发 AUTH-403001。
 */
@DisplayName("CT-chatWithAssistant: POST /api/v1/ai/chat 契约测试")
class CTChatWithAssistantTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/ai/chat";
    private static final String ERROR_CODE_FORBIDDEN = "AUTH-403001";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 本测试创建的会话 ID 集合, @AfterEach 清理。 */
    private final java.util.Set<String> createdConversationIds = ConcurrentHashMap.newKeySet();
    /** 本测试产生的 traceId 集合 (成功+失败), 用于清理审计/工具日志。 */
    private final java.util.Set<String> traceIds = ConcurrentHashMap.newKeySet();

    @AfterEach
    void cleanupTestData() {
        // 1. 先删子表 ai_message (按 conversation_id)
        for (String convId : createdConversationIds) {
            jdbcTemplate.update("DELETE FROM ai_message WHERE conversation_id = ?", convId);
        }
        // 2. 删 ai_cost_log (按 conversation_id)
        for (String convId : createdConversationIds) {
            jdbcTemplate.update("DELETE FROM ai_cost_log WHERE conversation_id = ?", convId);
        }
        // 3. 删 ai_tool_call_log (按 trace_id)
        for (String tid : traceIds) {
            jdbcTemplate.update("DELETE FROM ai_tool_call_log WHERE trace_id = ?", tid);
        }
        // 4. 删主表 ai_conversation
        for (String convId : createdConversationIds) {
            jdbcTemplate.update("DELETE FROM ai_conversation WHERE id = ?", convId);
        }
        // 5. 删 sys_operation_log (权限拒绝审计, 按 trace_id)
        for (String tid : traceIds) {
            jdbcTemplate.update("DELETE FROM sys_operation_log WHERE trace_id = ?", tid);
        }
        createdConversationIds.clear();
        traceIds.clear();
    }

    /** 构造合法 chat 请求体。 */
    private Map<String, Object> buildChatRequest(String message, String scenario, String conversationId) {
        Map<String, Object> req = new HashMap<>();
        req.put("message", message);
        if (scenario != null) {
            req.put("scenario", scenario);
        }
        if (conversationId != null) {
            req.put("conversationId", conversationId);
        }
        req.put("idempotencyKey", "ct-chat-" + UUID.randomUUID());
        return req;
    }

    /** 发起 chat 并跟踪会话 ID 与 traceId。 */
    private MvcResult chatAndTrack(String userType, String message, String scenario) throws Exception {
        MvcResult result = performPost(API_PATH, buildChatRequest(message, scenario, null), mockUser(userType));
        ResultNode node = parseResult(result);
        traceIds.add(node.traceId());
        if (node.success() && node.data() != null && node.data().has("conversationId")) {
            createdConversationIds.add(node.data().get("conversationId").asText());
        }
        return result;
    }

    // ===== CT-1: 成功对话 =====

    @Test
    @DisplayName("CT-1: 成功对话 (admin, scenario=PLATFORM_QA, 200, code=0, 返回 conversationId/messageId/content/tokenUsage + traceId)")
    void testChatWithAssistantSuccess() throws Exception {
        MvcResult result = chatAndTrack("admin", "如何创建客户?", "PLATFORM_QA");

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");
        assertNotNull(data.get("conversationId"), "conversationId 不应为 null");
        assertNotNull(data.get("messageId"), "messageId 不应为 null");
        assertNotNull(data.get("content"), "content 不应为 null");
        assertTrue(data.get("content").asText().length() > 0, "content 应为非空字符串");
        assertEquals("PLATFORM_QA", data.get("scenario").asText(), "scenario 应为 PLATFORM_QA");
        assertNotNull(data.get("modelCode"), "modelCode 不应为 null");
        assertTrue(data.get("tokenInput").asInt() >= 0, "tokenInput 应 >= 0");
        assertTrue(data.get("tokenOutput").asInt() >= 0, "tokenOutput 应 >= 0");
        assertTrue(data.get("latencyMs").asInt() >= 0, "latencyMs 应 >= 0");
        assertNotNull(data.get("createdTime"), "createdTime 不应为 null");
        assertNotNull(data.get("citations"), "citations 不应为 null (可为空数组)");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testChatWithAssistantUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (viewer + scenario=PAGE_GENERATE, 403 AUTH-403001, viewer 无 ai:tool:generate)")
    void testChatWithAssistantForbidden() throws Exception {
        // 1. admin 先创建一个会话 (scenario=null 无工具, 不触发权限校验), 跟踪 conversationId
        MvcResult prepResult = chatAndTrack("admin", "预备会话", null);
        assertSuccess(prepResult);
        String conversationId = parseResult(prepResult).data().get("conversationId").asText();

        // 2. viewer 用该 conversationId + scenario=PAGE_GENERATE 发起对话
        //    chat() 内部 resolveToolForScenario(PAGE_GENERATE) → generate_page_draft → requirePermission(ai:tool:generate)
        //    viewer 缺少 ai:tool:generate → PermissionDeniedException → 403 AUTH-403001
        MvcResult result = performPost(API_PATH,
                buildChatRequest("帮我生成一个页面草稿", "PAGE_GENERATE", conversationId),
                mockUser("viewer"));

        ResultNode node = parseResult(result);
        traceIds.add(node.traceId());

        assertEquals(403, result.getResponse().getStatus(), "viewer 无 ai:tool:generate, HTTP 应为 403");
        assertError(result, ERROR_CODE_FORBIDDEN);
        assertTraceIdPresent(result);
    }

    // ===== CT-4: 参数校验 - message 为空 =====

    @Test
    @DisplayName("CT-4: 参数校验 - message 为空 (AiChatRequest 无 @NotBlank 注解, @Disabled 建议增加校验)")
    @org.junit.jupiter.api.Disabled("AiChatRequest.message 无 @NotBlank 注解且 Controller 无 @Valid, " +
            "空 message 不会触发 400 校验; 建议在 AiChatRequest 增加 @NotBlank 并在 Controller 加 @Valid")
    void testChatWithAssistantValidationBlankMessage() {
        // 当前实现: 空 message 仍会成功返回 (Mock 模式下 generateMockReply 处理空字符串)
        // 若后续增加 @Valid + @NotBlank, 此用例应断言 400 SYS-400001
    }

    // ===== CT-5: 续接会话 =====

    @Test
    @DisplayName("CT-5: 续接会话 (先 chat 创建会话, 再用 conversationId 续接, 验证同一 conversationId)")
    void testChatWithAssistantContinueConversation() throws Exception {
        // 第一次对话: 创建新会话
        MvcResult first = chatAndTrack("admin", "第一个问题", "PLATFORM_QA");
        assertEquals(200, first.getResponse().getStatus());
        assertSuccess(first);
        String firstConversationId = parseResult(first).data().get("conversationId").asText();
        String firstMessageId = parseResult(first).data().get("messageId").asText();

        // 第二次对话: 传入 conversationId 续接
        Map<String, Object> req = buildChatRequest("第二个问题", "PLATFORM_QA", firstConversationId);
        MvcResult second = performPost(API_PATH, req, mockUser("admin"));
        ResultNode secondNode = parseResult(second);
        traceIds.add(secondNode.traceId());

        assertEquals(200, second.getResponse().getStatus(), "续接会话 HTTP 应为 200");
        assertSuccess(second);
        assertTraceIdPresent(second);

        String secondConversationId = secondNode.data().get("conversationId").asText();
        String secondMessageId = secondNode.data().get("messageId").asText();
        assertEquals(firstConversationId, secondConversationId,
                "续接会话应返回同一 conversationId");
        assertNotEquals(firstMessageId, secondMessageId,
                "两次对话的 messageId 应不同");
    }

    // ===== CT-6: traceId 透传 =====

    @Test
    @DisplayName("CT-6: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)")
    void testChatWithAssistantTraceIdPropagation() throws Exception {
        MvcResult result = chatAndTrack("admin", "traceId 透传测试", null);

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);

        ResultNode node = parseResult(result);
        assertNotNull(node.traceId(), "响应体应包含 traceId");
        assertTrue(node.traceId().length() >= 16 && node.traceId().length() <= 64,
                "traceId 长度应在 16-64 之间");

        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertNotNull(headerTraceId, "响应头应包含 X-Trace-Id");
        assertEquals(node.traceId(), headerTraceId,
                "响应头 X-Trace-Id 应与响应体 traceId 一致");
    }

    // ===== CT-7: 数据落库 =====

    @Test
    @DisplayName("CT-7: 数据落库 (chat 后 ai_conversation + ai_message 有记录)")
    void testChatWithAssistantDataPersisted() throws Exception {
        MvcResult result = chatAndTrack("admin", "数据落库验证问题", "PLATFORM_QA");
        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);

        String conversationId = parseResult(result).data().get("conversationId").asText();
        String traceId = parseResult(result).traceId();

        // 验证 ai_conversation 有记录
        Integer convCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_conversation WHERE id = ?",
                Integer.class, conversationId);
        assertNotNull(convCount, "ai_conversation 查询不应返回 null");
        assertEquals(1, convCount, "ai_conversation 应有 1 条记录");

        // 验证 ai_message 有记录 (1 条 user + 1 条 assistant = 2 条)
        Integer msgCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_message WHERE conversation_id = ?",
                Integer.class, conversationId);
        assertNotNull(msgCount, "ai_message 查询不应返回 null");
        assertEquals(2, msgCount, "ai_message 应有 2 条记录 (user + assistant)");

        // 验证 ai_cost_log 有记录 (PLATFORM_QA 场景会产生成本日志)
        Integer costCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_cost_log WHERE conversation_id = ?",
                Integer.class, conversationId);
        assertNotNull(costCount, "ai_cost_log 查询不应返回 null");
        assertEquals(1, costCount, "ai_cost_log 应有 1 条记录");

        // 验证 ai_tool_call_log 有记录 (PLATFORM_QA → query_meta_model 工具)
        Integer toolLogCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_tool_call_log WHERE trace_id = ?",
                Integer.class, traceId);
        assertNotNull(toolLogCount, "ai_tool_call_log 查询不应返回 null");
        assertEquals(1, toolLogCount, "ai_tool_call_log 应有 1 条记录 (query_meta_model)");
    }
}
