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
 * CT-listMessages 契约测试 (operationId: listMessages)。
 *
 * <p>契约来源: routes.yaml operationIds: [listMessages],
 * GET /api/v1/messages?pageNo=1&pageSize=20&readStatus=UNREAD
 * <p>控制器: MessageController.page(pageNo, pageSize, readStatus) - 无 @RequiresPermission (所有认证用户可访问)
 * <p>数据隔离: MessageService.pageMessages - 查询范围限定当前用户 (receiver_id=当前用户), 按 created_time DESC;
 * 可选 readStatus 过滤 (UNREAD/READ); admin (有 * 或 system:message:read-all 权限) 可读全部站内信。
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功分页查询 (200, code=0, records/total/page/size + traceId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 空结果 (无站内信, total=0, records 空数组)</li>
 *   <li>CT-4 过滤条件 readStatus=UNREAD (仅返回未读消息)</li>
 *   <li>CT-5 数据隔离 (仅返回当前用户的消息, 不含他人消息)</li>
 *   <li>CT-6 排序 (created_time DESC 非递增)</li>
 *   <li>CT-7 标准响应信封 (records/total/page/size 结构齐全)</li>
 *   <li>CT-8 traceId 透传 (响应头 + 响应体)</li>
 * </ol>
 *
 * <p>数据隔离: 通过 JdbcTemplate 直接插入 sys_message 测试数据 (receiver_id=admin mock userId),
 * @AfterEach 物理清理。
 */
@DisplayName("CT-listMessages: GET /api/v1/messages 契约测试")
class CTListMessagesTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/messages";

    /** admin mock 用户的 userId (与 MockAuthAdapter 中一致)。 */
    private static final String ADMIN_USER_ID = "01MOCKUSER0000000000000ADMIN";
    /** biz mock 用户的 userId (与 MockAuthAdapter 中一致)。 */
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
    private String createMessageAndTrack(String receiverId, String readStatus, String titleSuffix) {
        String id = "CTMSG" + UUID.randomUUID().toString().replace("-", "").substring(0, 22);
        jdbcTemplate.update(
                "INSERT INTO sys_message (id, tenant_id, receiver_id, msg_type, title, content, " +
                        "read_status, target_route_id, target_params, created_by, created_time, " +
                        "updated_by, updated_time, deleted, version) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, now(), ?, now(), false, 0)",
                id, TENANT_ID, receiverId, "SYSTEM",
                "CT测试消息-" + titleSuffix, "CT测试内容-" + titleSuffix,
                readStatus, "/dashboard", "{}",
                receiverId, receiverId);
        createdIds.add(id);
        return id;
    }

    // ===== CT-1: 成功分页查询 =====

    @Test
    @DisplayName("CT-1: 成功分页查询 (admin 用户, 200, code=0, records/total/page/size + traceId)")
    void testListMessagesSuccess() throws Exception {
        createMessageAndTrack(ADMIN_USER_ID, "UNREAD", "成功查询");

        MvcResult result = performGet(API_PATH + "?pageNo=1&pageSize=10", mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        ResultNode node = parseResult(result);
        JsonNode data = node.data();
        assertNotNull(data, "data 不应为 null");
        assertNotNull(data.get("records"), "records 不应为 null");
        assertTrue(data.get("records").isArray(), "records 应为数组");
        assertTrue(data.get("total").asLong() >= 1, "total 应 >= 1 (至少包含刚创建的消息)");
        assertEquals(1, data.get("page").asInt(), "page 应为 1");
        assertEquals(10, data.get("size").asInt(), "size 应为 10");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testListMessagesUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 空结果 =====

    @Test
    @DisplayName("CT-3: 空结果 (biz 用户无站内信, total=0, records 空数组)")
    void testListMessagesEmptyResult() throws Exception {
        // biz 用户无测试消息 → 空结果
        MvcResult result = performGet(API_PATH + "?pageNo=1&pageSize=10", mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        ResultNode node = parseResult(result);
        assertEquals(0, node.data().get("total").asLong(), "biz 用户无消息, total 应为 0");
        assertTrue(node.data().get("records").isArray(), "records 应为数组");
        assertEquals(0, node.data().get("records").size(), "records 应为空数组");
    }

    // ===== CT-4: 过滤条件 readStatus=UNREAD =====

    @Test
    @DisplayName("CT-4: 过滤条件 readStatus=UNREAD (仅返回未读消息)")
    void testListMessagesFilterByReadStatus() throws Exception {
        createMessageAndTrack(ADMIN_USER_ID, "UNREAD", "未读");
        createMessageAndTrack(ADMIN_USER_ID, "READ", "已读");

        MvcResult result = performGet(API_PATH + "?pageNo=1&pageSize=20&readStatus=UNREAD", mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        ArrayNode records = (ArrayNode) parseResult(result).data().get("records");

        // 所有返回消息 readStatus 均为 UNREAD
        for (JsonNode rec : records) {
            assertEquals("UNREAD", rec.get("readStatus").asText(), "应仅返回 UNREAD 消息");
        }
    }

    // ===== CT-5: 数据隔离 (仅返回当前用户的消息) =====

    @Test
    @DisplayName("CT-5: 数据隔离 (biz 仅返回当前用户消息, 不含他人消息; admin 有 read-all 不适合测隔离)")
    void testListMessagesDataIsolation() throws Exception {
        // admin 用户的消息 (biz 不应看到)
        createMessageAndTrack(ADMIN_USER_ID, "UNREAD", "admin消息");
        // biz 用户的消息 (biz 应看到)
        String bizMsgId = createMessageAndTrack(BIZ_USER_ID, "UNREAD", "biz消息");

        // biz 用户查询: 无 read-all 权限, 仅看到 receiver_id = 自己 的消息
        MvcResult result = performGet(API_PATH + "?pageNo=1&pageSize=50", mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        ArrayNode records = (ArrayNode) parseResult(result).data().get("records");

        // 验证 biz 的消息在结果中, 且所有消息 receiverId 均为 biz 用户
        boolean foundBizMsg = false;
        for (JsonNode rec : records) {
            assertEquals(BIZ_USER_ID, rec.get("receiverId").asText(),
                    "所有消息的 receiverId 应为当前用户 (biz)");
            if (bizMsgId.equals(rec.get("id").asText())) {
                foundBizMsg = true;
            }
        }
        assertTrue(foundBizMsg, "biz 用户的消息应在结果中");
    }

    // ===== CT-6: 排序 (created_time DESC) =====

    @Test
    @DisplayName("CT-6: 排序 (created_time DESC 非递增)")
    void testListMessagesSortOrder() throws Exception {
        createMessageAndTrack(ADMIN_USER_ID, "UNREAD", "排序1");
        Thread.sleep(10); // 确保 created_time 不同
        createMessageAndTrack(ADMIN_USER_ID, "UNREAD", "排序2");

        MvcResult result = performGet(API_PATH + "?pageNo=1&pageSize=20", mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        ArrayNode records = (ArrayNode) parseResult(result).data().get("records");
        assertTrue(records.size() >= 2, "应至少返回 2 条消息");

        // created_time DESC (非递增: 前一条时间 >= 后一条时间)
        for (int i = 1; i < records.size(); i++) {
            String prev = records.get(i - 1).get("createdTime").asText();
            String curr = records.get(i).get("createdTime").asText();
            assertTrue(prev.compareTo(curr) >= 0,
                    "created_time 应为 DESC 排序 (非递增), 但 " + prev + " < " + curr);
        }
    }

    // ===== CT-7: 标准响应信封 =====

    @Test
    @DisplayName("CT-7: 标准响应信封 (records/total/page/size 结构齐全)")
    void testListMessagesResponseEnvelope() throws Exception {
        createMessageAndTrack(ADMIN_USER_ID, "UNREAD", "信封测试");

        MvcResult result = performGet(API_PATH + "?pageNo=1&pageSize=5", mockUser("admin"));

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

    // ===== CT-8: traceId 透传 =====

    @Test
    @DisplayName("CT-8: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)")
    void testListMessagesTraceIdPropagation() throws Exception {
        createMessageAndTrack(ADMIN_USER_ID, "UNREAD", "traceId测试");

        MvcResult result = performGet(API_PATH + "?pageNo=1&pageSize=10", mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        ResultNode node = parseResult(result);
        assertNotNull(node.traceId(), "响应体应包含 traceId");
        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertNotNull(headerTraceId, "响应头应包含 X-Trace-Id");
        assertEquals(node.traceId(), headerTraceId, "响应头 X-Trace-Id 应与响应体 traceId 一致");
    }
}
