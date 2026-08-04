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
 * CT-getAiConversation 契约测试 (operationId: getAiConversation)。
 *
 * <p>契约来源: routes.yaml web.ai.assistant (permission: ai:assistant:use),
 *   AiChatController GET /api/v1/ai/conversations/{id}
 * <p>错误码来源: errors.yaml SYS-404001 (ResourceNotFoundException 默认错误码)
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功获取 (admin 创建会话, getAiConversation, 200, code=0, 返回 id/conversationNo/scenario/userId + traceId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 不存在 (不存在的 id, 404 SYS-404001)</li>
 *   <li>CT-4 traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)</li>
 *   <li>CT-5 标准错误信封 (404 响应 code/message/traceId 字段齐全)</li>
 * </ol>
 *
 * <p>数据隔离: 通过 chat 创建唯一会话, @AfterEach 按 conversationId/traceId 物理清理。
 *
 * <p>注意: getAiConversation 直接 selectById, 无数据范围校验 (不检查 userId 归属)。
 * AI 控制器无 @RequiresPermission, 无路由级权限校验。
 * ResourceNotFoundException 默认使用 SYS-404001 (errors.yaml 中无 AI-404001)。
 */
@DisplayName("CT-getAiConversation: GET /api/v1/ai/conversations/{id} 契约测试")
class CTGetAiConversationTest extends AbstractContractTest {

    private static final String API_PATH_PREFIX = "/api/v1/ai/conversations/";
    private static final String CHAT_PATH = "/api/v1/ai/chat";
    private static final String ERROR_CODE_NOT_FOUND = "SYS-404001";

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

    /** 通过 chat 创建会话并跟踪 ID, 返回 conversationId。 */
    private String createConversationAndTrack(String userType, String scenario) throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("message", "CT-getConv-" + UUID.randomUUID());
        if (scenario != null) {
            req.put("scenario", scenario);
        }
        req.put("idempotencyKey", "ct-getconv-" + UUID.randomUUID());

        MvcResult result = performPost(CHAT_PATH, req, mockUser(userType));
        assertSuccess(result);
        ResultNode node = parseResult(result);
        traceIds.add(node.traceId());
        String convId = node.data().get("conversationId").asText();
        createdConversationIds.add(convId);
        return convId;
    }

    // ===== CT-1: 成功获取 =====

    @Test
    @DisplayName("CT-1: 成功获取会话详情 (admin, 200, code=0, 返回 id/conversationNo/scenario/userId/status + traceId)")
    void testGetAiConversationSuccess() throws Exception {
        String convId = createConversationAndTrack("admin", "PLATFORM_QA");

        MvcResult result = performGet(API_PATH_PREFIX + convId, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");
        assertEquals(convId, data.get("id").asText(), "返回的 id 应与请求的 id 一致");
        assertNotNull(data.get("conversationNo"), "conversationNo 不应为 null");
        assertNotNull(data.get("userId"), "userId 不应为 null");
        assertNotNull(data.get("title"), "title 不应为 null");
        assertEquals("PLATFORM_QA", data.get("scenario").asText(), "scenario 应为 PLATFORM_QA");
        assertNotNull(data.get("modelCode"), "modelCode 不应为 null");
        assertNotNull(data.get("status"), "status 不应为 null");
        assertNotNull(data.get("locale"), "locale 不应为 null");
        assertNotNull(data.get("lastMessageTime"), "lastMessageTime 不应为 null");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testGetAiConversationUnauthenticated() {
        // Mock 模式限制
    }

    // ===== CT-3: 不存在 (404) =====

    @Test
    @DisplayName("CT-3: 不存在 (不存在的 id, 404 SYS-404001)")
    void testGetAiConversationNotFound() throws Exception {
        String nonExistentId = "NOSUCHCONV-CT-" + UUID.randomUUID();

        MvcResult result = performGet(API_PATH_PREFIX + nonExistentId, mockUser("admin"));

        assertEquals(404, result.getResponse().getStatus(), "不存在的会话 HTTP 应为 404");
        assertError(result, ERROR_CODE_NOT_FOUND);
        assertTraceIdPresent(result);
    }

    // ===== CT-4: traceId 透传 =====

    @Test
    @DisplayName("CT-4: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)")
    void testGetAiConversationTraceIdPropagation() throws Exception {
        String convId = createConversationAndTrack("admin", "PLATFORM_QA");

        MvcResult result = performGet(API_PATH_PREFIX + convId, mockUser("admin"));

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

    // ===== CT-5: 标准错误信封结构 =====

    @Test
    @DisplayName("CT-5: 标准错误信封 (404 响应 code/message/traceId 字段齐全)")
    void testGetAiConversationErrorEnvelopeStructure() throws Exception {
        String nonExistentId = "NOSUCHCONV-CT-" + UUID.randomUUID();

        MvcResult result = performGet(API_PATH_PREFIX + nonExistentId, mockUser("admin"));

        assertEquals(404, result.getResponse().getStatus());

        ResultNode node = parseResult(result);
        assertNotNull(node.code(), "错误信封必须包含 code 字段");
        assertNotEquals("0", node.code(), "错误响应 code 不应为 0");
        assertEquals(ERROR_CODE_NOT_FOUND, node.code(), "code 应为 SYS-404001");
        assertNotNull(node.message(), "错误信封必须包含 message 字段");
        assertNotNull(node.traceId(), "错误信封必须包含 traceId 字段");
        assertTrue(node.traceId().length() >= 16 && node.traceId().length() <= 64,
                "traceId 长度应在 16-64 之间");
    }
}
