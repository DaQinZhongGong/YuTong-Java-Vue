package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
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
 * CT-createDictItem 契约测试 (operationId: createDictItem)。
 *
 * <p>契约来源: routes.yaml operationIds: [createDictItem], POST /api/v1/dict-items
 * <p>控制器: DictItemController.create(item) - @RequiresPermission("system:dict:add")
 * <p>权限码: system:dict:add (仅 admin 通配 * 拥有)
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功创建 (200, code=0, 返回 id/dictType/itemCode/itemLabel + traceId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (viewer 无 system:dict:add, 403 AUTH-403001)</li>
 *   <li>CT-4 traceId 透传 (响应头 + 响应体)</li>
 *   <li>CT-5 标准错误信封 (权限拒绝场景, code/message/traceId 字段齐全)</li>
 *   <li>CT-6 字段回显 (dictType/itemCode/itemLabel/status/sortNo)</li>
 * </ol>
 *
 * <p>数据隔离: 每个用例使用唯一 itemCode (UUID 后缀), @AfterEach 物理清理。
 */
@DisplayName("CT-createDictItem: POST /api/v1/dict-items 契约测试")
class CTCreateDictItemTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/dict-items";
    private static final String ERROR_CODE_FORBIDDEN = "AUTH-403001";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final java.util.Set<String> createdIds = ConcurrentHashMap.newKeySet();

    @AfterEach
    void cleanupTestData() {
        for (String id : createdIds) {
            jdbcTemplate.update("DELETE FROM sys_dict_item WHERE id = ?", id);
        }
        createdIds.clear();
    }

    /** 构造合法 DictItem 请求体 (唯一 itemCode)。 */
    private DictItem buildValidDictItem() {
        DictItem item = new DictItem();
        item.setDictType("CT-CREATEDICTITEM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        item.setItemCode("ITEM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        item.setItemLabel("CT测试字典项创建");
        item.setStatus("ENABLED");
        item.setSortNo(0);
        return item;
    }

    /** 从成功响应中提取 ID 并记录到清理集合。 */
    private String extractAndTrackId(MvcResult result) throws Exception {
        JsonNode data = parseResult(result).data();
        assertNotNull(data, "成功响应 data 不应为 null");
        String id = data.get("id").asText();
        createdIds.add(id);
        return id;
    }

    // ===== CT-1: 成功创建 =====

    @Test
    @DisplayName("CT-1: 成功创建字典项 (admin 用户, 200, code=0, 返回 id/dictType/itemCode)")
    void testCreateDictItemSuccess() throws Exception {
        DictItem request = buildValidDictItem();

        MvcResult result = performPost(API_PATH, request, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertNotNull(data.get("id"), "返回的 id 不应为 null");
        assertEquals(request.getDictType(), data.get("dictType").asText());
        assertEquals(request.getItemCode(), data.get("itemCode").asText());
        assertEquals(request.getItemLabel(), data.get("itemLabel").asText());
        assertEquals("ENABLED", data.get("status").asText());

        extractAndTrackId(result);
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testCreateDictItemUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (viewer 无 system:dict:add, 403 AUTH-403001)")
    void testCreateDictItemForbidden() throws Exception {
        DictItem request = buildValidDictItem();

        MvcResult result = performPost(API_PATH, request, mockUser("viewer"));

        assertEquals(403, result.getResponse().getStatus(), "viewer 无写权限, HTTP 应为 403");
        assertError(result, ERROR_CODE_FORBIDDEN);
        assertTraceIdPresent(result);
    }

    // ===== CT-4: traceId 透传 =====

    @Test
    @DisplayName("CT-4: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)")
    void testCreateDictItemTraceIdPropagation() throws Exception {
        DictItem request = buildValidDictItem();

        MvcResult result = performPost(API_PATH, request, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        ResultNode node = parseResult(result);
        assertNotNull(node.traceId(), "响应体应包含 traceId");
        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertNotNull(headerTraceId, "响应头应包含 X-Trace-Id");
        assertEquals(node.traceId(), headerTraceId, "响应头 X-Trace-Id 应与响应体 traceId 一致");

        extractAndTrackId(result);
    }

    // ===== CT-5: 标准错误信封结构 =====

    @Test
    @DisplayName("CT-5: 标准错误信封 (权限拒绝场景, code/message/traceId 字段齐全)")
    void testCreateDictItemErrorEnvelopeStructure() throws Exception {
        DictItem request = buildValidDictItem();

        MvcResult result = performPost(API_PATH, request, mockUser("viewer"));

        ResultNode node = parseResult(result);
        assertNotNull(node.code(), "错误信封必须包含 code 字段");
        assertNotEquals("0", node.code(), "错误响应 code 不应为 0");
        assertEquals(ERROR_CODE_FORBIDDEN, node.code(), "应返回 AUTH-403001");
        assertNotNull(node.message(), "错误信封必须包含 message 字段");
        assertNotNull(node.traceId(), "错误信封必须包含 traceId 字段");
        assertTrue(node.traceId().length() >= 16 && node.traceId().length() <= 64,
                "traceId 长度应在 16-64 之间");
    }

    // ===== CT-6: 字段回显 =====

    @Test
    @DisplayName("CT-6: 字段回显 (dictType/itemCode/itemLabel/status/sortNo)")
    void testCreateDictItemFieldEcho() throws Exception {
        DictItem request = buildValidDictItem();
        request.setSortNo(42);

        MvcResult result = performPost(API_PATH, request, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertEquals(request.getDictType(), data.get("dictType").asText(), "dictType 应回显");
        assertEquals(request.getItemCode(), data.get("itemCode").asText(), "itemCode 应回显");
        assertEquals(request.getItemLabel(), data.get("itemLabel").asText(), "itemLabel 应回显");
        assertEquals("ENABLED", data.get("status").asText(), "status 应回显");
        assertEquals(42, data.get("sortNo").asInt(), "sortNo 应回显");
        assertNotNull(data.get("version"), "version 不应为 null");

        extractAndTrackId(result);
    }
}
