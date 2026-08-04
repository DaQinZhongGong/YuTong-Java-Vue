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
 * CT-updateDictItem 契约测试 (operationId: updateDictItem)。
 *
 * <p>契约来源: routes.yaml operationIds: [updateDictItem], PUT /api/v1/dict-items/{id}
 * <p>控制器: DictItemController.update(id, item) - @RequiresPermission("system:dict:edit")
 * <p>权限码: system:dict:edit (仅 admin 通配 * 拥有)
 * <p>服务: DictService.updateDictItem - 不存在时抛 ResourceNotFoundException (SYS-404001)
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功更新 (200, code=0, 返回更新后字段 + traceId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (viewer 无 system:dict:edit, 403 AUTH-403001)</li>
 *   <li>CT-4 不存在的 id (404 SYS-404001)</li>
 *   <li>CT-5 traceId 透传 (响应头 + 响应体)</li>
 *   <li>CT-6 字段更新生效 (itemLabel/sortNo/status 变更)</li>
 * </ol>
 *
 * <p>数据隔离: 先创建字典项 (admin), 更新后 @AfterEach 物理清理。
 */
@DisplayName("CT-updateDictItem: PUT /api/v1/dict-items/{id} 契约测试")
class CTUpdateDictItemTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/dict-items";
    private static final String ERROR_CODE_FORBIDDEN = "AUTH-403001";
    private static final String ERROR_CODE_NOT_FOUND = "SYS-404001";

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

    /** 创建字典项并跟踪 ID。 */
    private String createDictItemAndTrack() throws Exception {
        DictItem item = new DictItem();
        item.setDictType("CT-UPDATEDICTITEM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        item.setItemCode("ITEM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        item.setItemLabel("CT更新前标签");
        item.setStatus("ENABLED");
        item.setSortNo(0);

        MvcResult result = performPost(API_PATH, item, mockUser("admin"));
        assertSuccess(result);
        String id = parseResult(result).data().get("id").asText();
        createdIds.add(id);
        return id;
    }

    // ===== CT-1: 成功更新 =====

    @Test
    @DisplayName("CT-1: 成功更新字典项 (admin, 200, code=0, 返回更新后字段)")
    void testUpdateDictItemSuccess() throws Exception {
        String id = createDictItemAndTrack();

        DictItem update = new DictItem();
        update.setItemLabel("CT更新后标签");
        update.setStatus("DISABLED");
        update.setSortNo(99);

        MvcResult result = performPut(API_PATH + "/" + id, update, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertEquals("CT更新后标签", data.get("itemLabel").asText(), "itemLabel 应已更新");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testUpdateDictItemUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (viewer 无 system:dict:edit, 403 AUTH-403001)")
    void testUpdateDictItemForbidden() throws Exception {
        String id = createDictItemAndTrack();

        DictItem update = new DictItem();
        update.setItemLabel("viewer尝试更新");

        MvcResult result = performPut(API_PATH + "/" + id, update, mockUser("viewer"));

        assertEquals(403, result.getResponse().getStatus(), "viewer 无写权限, HTTP 应为 403");
        assertError(result, ERROR_CODE_FORBIDDEN);
        assertTraceIdPresent(result);
    }

    // ===== CT-4: 不存在的 id =====

    @Test
    @DisplayName("CT-4: 不存在的 id (404 SYS-404001)")
    void testUpdateDictItemNotFound() throws Exception {
        String nonExistentId = "NOSUCHID" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        DictItem update = new DictItem();
        update.setItemLabel("不存在的字典项");

        MvcResult result = performPut(API_PATH + "/" + nonExistentId, update, mockUser("admin"));

        assertEquals(404, result.getResponse().getStatus(), "不存在的 id HTTP 应为 404");
        assertError(result, ERROR_CODE_NOT_FOUND);
        assertTraceIdPresent(result);
    }

    // ===== CT-5: traceId 透传 =====

    @Test
    @DisplayName("CT-5: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)")
    void testUpdateDictItemTraceIdPropagation() throws Exception {
        String id = createDictItemAndTrack();

        DictItem update = new DictItem();
        update.setItemLabel("CT-traceId-更新");

        MvcResult result = performPut(API_PATH + "/" + id, update, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        ResultNode node = parseResult(result);
        assertNotNull(node.traceId(), "响应体应包含 traceId");
        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertNotNull(headerTraceId, "响应头应包含 X-Trace-Id");
        assertEquals(node.traceId(), headerTraceId, "响应头 X-Trace-Id 应与响应体 traceId 一致");
    }

    // ===== CT-6: 字段更新生效 =====

    @Test
    @DisplayName("CT-6: 字段更新生效 (itemLabel/sortNo/status 变更)")
    void testUpdateDictItemFieldsEffective() throws Exception {
        String id = createDictItemAndTrack();

        DictItem update = new DictItem();
        update.setItemLabel("CT-字段更新生效-新标签");
        update.setStatus("DISABLED");
        update.setSortNo(77);

        MvcResult result = performPut(API_PATH + "/" + id, update, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertEquals("CT-字段更新生效-新标签", data.get("itemLabel").asText(), "itemLabel 应已更新");
        assertEquals("DISABLED", data.get("status").asText(), "status 应已更新为 DISABLED");
        assertEquals(77, data.get("sortNo").asInt(), "sortNo 应已更新为 77");
    }
}
