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
 * CT-listAiConversations 契约测试 (operationId: listAiConversations)。
 *
 * <p>契约来源: routes.yaml web.ai.assistant (permission: ai:assistant:use),
 *   AiChatController GET /api/v1/ai/conversations?page=&size=&scenario=
 * <p>策略来源: queryPage profile
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功分页查询 (biz, 200, code=0, records/total/page/size + traceId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 数据隔离 (biz 创建会话, viewer 查询看不到 biz 的会话 — 按 userId 隔离)</li>
 *   <li>CT-4 分页参数 (size=0 → 钳制为 20, 200 优雅降级)</li>
 *   <li>CT-5 过滤条件 scenario (只返回指定 scenario 的会话)</li>
 *   <li>CT-6 空结果 (scenario 不匹配, total=0)</li>
 * </ol>
 *
 * <p>数据隔离: 每个用例通过 chat 创建唯一会话, @AfterEach 按 conversationId/traceId 物理清理。
 *
 * <p>注意: listAiConversations 按 CurrentUserContext.getUserId() 过滤, 每个用户仅看到自己的会话。
 * AI 控制器无 @RequiresPermission, 无路由级权限校验。
 */
@DisplayName("CT-listAiConversations: GET /api/v1/ai/conversations 契约测试")
class CTListAiConversationsTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/ai/conversations";
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

    /** 通过 chat 创建会话并跟踪 ID, 返回 conversationId。 */
    private String createConversationAndTrack(String userType, String scenario) throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("message", "CT-listConv-" + UUID.randomUUID());
        if (scenario != null) {
            req.put("scenario", scenario);
        }
        req.put("idempotencyKey", "ct-listconv-" + UUID.randomUUID());

        MvcResult result = performPost(CHAT_PATH, req, mockUser(userType));
        assertSuccess(result);
        ResultNode node = parseResult(result);
        traceIds.add(node.traceId());
        String convId = node.data().get("conversationId").asText();
        createdConversationIds.add(convId);
        return convId;
    }

    // ===== CT-1: 成功分页查询 =====

    @Test
    @DisplayName("CT-1: 成功分页查询 (biz, 200, code=0, records/total/page/size + traceId)")
    void testListAiConversationsSuccess() throws Exception {
        String convId = createConversationAndTrack("biz", "PLATFORM_QA");

        MvcResult result = performGet(API_PATH + "?page=1&size=10", mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");
        assertNotNull(data.get("records"), "records 不应为 null");
        assertTrue(data.get("records").isArray(), "records 应为数组");
        assertTrue(data.get("total").asLong() >= 1, "total 应 >= 1 (至少包含刚创建的会话)");
        assertEquals(1, data.get("page").asInt(), "page 应为 1");
        assertEquals(10, data.get("size").asInt(), "size 应为 10");

        // 验证刚创建的会话在结果中
        ArrayNode records = (ArrayNode) data.get("records");
        boolean found = false;
        for (JsonNode rec : records) {
            if (convId.equals(rec.get("id").asText())) {
                found = true;
                assertNotNull(rec.get("conversationNo"), "conversationNo 不应为 null");
                assertNotNull(rec.get("scenario"), "scenario 不应为 null");
                assertNotNull(rec.get("status"), "status 不应为 null");
                break;
            }
        }
        assertTrue(found, "结果中应包含刚创建的会话 (id=" + convId + ")");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testListAiConversationsUnauthenticated() {
        // Mock 模式限制
    }

    // ===== CT-3: 数据隔离 (按 userId) =====

    @Test
    @DisplayName("CT-3: 数据隔离 (biz 创建会话, viewer 查询看不到 biz 的会话)")
    void testListAiConversationsDataIsolation() throws Exception {
        String convId = createConversationAndTrack("biz", "PLATFORM_QA");

        // viewer 查询 (userId 不同, 不应看到 biz 创建的会话)
        MvcResult result = performGet(API_PATH + "?page=1&size=50", mockUser("viewer"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        ArrayNode records = (ArrayNode) data.get("records");

        // viewer 的 userId 与 biz 不同, 不应看到 biz 的会话
        boolean found = false;
        for (JsonNode rec : records) {
            if (convId.equals(rec.get("id").asText())) {
                found = true;
                break;
            }
        }
        assertFalse(found, "viewer 不应看到 biz 创建的会话 (按 userId 隔离)");
    }

    // ===== CT-4: 分页参数 - size=0 钳制为 20 =====

    @Test
    @DisplayName("CT-4: 分页参数 (size=0 → PageRequest 钳制为 20, 200 优雅降级)")
    void testListAiConversationsPageSizeClamped() throws Exception {
        MvcResult result = performGet(API_PATH + "?page=1&size=0", mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus(), "size 越界应优雅降级, HTTP 200");
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        // PageRequest: size < 1 → 钳制为 20
        assertEquals(20, data.get("size").asInt(), "size=0 应钳制为默认值 20");
    }

    // ===== CT-5: 过滤条件 scenario =====

    @Test
    @DisplayName("CT-5: 过滤条件 scenario (scenario=PLATFORM_QA 只返回该场景会话)")
    void testListAiConversationsFilterByScenario() throws Exception {
        // 创建两个不同 scenario 的会话
        String convPq = createConversationAndTrack("biz", "PLATFORM_QA");
        String convField = createConversationAndTrack("biz", "FIELD_SUGGEST");

        // 只查 PLATFORM_QA
        MvcResult result = performGet(API_PATH + "?page=1&size=50&scenario=PLATFORM_QA", mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        ArrayNode records = (ArrayNode) parseResult(result).data().get("records");

        // 验证所有返回的记录 scenario 均为 PLATFORM_QA
        for (JsonNode rec : records) {
            assertEquals("PLATFORM_QA", rec.get("scenario").asText(),
                    "过滤 scenario=PLATFORM_QA 后所有记录 scenario 应为 PLATFORM_QA");
        }

        // 验证 convPq 在结果中, convField 不在
        boolean foundPq = false;
        boolean foundField = false;
        for (JsonNode rec : records) {
            String id = rec.get("id").asText();
            if (id.equals(convPq)) foundPq = true;
            if (id.equals(convField)) foundField = true;
        }
        assertTrue(foundPq, "PLATFORM_QA 会话应在结果中");
        assertFalse(foundField, "FIELD_SUGGEST 会话不应在 scenario=PLATFORM_QA 结果中");
    }

    // ===== CT-6: 空结果 =====

    @Test
    @DisplayName("CT-6: 空结果 (scenario 不匹配, total=0, records 空数组)")
    void testListAiConversationsEmptyResult() throws Exception {
        // 使用不存在的 scenario 查询
        MvcResult result = performGet(API_PATH + "?page=1&size=10&scenario=NOSUCH_SCENARIO_CT", mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertEquals(0, data.get("total").asLong(), "不匹配的 scenario 应返回 total=0");
        assertTrue(data.get("records").isArray(), "records 应为数组");
        assertEquals(0, data.get("records").size(), "records 应为空数组");
    }
}
