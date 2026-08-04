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
 * CT-markMessageRead 契约测试 (operationId: markMessageRead)。
 *
 * <p>契约来源: routes.yaml operationIds: [markMessageRead],
 * POST /api/v1/messages/{id}/read
 * <p>控制器: MessageController.markAsRead(id) - @RequiresPermission("system:message:read")
 * <p>权限码: system:message:read (admin/biz/approver/viewer 均拥有, 无权限拒绝用例)
 * <p>服务: MessageService.markAsRead - 检查消息存在且属于当前用户 (receiver_id=当前用户);
 * 不存在或不属于当前用户时抛 ResourceNotFoundException (SYS-404001);
 * 更新 read_status=READ, read_time=now
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功标记已读 (200, code=0, data=null)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 消息不存在 (404 SYS-404001)</li>
 *   <li>CT-4 消息不属于当前用户 (404 SYS-404001, 数据隔离)</li>
 *   <li>CT-5 traceId 透传 (响应头 + 响应体)</li>
 *   <li>CT-6 字段更新生效 (readStatus=READ, readTime 非空)</li>
 *   <li>CT-7 重复标记已读 (幂等, 200 code=0)</li>
 * </ol>
 *
 * <p>数据隔离: 通过 JdbcTemplate 直接插入 sys_message 测试数据, @AfterEach 物理清理。
 */
@DisplayName("CT-markMessageRead: POST /api/v1/messages/{id}/read 契约测试")
class CTMarkMessageReadTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/messages";

    private static final String ADMIN_USER_ID = "01MOCKUSER0000000000000ADMIN";
    private static final String BIZ_USER_ID = "01MOCKUSER00000000000000BIZ";
    private static final String TENANT_ID = "default";
    private static final String ERROR_CODE_NOT_FOUND = "SYS-404001";

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
                "CT标记已读测试", "CT测试内容",
                readStatus, "/dashboard", "{}",
                receiverId, receiverId);
        createdIds.add(id);
        return id;
    }

    // ===== CT-1: 成功标记已读 =====

    @Test
    @DisplayName("CT-1: 成功标记已读 (admin, 200, code=0, data=null)")
    void testMarkMessageReadSuccess() throws Exception {
        String id = createMessageAndTrack(ADMIN_USER_ID, "UNREAD");

        MvcResult result = performPost(API_PATH + "/" + id + "/read", null, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertNull(data, "标记已读成功响应 data 应为 null");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testMarkMessageReadUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 消息不存在 =====

    @Test
    @DisplayName("CT-3: 消息不存在 (404 SYS-404001)")
    void testMarkMessageReadNotFound() throws Exception {
        String nonExistentId = "NOSUCHMSG" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        MvcResult result = performPost(API_PATH + "/" + nonExistentId + "/read", null, mockUser("admin"));

        assertEquals(404, result.getResponse().getStatus(), "消息不存在, HTTP 应为 404");
        assertError(result, ERROR_CODE_NOT_FOUND);
        assertTraceIdPresent(result);
    }

    // ===== CT-4: 消息不属于当前用户 =====

    @Test
    @DisplayName("CT-4: 消息不属于当前用户 (404 SYS-404001, 数据隔离)")
    void testMarkMessageReadNotOwned() throws Exception {
        // 消息属于 biz 用户, admin 尝试标记 → 404
        String id = createMessageAndTrack(BIZ_USER_ID, "UNREAD");

        MvcResult result = performPost(API_PATH + "/" + id + "/read", null, mockUser("admin"));

        assertEquals(404, result.getResponse().getStatus(),
                "消息不属于当前用户, HTTP 应为 404 (数据隔离)");
        assertError(result, ERROR_CODE_NOT_FOUND);
        assertTraceIdPresent(result);
    }

    // ===== CT-5: traceId 透传 =====

    @Test
    @DisplayName("CT-5: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)")
    void testMarkMessageReadTraceIdPropagation() throws Exception {
        String id = createMessageAndTrack(ADMIN_USER_ID, "UNREAD");

        MvcResult result = performPost(API_PATH + "/" + id + "/read", null, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        ResultNode node = parseResult(result);
        assertNotNull(node.traceId(), "响应体应包含 traceId");
        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertNotNull(headerTraceId, "响应头应包含 X-Trace-Id");
        assertEquals(node.traceId(), headerTraceId, "响应头 X-Trace-Id 应与响应体 traceId 一致");
    }

    // ===== CT-6: 字段更新生效 =====

    @Test
    @DisplayName("CT-6: 字段更新生效 (readStatus=READ, readTime 非空)")
    void testMarkMessageReadFieldsEffective() throws Exception {
        String id = createMessageAndTrack(ADMIN_USER_ID, "UNREAD");

        // 标记已读
        MvcResult markResult = performPost(API_PATH + "/" + id + "/read", null, mockUser("admin"));
        assertEquals(200, markResult.getResponse().getStatus());
        assertSuccess(markResult);

        // 查询验证字段已更新
        MvcResult queryResult = performGet(API_PATH + "?pageNo=1&pageSize=50&readStatus=READ", mockUser("admin"));
        assertEquals(200, queryResult.getResponse().getStatus());
        assertSuccess(queryResult);

        com.fasterxml.jackson.databind.node.ArrayNode records =
                (com.fasterxml.jackson.databind.node.ArrayNode) parseResult(queryResult).data().get("records");
        boolean found = false;
        for (JsonNode rec : records) {
            if (id.equals(rec.get("id").asText())) {
                found = true;
                assertEquals("READ", rec.get("readStatus").asText(), "readStatus 应已更新为 READ");
                assertNotNull(rec.get("readTime"), "readTime 不应为 null");
                assertFalse(rec.get("readTime").isNull(), "readTime 应有值");
            }
        }
        assertTrue(found, "标记已读的消息应在 READ 列表中");
    }

    // ===== CT-7: 重复标记已读 (幂等) =====

    @Test
    @DisplayName("CT-7: 重复标记已读 (幂等, 200 code=0)")
    void testMarkMessageReadIdempotent() throws Exception {
        String id = createMessageAndTrack(ADMIN_USER_ID, "UNREAD");

        // 第一次标记
        MvcResult firstResult = performPost(API_PATH + "/" + id + "/read", null, mockUser("admin"));
        assertEquals(200, firstResult.getResponse().getStatus());
        assertSuccess(firstResult);

        // 第二次标记 (幂等, 仍成功)
        MvcResult secondResult = performPost(API_PATH + "/" + id + "/read", null, mockUser("admin"));
        assertEquals(200, secondResult.getResponse().getStatus(), "重复标记已读应仍返回 200 (幂等)");
        assertSuccess(secondResult);
    }
}
