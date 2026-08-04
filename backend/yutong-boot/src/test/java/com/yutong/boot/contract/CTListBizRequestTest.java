package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.yutong.sample.request.dto.SaveBizRequestRequest;
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
 * GA2-L189 CT-listBizRequest 契约测试 (operationId: listBizRequests)。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-listBizRequests 验收标准)
 * <p>策略来源: operation-policies.yaml listBizRequests → read profile (queryPage)
 * <p>契约来源: openapi.yaml GET /api/v1/biz-requests (x-permission: biz:request:list)
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功分页查询 (biz 用户, 200, code=0, records/total/page/size + traceId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 数据范围-approver空白名单 (approver CUSTOM 空白名单, 200, total=0)</li>
 *   <li>CT-4 参数校验 - pageSize=0 越界 (PageRequest 钳制为 20, 200 优雅降级)</li>
 *   <li>CT-5 空结果 (title 不匹配, total=0, records 空数组)</li>
 *   <li>CT-6 过滤条件 title (唯一 title 精确匹配, total=1)</li>
 *   <li>CT-7 过滤条件 status (status=DRAFT 只返回草稿状态)</li>
 *   <li>CT-8 排序 (createdTime DESC 非递增)</li>
 *   <li>CT-9 数据范围-viewer TENANT (viewer 可见本租户数据)</li>
 * </ol>
 *
 * <p>数据隔离: 每个用例独立创建测试数据 (UUID 后缀 title), @AfterEach 物理清理。
 */
@DisplayName("CT-listBizRequest: GET /api/v1/biz-requests 契约测试")
class CTListBizRequestTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/biz-requests";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final java.util.Set<String> createdRequestIds = ConcurrentHashMap.newKeySet();

    @AfterEach
    void cleanupTestData() {
        for (String id : createdRequestIds) {
            jdbcTemplate.update("DELETE FROM sys_operation_log WHERE biz_type = 'biz_request' AND biz_id = ?", id);
            jdbcTemplate.update("DELETE FROM biz_approval_record WHERE request_id = ?", id);
            jdbcTemplate.update("DELETE FROM biz_request_item WHERE request_id = ?", id);
            jdbcTemplate.update("DELETE FROM biz_request WHERE id = ?", id);
        }
        createdRequestIds.clear();
    }

    /** 构造合法 SaveBizRequestRequest (唯一 title, 指定前缀)。 */
    private SaveBizRequestRequest buildValidRequest(String titlePrefix) {
        SaveBizRequestRequest req = new SaveBizRequestRequest();
        req.setTitle(titlePrefix + "-" + UUID.randomUUID().toString().substring(0, 8));
        req.setCustomerId("01CTCUSTOMER0000000000000001");
        req.setCustomerNameSnapshot("CT列表测试客户");
        req.setApplyReason("CT列表测试申请原因");
        return req;
    }

    /** 创建申请单草稿并跟踪 ID, 返回申请单 title。 */
    private String createRequestAndTrack(String titlePrefix) throws Exception {
        SaveBizRequestRequest request = buildValidRequest(titlePrefix);
        MvcResult result = performPost(API_PATH, request, mockUser("biz"));
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertNotNull(data, "创建响应 data 不应为 null");
        String id = data.get("id").asText();
        createdRequestIds.add(id);
        return request.getTitle();
    }

    // ===== CT-1: 成功分页查询 =====

    @Test
    @DisplayName("CT-1: 成功分页查询 (biz 用户, 200, code=0, records/total/page/size + traceId)")
    void testListBizRequestSuccess() throws Exception {
        String title = createRequestAndTrack("CT-L189-L");

        MvcResult result = performGet(API_PATH + "?pageNo=1&pageSize=10&title=" + title, mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        ResultNode node = parseResult(result);
        JsonNode data = node.data();
        assertNotNull(data, "data 不应为 null");
        assertNotNull(data.get("records"), "records 不应为 null");
        assertTrue(data.get("records").isArray(), "records 应为数组");
        assertTrue(data.get("total").asLong() >= 1, "total 应 >= 1 (至少包含刚创建的申请单)");
        assertEquals(1, data.get("page").asInt(), "page 应为 1");
        assertEquals(10, data.get("size").asInt(), "size 应为 10");

        // 验证刚创建的申请单在结果中
        ArrayNode records = (ArrayNode) data.get("records");
        boolean found = false;
        for (JsonNode rec : records) {
            if (title.equals(rec.get("title").asText())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "结果中应包含刚创建的申请单 (title=" + title + ")");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testListBizRequestUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 数据范围-approver 空白名单 =====

    @Test
    @DisplayName("CT-3: 数据范围 (approver CUSTOM 空白名单, 200, total=0, 空结果)")
    void testListBizRequestApproverEmptyDataScope() throws Exception {
        String title = createRequestAndTrack("CT-L189-L-DS");

        // approver CUSTOM 空白名单 → DataScope 过滤后无可见数据
        MvcResult result = performGet(API_PATH + "?pageNo=1&pageSize=10&title=" + title, mockUser("approver"));

        assertEquals(200, result.getResponse().getStatus(), "approver 有 biz:request:list, HTTP 应为 200");
        assertSuccess(result);
        ResultNode node = parseResult(result);
        assertEquals(0, node.data().get("total").asLong(), "approver CUSTOM 空白名单应返回 total=0");
        assertTrue(node.data().get("records").isArray(), "records 应为数组");
        assertEquals(0, node.data().get("records").size(), "records 应为空数组");
    }

    // ===== CT-4: 参数校验 - pageSize 越界 =====

    @Test
    @DisplayName("CT-4: 参数校验 - pageSize=0 越界 (PageRequest 钳制为 20, 200 优雅降级)")
    void testListBizRequestPageSizeOutOfRange() throws Exception {
        MvcResult result = performGet(API_PATH + "?pageNo=1&pageSize=0", mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus(), "pageSize 越界应优雅降级, HTTP 200");
        assertSuccess(result);
        ResultNode node = parseResult(result);
        // PageRequest: size < 1 → 钳制为 20
        assertEquals(20, node.data().get("size").asInt(), "pageSize=0 应钳制为默认值 20");
    }

    // ===== CT-5: 空结果 =====

    @Test
    @DisplayName("CT-5: 空结果 (title 不匹配, total=0, records 空数组)")
    void testListBizRequestEmptyResult() throws Exception {
        String nonExistentTitle = "NOSUCHTITLE-CT-L189-" + UUID.randomUUID().toString().substring(0, 8);

        MvcResult result = performGet(API_PATH + "?pageNo=1&pageSize=10&title=" + nonExistentTitle, mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        ResultNode node = parseResult(result);
        assertEquals(0, node.data().get("total").asLong(), "不匹配的 title 应返回 total=0");
        assertTrue(node.data().get("records").isArray(), "records 应为数组");
        assertEquals(0, node.data().get("records").size(), "records 应为空数组");
    }

    // ===== CT-6: 过滤条件 title =====

    @Test
    @DisplayName("CT-6: 过滤条件 title (唯一 title 精确匹配, total=1)")
    void testListBizRequestFilterByTitle() throws Exception {
        String title = createRequestAndTrack("CT-L189-L-KW");

        MvcResult result = performGet(API_PATH + "?pageNo=1&pageSize=10&title=" + title, mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        ResultNode node = parseResult(result);
        assertEquals(1, node.data().get("total").asLong(), "唯一 title 应匹配 total=1");
        ArrayNode records = (ArrayNode) node.data().get("records");
        assertEquals(1, records.size(), "records 应只有 1 条");
        assertEquals(title, records.get(0).get("title").asText(), "返回的申请单 title 应匹配");
    }

    // ===== CT-7: 过滤条件 status =====

    @Test
    @DisplayName("CT-7: 过滤条件 status (status=DRAFT 只返回草稿状态申请单)")
    void testListBizRequestFilterByStatus() throws Exception {
        // 创建 2 个 DRAFT 状态申请单 (新建默认为 DRAFT)
        String title1 = createRequestAndTrack("CT-L189-L-ST");
        String title2 = createRequestAndTrack("CT-L189-L-ST");

        // 查询 status=DRAFT + title 前缀 → 只返回 DRAFT 状态
        MvcResult result = performGet(
                API_PATH + "?pageNo=1&pageSize=50&title=CT-L189-L-ST&status=DRAFT",
                mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        ResultNode node = parseResult(result);
        ArrayNode records = (ArrayNode) node.data().get("records");
        assertTrue(records.size() >= 2, "应至少返回 2 条 DRAFT 申请单");

        // 验证所有返回的记录 requestStatus 均为 DRAFT
        for (JsonNode rec : records) {
            assertEquals("DRAFT", rec.get("requestStatus").asText(),
                    "过滤 status=DRAFT 后所有记录 requestStatus 应为 DRAFT");
        }
    }

    // ===== CT-8: 排序 =====

    @Test
    @DisplayName("CT-8: 排序 (createdTime DESC, 非递增)")
    void testListBizRequestSortOrder() throws Exception {
        // 创建 2 个申请单使用共同前缀, 按创建顺序
        String title1 = createRequestAndTrack("CT-L189-L-SO");
        Thread.sleep(10); // 确保时间差
        String title2 = createRequestAndTrack("CT-L189-L-SO");

        MvcResult result = performGet(
                API_PATH + "?pageNo=1&pageSize=50&title=CT-L189-L-SO", mockUser("biz"));

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

    // ===== CT-9: 数据范围-viewer TENANT =====

    @Test
    @DisplayName("CT-9: 数据范围 (viewer TENANT 可见本租户数据)")
    void testListBizRequestViewerDataScope() throws Exception {
        // biz 创建申请单
        String title = createRequestAndTrack("CT-L189-L-VDS");

        // viewer (TENANT 范围) 查询, 应能看到同租户数据
        MvcResult result = performGet(API_PATH + "?pageNo=1&pageSize=10&title=" + title, mockUser("viewer"));

        assertEquals(200, result.getResponse().getStatus(), "viewer 应能查询 (有 biz:request:list)");
        assertSuccess(result);
        ResultNode node = parseResult(result);
        assertTrue(node.data().get("total").asLong() >= 1, "viewer TENANT 范围应可见本租户数据");

        ArrayNode records = (ArrayNode) node.data().get("records");
        boolean found = false;
        for (JsonNode rec : records) {
            if (title.equals(rec.get("title").asText())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "viewer 应能看到 biz 创建的申请单 (TENANT 范围)");
    }
}
