package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.yutong.system.dict.domain.DictItem;
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
 * CT-listEnabledDictItemsByType 契约测试 (operationId: listEnabledDictItemsByType)。
 *
 * <p>契约来源: routes.yaml operationIds: [listEnabledDictItemsByType],
 * GET /api/v1/dict-items/by-type/{dictType}
 * <p>控制器: DictItemController.listByType(dictType) - 无 @RequiresPermission (所有认证用户可访问)
 * <p>服务: DictService.listByType - 仅返回 status=ENABLED 字典项, 按 sortNo ASC;
 * 无启用项时抛 ResourceNotFoundException (SYS-404001)
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功查询启用字典项 (200, code=0, list 非空)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 不存在的 dictType (404 SYS-404001, 无启用项)</li>
 *   <li>CT-4 排序 (sortNo ASC 非递减)</li>
 *   <li>CT-5 traceId 透传 (响应头 + 响应体)</li>
 *   <li>CT-6 仅返回 ENABLED 字典项 (DISABLED 项被过滤)</li>
 * </ol>
 *
 * <p>数据隔离: 通过 POST /api/v1/dict-items 创建测试数据 (admin), @AfterEach 物理清理。
 */
@DisplayName("CT-listEnabledDictItemsByType: GET /api/v1/dict-items/by-type/{dictType} 契约测试")
class CTListEnabledDictItemsByTypeTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/dict-items";
    private static final String ERROR_CODE_NOT_FOUND = "SYS-404001";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final java.util.Set<String> createdItemIds = ConcurrentHashMap.newKeySet();

    @AfterEach
    void cleanupTestData() {
        for (String id : createdItemIds) {
            jdbcTemplate.update("DELETE FROM sys_dict_item WHERE id = ?", id);
        }
        createdItemIds.clear();
    }

    /** 构造合法 DictItem (指定 dictType + sortNo + status, 唯一 itemCode)。 */
    private DictItem buildDictItem(String dictType, int sortNo, String status) {
        DictItem item = new DictItem();
        item.setDictType(dictType);
        item.setItemCode("ITEM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        item.setItemLabel("CT字典项按类型查询");
        item.setStatus(status);
        item.setSortNo(sortNo);
        return item;
    }

    /** 创建字典项并跟踪 ID, 返回 itemCode。 */
    private String createDictItemAndTrack(String dictType, int sortNo, String status) throws Exception {
        DictItem request = buildDictItem(dictType, sortNo, status);
        MvcResult result = performPost(API_PATH, request, mockUser("admin"));
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        String id = data.get("id").asText();
        createdItemIds.add(id);
        return request.getItemCode();
    }

    private String uniqueDictType() {
        return "CT-BYTYPE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    // ===== CT-1: 成功查询启用字典项 =====

    @Test
    @DisplayName("CT-1: 成功查询启用字典项 (200, code=0, list 非空)")
    void testListEnabledDictItemsByTypeSuccess() throws Exception {
        String dictType = uniqueDictType();
        createDictItemAndTrack(dictType, 0, "ENABLED");

        MvcResult result = performGet(API_PATH + "/by-type/" + dictType, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");
        assertTrue(data.isArray(), "data 应为数组");
        assertTrue(data.size() >= 1, "应至少返回 1 条启用字典项");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testListEnabledDictItemsByTypeUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 不存在的 dictType =====

    @Test
    @DisplayName("CT-3: 不存在的 dictType (404 SYS-404001, 无启用项)")
    void testListEnabledDictItemsByTypeNotFound() throws Exception {
        String nonExistent = "NOSUCHTYPE-CT-" + UUID.randomUUID().toString().substring(0, 8);

        MvcResult result = performGet(API_PATH + "/by-type/" + nonExistent, mockUser("admin"));

        assertEquals(404, result.getResponse().getStatus(), "无启用项 HTTP 应为 404");
        assertError(result, ERROR_CODE_NOT_FOUND);
        assertTraceIdPresent(result);
    }

    // ===== CT-4: 排序 =====

    @Test
    @DisplayName("CT-4: 排序 (sortNo ASC 非递减)")
    void testListEnabledDictItemsByTypeSortOrder() throws Exception {
        String dictType = uniqueDictType();
        createDictItemAndTrack(dictType, 10, "ENABLED");
        createDictItemAndTrack(dictType, 1, "ENABLED");

        MvcResult result = performGet(API_PATH + "/by-type/" + dictType, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        ArrayNode records = (ArrayNode) parseResult(result).data();
        assertTrue(records.size() >= 2, "应至少返回 2 条记录");

        // sortNo ASC (非递减: 前一条 sortNo <= 后一条 sortNo)
        for (int i = 1; i < records.size(); i++) {
            int prev = records.get(i - 1).get("sortNo").asInt();
            int curr = records.get(i).get("sortNo").asInt();
            assertTrue(prev <= curr, "sortNo 应为 ASC 排序 (非递减), 但 " + prev + " > " + curr);
        }
    }

    // ===== CT-5: traceId 透传 =====

    @Test
    @DisplayName("CT-5: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)")
    void testListEnabledDictItemsByTypeTraceIdPropagation() throws Exception {
        String dictType = uniqueDictType();
        createDictItemAndTrack(dictType, 0, "ENABLED");

        MvcResult result = performGet(API_PATH + "/by-type/" + dictType, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        ResultNode node = parseResult(result);
        assertNotNull(node.traceId(), "响应体应包含 traceId");
        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertNotNull(headerTraceId, "响应头应包含 X-Trace-Id");
        assertEquals(node.traceId(), headerTraceId, "响应头 X-Trace-Id 应与响应体 traceId 一致");
    }

    // ===== CT-6: 仅返回 ENABLED 字典项 =====

    @Test
    @DisplayName("CT-6: 仅返回 ENABLED 字典项 (DISABLED 项被过滤)")
    void testListEnabledDictItemsByTypeOnlyEnabled() throws Exception {
        String dictType = uniqueDictType();
        String enabledCode = createDictItemAndTrack(dictType, 0, "ENABLED");
        createDictItemAndTrack(dictType, 1, "DISABLED");

        MvcResult result = performGet(API_PATH + "/by-type/" + dictType, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        ArrayNode records = (ArrayNode) parseResult(result).data();

        // 所有返回项 status 均为 ENABLED
        for (JsonNode rec : records) {
            assertEquals("ENABLED", rec.get("status").asText(), "应仅返回 ENABLED 字典项");
        }
        // 验证 ENABLED 项在结果中
        boolean foundEnabled = false;
        for (JsonNode rec : records) {
            if (enabledCode.equals(rec.get("itemCode").asText())) foundEnabled = true;
        }
        assertTrue(foundEnabled, "ENABLED 字典项应在结果中");
    }
}
