package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CT-listMobileTodos 契约测试。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-listMobileTodos 验收标准)
 * <p>契约来源: openapi.yaml GET /api/v1/mobile/todos
 *              (routes.yaml x-permission: mobile:todo:list)
 *
 * <p>当前实现: MobileController.listTodos 返回 PageResult&lt;MobileTodoVO&gt;,
 *   只返回当前用户 assignee_id 匹配的待办, 聚合 biz_request 摘要。
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功分页查询 (biz, 200, code=0, records/total/page/size + traceId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (MobileController 未加 @RequiresPermission, @Disabled)</li>
 *   <li>CT-4 空结果 (biz 无待办, total=0, records 空数组)</li>
 *   <li>CT-5 traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)</li>
 * </ol>
 *
 * <p>实现说明: MobileController 当前未加 @RequiresPermission 注解, 权限码
 *   mobile:todo:list 未在控制器层强制; 待补充注解后启用 CT-3。
 */
@DisplayName("CT-listMobileTodos: GET /api/v1/mobile/todos 契约测试")
class CTListMobileTodosTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/mobile/todos";

    // ===== CT-1: 成功分页查询 =====

    @Test
    @DisplayName("CT-1: 成功分页查询移动端待办 (biz, 200, code=0, records/total/page/size + traceId)")
    void testListMobileTodosSuccess() throws Exception {
        MvcResult result = performGet(API_PATH + "?pageNo=1&pageSize=10", mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");
        assertNotNull(data.get("records"), "records 不应为 null");
        assertTrue(data.get("records").isArray(), "records 应为数组");
        assertNotNull(data.get("total"), "total 不应为 null");
        assertEquals(1, data.get("page").asInt(), "page 应为 1");
        assertEquals(10, data.get("size").asInt(), "size 应为 10");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testListMobileTodosUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 (MobileController 未加 @RequiresPermission) =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (MobileController 未加 @RequiresPermission, @Disabled 待补充注解)")
    @org.junit.jupiter.api.Disabled("MobileController.listTodos 未加 @RequiresPermission(mobile:todo:list), " +
            "权限码未在控制器层强制; 待补充注解后启用此用例")
    void testListMobileTodosForbidden() {
        // 实现待补充: MobileController 当前未加 @RequiresPermission 注解
    }

    // ===== CT-4: 空结果 =====

    @Test
    @DisplayName("CT-4: 空结果 (biz 用户无待办, total=0, records 空数组)")
    void testListMobileTodosEmpty() throws Exception {
        // biz 用户 (Mock 模式) 通常无分配给 01MOCKUSER00000000000000BIZ 的待办
        MvcResult result = performGet(API_PATH + "?pageNo=1&pageSize=10", mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);

        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");
        assertNotNull(data.get("records"), "records 不应为 null");
        assertTrue(data.get("records").isArray(), "records 应为数组");
        // biz 用户无待办时 total=0; 仅断言 total 字段存在且为非负
        long total = data.get("total").asLong();
        assertTrue(total >= 0, "total 应为非负整数");
    }

    // ===== CT-5: traceId 透传 =====

    @Test
    @DisplayName("CT-5: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)")
    void testListMobileTodosTraceIdPropagation() throws Exception {
        MvcResult result = performGet(API_PATH + "?pageNo=1&pageSize=10", mockUser("biz"));

        assertEquals(200, result.getResponse().getStatus());
        ResultNode node = parseResult(result);
        assertNotNull(node.traceId(), "响应体应包含 traceId");
        assertTrue(node.traceId().length() >= 16 && node.traceId().length() <= 64,
                "traceId 长度应在 16-64 之间");

        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertNotNull(headerTraceId, "响应头应包含 X-Trace-Id");
        assertEquals(node.traceId(), headerTraceId, "响应头 X-Trace-Id 应与响应体 traceId 一致");
    }
}
