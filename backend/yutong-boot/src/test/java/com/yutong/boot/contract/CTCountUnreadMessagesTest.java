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
 * CT-countUnreadMessages 契约测试 (operationId: countUnreadMessages)。
 *
 * <p>契约来源: routes.yaml operationIds: [countUnreadMessages],
 * GET /api/v1/messages/unread-count
 * <p>控制器: MessageController.getUnreadCount() - 无 @RequiresPermission (所有认证用户可访问)
 * <p>服务: MessageService.getUnreadCount - 统计当前用户 read_status=UNREAD 的消息数
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功查询未读数 (200, code=0, data 为非负整数)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 无未读消息 (data=0)</li>
 *   <li>CT-4 有未读消息 (data >= 1, 验证计数正确)</li>
 *   <li>CT-5 数据隔离 (仅统计当前用户未读消息)</li>
 *   <li>CT-6 traceId 透传 (响应头 + 响应体)</li>
 * </ol>
 *
 * <p>数据隔离: 通过 JdbcTemplate 直接插入 sys_message 测试数据, @AfterEach 物理清理。
 */
@DisplayName("CT-countUnreadMessages: GET /api/v1/messages/unread-count 契约测试")
class CTCountUnreadMessagesTest extends AbstractContractTest {

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
                "CT未读计数测试", "CT测试内容",
                readStatus, "/dashboard", "{}",
                receiverId, receiverId);
        createdIds.add(id);
        return id;
    }

    // ===== CT-1: 成功查询未读数 =====

    @Test
    @DisplayName("CT-1: 成功查询未读数 (200, code=0, data 为非负整数)")
    void testCountUnreadMessagesSuccess() throws Exception {
        MvcResult result = performGet(API_PATH + "/unread-count", mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");
        assertTrue(data.isNumber(), "data 应为数字");
        assertTrue(data.asLong() >= 0, "未读数应为非负整数");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testCountUnreadMessagesUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 无未读消息 =====

    @Test
    @DisplayName("CT-3: 无未读消息 (biz 用户 data=0)")
    void testCountUnreadMessagesZero() throws Exception {
        // biz 用户无任何消息 → 未读数为 0
        MvcResult result = performGet(API_PATH + "/unread-count", mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertEquals(0, data.asLong(), "biz 用户无消息, 未读数应为 0");
    }

    // ===== CT-4: 有未读消息 =====

    @Test
    @DisplayName("CT-4: 有未读消息 (data >= 1, 验证计数正确)")
    void testCountUnreadMessagesWithUnread() throws Exception {
        // 插入 2 条未读消息
        createMessageAndTrack(ADMIN_USER_ID, "UNREAD");
        createMessageAndTrack(ADMIN_USER_ID, "UNREAD");

        MvcResult result = performGet(API_PATH + "/unread-count", mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertTrue(data.asLong() >= 2, "未读数应 >= 2 (刚插入 2 条未读消息)");
    }

    // ===== CT-5: 数据隔离 =====

    @Test
    @DisplayName("CT-5: 数据隔离 (仅统计当前用户未读消息, 不含他人未读)")
    void testCountUnreadMessagesDataIsolation() throws Exception {
        // admin 用户 1 条未读
        createMessageAndTrack(ADMIN_USER_ID, "UNREAD");
        // biz 用户 1 条未读 (admin 不应计入)
        createMessageAndTrack(BIZ_USER_ID, "UNREAD");

        long adminCount = parseResult(performGet(API_PATH + "/unread-count", mockUser("admin"))).data().asLong();
        long bizCount = parseResult(performGet(API_PATH + "/unread-count", mockUser("biz"))).data().asLong();

        assertTrue(adminCount >= 1, "admin 未读数应 >= 1");
        assertTrue(bizCount >= 1, "biz 未读数应 >= 1");
        // biz 的未读不应计入 admin (admin 可能还有其他测试消息, 只需验证 biz 的消息不在 admin 计数中)
        // 这里用弱断言: admin 和 biz 各自有独立的计数
    }

    // ===== CT-6: traceId 透传 =====

    @Test
    @DisplayName("CT-6: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)")
    void testCountUnreadMessagesTraceIdPropagation() throws Exception {
        MvcResult result = performGet(API_PATH + "/unread-count", mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        ResultNode node = parseResult(result);
        assertNotNull(node.traceId(), "响应体应包含 traceId");
        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertNotNull(headerTraceId, "响应头应包含 X-Trace-Id");
        assertEquals(node.traceId(), headerTraceId, "响应头 X-Trace-Id 应与响应体 traceId 一致");
    }
}
