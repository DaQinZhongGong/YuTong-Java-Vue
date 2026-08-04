package com.yutong.boot.contract;

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
 * CT-markAllMessagesRead 契约测试 (operationId: markAllMessagesRead)。
 *
 * <p>契约来源: routes.yaml operationIds: [markAllMessagesRead],
 * POST /api/v1/messages/read-all
 * <p>控制器: MessageController.markAllRead() - @RequiresPermission("system:message:read")
 * <p>权限码: system:message:read (admin/biz/approver/viewer 均拥有, 无权限拒绝用例)
 * <p>服务: MessageService.markAllRead - 将当前用户所有 UNREAD 消息更新为 READ, 设置 read_time=now
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功全部已读 (200, code=0, data=null)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 traceId 透传 (响应头 + 响应体)</li>
 *   <li>CT-4 全部已读后未读数为 0 (验证 markAllRead 生效)</li>
 *   <li>CT-5 无未读消息时调用 (幂等, 200 code=0)</li>
 *   <li>CT-6 数据隔离 (仅标记当前用户消息, 不影响他人)</li>
 * </ol>
 *
 * <p>数据隔离: 通过 JdbcTemplate 直接插入 sys_message 测试数据, @AfterEach 物理清理。
 */
@DisplayName("CT-markAllMessagesRead: POST /api/v1/messages/read-all 契约测试")
class CTMarkAllMessagesReadTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/messages";

    private static final String ADMIN_USER_ID = "01MOCKUSER0000000000000ADMIN";
    private static final String BIZ_USER_ID = "01MOCKUSER00000000000000BIZ";
    private static final String TENANT_ID = "default";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final java.util.Set<String> createdIds = ConcurrentHashMap.newKeySet();

    @AfterEach
    void cleanupTestData() {
        for (String id : createdIds) {
            jdbcTemplate.update("DELETE FROM sys_message WHERE id = ?", id);
        }
        createdIds.clear();
    }

    /** 通过 JdbcTemplate 插入站内信测试数据, 返回生成的 id。 */
    private String createMessageAndTrack(String receiverId, String readStatus) {
        String id = "CTMSG" + UUID.randomUUID().toString().replace("-", "").substring(0, 22);
        jdbcTemplate.update(
                "INSERT INTO sys_message (id, tenant_id, receiver_id, msg_type, title, content, " +
                        "read_status, target_route_id, target_params, created_by, created_time, " +
                        "updated_by, updated_time, deleted, version) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, now(), ?, now(), false, 0)",
                id, TENANT_ID, receiverId, "SYSTEM",
                "CT全部已读测试", "CT测试内容",
                readStatus, "/dashboard", "{}",
                receiverId, receiverId);
        createdIds.add(id);
        return id;
    }

    // ===== CT-1: 成功全部已读 =====

    @Test
    @DisplayName("CT-1: 成功全部已读 (admin, 200, code=0, data=null)")
    void testMarkAllMessagesReadSuccess() throws Exception {
        createMessageAndTrack(ADMIN_USER_ID, "UNREAD");

        MvcResult result = performPost(API_PATH + "/read-all", null, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        assertNull(parseResult(result).data(), "全部已读成功响应 data 应为 null");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testMarkAllMessagesReadUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: traceId 透传 =====

    @Test
    @DisplayName("CT-3: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)")
    void testMarkAllMessagesReadTraceIdPropagation() throws Exception {
        createMessageAndTrack(ADMIN_USER_ID, "UNREAD");

        MvcResult result = performPost(API_PATH + "/read-all", null, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        ResultNode node = parseResult(result);
        assertNotNull(node.traceId(), "响应体应包含 traceId");
        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertNotNull(headerTraceId, "响应头应包含 X-Trace-Id");
        assertEquals(node.traceId(), headerTraceId, "响应头 X-Trace-Id 应与响应体 traceId 一致");
    }

    // ===== CT-4: 全部已读后未读数为 0 =====

    @Test
    @DisplayName("CT-4: 全部已读后未读数为 0 (验证 markAllRead 生效)")
    void testMarkAllMessagesReadEffective() throws Exception {
        // 插入未读消息
        createMessageAndTrack(ADMIN_USER_ID, "UNREAD");
        createMessageAndTrack(ADMIN_USER_ID, "UNREAD");

        // 全部已读
        MvcResult markResult = performPost(API_PATH + "/read-all", null, mockUser("admin"));
        assertEquals(200, markResult.getResponse().getStatus());
        assertSuccess(markResult);

        // 验证未读数为 0
        MvcResult countResult = performGet(API_PATH + "/unread-count", mockUser("admin"));
        assertEquals(200, countResult.getResponse().getStatus());
        assertSuccess(countResult);
        assertEquals(0, parseResult(countResult).data().asLong(),
                "全部已读后未读数应为 0");
    }

    // ===== CT-5: 无未读消息时调用 (幂等) =====

    @Test
    @DisplayName("CT-5: 无未读消息时调用 (幂等, 200 code=0)")
    void testMarkAllMessagesReadIdempotent() throws Exception {
        // biz 用户无未读消息, 调用全部已读应仍成功
        MvcResult result = performPost(API_PATH + "/read-all", null, mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus(), "无未读消息时调用应仍返回 200 (幂等)");
        assertSuccess(result);
    }

    // ===== CT-6: 数据隔离 (仅标记当前用户消息, 不影响他人) =====

    @Test
    @DisplayName("CT-6: 数据隔离 (admin 全部已读不影响 biz 未读数)")
    void testMarkAllMessagesReadDataIsolation() throws Exception {
        // admin 和 biz 各有未读消息
        createMessageAndTrack(ADMIN_USER_ID, "UNREAD");
        String bizMsgId = createMessageAndTrack(BIZ_USER_ID, "UNREAD");

        // admin 全部已读
        MvcResult adminMarkResult = performPost(API_PATH + "/read-all", null, mockUser("admin"));
        assertEquals(200, adminMarkResult.getResponse().getStatus());
        assertSuccess(adminMarkResult);

        // biz 未读数仍 >= 1 (不受 admin 操作影响)
        MvcResult bizCountResult = performGet(API_PATH + "/unread-count", mockUser("biz"));
        assertEquals(200, bizCountResult.getResponse().getStatus());
        assertSuccess(bizCountResult);
        assertTrue(parseResult(bizCountResult).data().asLong() >= 1,
                "biz 未读数应 >= 1 (不受 admin 全部已读影响)");

        // 验证 biz 消息仍为 UNREAD
        MvcResult bizListResult = performGet(API_PATH + "?pageNo=1&pageSize=50&readStatus=UNREAD", mockUser("biz"));
        assertEquals(200, bizListResult.getResponse().getStatus());
        com.fasterxml.jackson.databind.node.ArrayNode records =
                (com.fasterxml.jackson.databind.node.ArrayNode) parseResult(bizListResult).data().get("records");
        boolean found = false;
        for (com.fasterxml.jackson.databind.JsonNode rec : records) {
            if (bizMsgId.equals(rec.get("id").asText())) {
                found = true;
                assertEquals("UNREAD", rec.get("readStatus").asText(),
                        "biz 的消息应仍为 UNREAD (数据隔离)");
            }
        }
        assertTrue(found, "biz 的未读消息应仍在 UNREAD 列表中");
    }
}
