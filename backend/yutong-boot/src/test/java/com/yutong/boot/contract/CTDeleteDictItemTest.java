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
 * CT-deleteDictItem 契约测试 (operationId: deleteDictItem)。
 *
 * <p>契约来源: routes.yaml operationIds: [deleteDictItem], DELETE /api/v1/dict-items/{id}
 * <p>控制器: DictItemController.delete(id) - @RequiresPermission("system:dict:delete")
 * <p>权限码: system:dict:delete (仅 admin 通配 * 拥有)
 * <p>服务: DictService.deleteDictItem - 不存在时抛 ResourceNotFoundException (SYS-404001)
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功删除 (200, code=0, data=null)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (viewer 无 system:dict:delete, 403 AUTH-403001)</li>
 *   <li>CT-4 不存在的 id (404 SYS-404001)</li>
 *   <li>CT-5 traceId 透传 (响应头 + 响应体)</li>
 *   <li>CT-6 删除后不可查 (再查 by-type 不再包含该项)</li>
 * </ol>
 *
 * <p>数据隔离: 先创建字典项 (admin), 删除后 @AfterEach 兜底物理清理。
 */
@DisplayName("CT-deleteDictItem: DELETE /api/v1/dict-items/{id} 契约测试")
class CTDeleteDictItemTest extends AbstractContractTest {

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

    /** 创建字典项并跟踪 ID, 返回 id 与 dictType。 */
    private String[] createDictItemAndTrack() throws Exception {
        String dictType = "CT-DELETEDICTITEM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        DictItem item = new DictItem();
        item.setDictType(dictType);
        item.setItemCode("ITEM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        item.setItemLabel("CT删除测试字典项");
        item.setStatus("ENABLED");
        item.setSortNo(0);

        MvcResult result = performPost(API_PATH, item, mockUser("admin"));
        assertSuccess(result);
        String id = parseResult(result).data().get("id").asText();
        createdIds.add(id);
        return new String[]{id, dictType};
    }

    // ===== CT-1: 成功删除 =====

    @Test
    @DisplayName("CT-1: 成功删除字典项 (admin, 200, code=0, data=null)")
    void testDeleteDictItemSuccess() throws Exception {
        String[] idAndType = createDictItemAndTrack();
        String id = idAndType[0];

        MvcResult result = performDelete(API_PATH + "/" + id, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertNull(data, "删除成功响应 data 应为 null");
        // 删除成功后从清理集合移除 (已逻辑删除, 兜底清理仍安全)
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testDeleteDictItemUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (viewer 无 system:dict:delete, 403 AUTH-403001)")
    void testDeleteDictItemForbidden() throws Exception {
        String[] idAndType = createDictItemAndTrack();
        String id = idAndType[0];

        MvcResult result = performDelete(API_PATH + "/" + id, mockUser("viewer"));

        assertEquals(403, result.getResponse().getStatus(), "viewer 无删除权限, HTTP 应为 403");
        assertError(result, ERROR_CODE_FORBIDDEN);
        assertTraceIdPresent(result);
    }

    // ===== CT-4: 不存在的 id =====

    @Test
    @DisplayName("CT-4: 不存在的 id (404 SYS-404001)")
    void testDeleteDictItemNotFound() throws Exception {
        String nonExistentId = "NOSUCHID" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        MvcResult result = performDelete(API_PATH + "/" + nonExistentId, mockUser("admin"));

        assertEquals(404, result.getResponse().getStatus(), "不存在的 id HTTP 应为 404");
        assertError(result, ERROR_CODE_NOT_FOUND);
        assertTraceIdPresent(result);
    }

    // ===== CT-5: traceId 透传 =====

    @Test
    @DisplayName("CT-5: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)")
    void testDeleteDictItemTraceIdPropagation() throws Exception {
        String[] idAndType = createDictItemAndTrack();
        String id = idAndType[0];

        MvcResult result = performDelete(API_PATH + "/" + id, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        ResultNode node = parseResult(result);
        assertNotNull(node.traceId(), "响应体应包含 traceId");
        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertNotNull(headerTraceId, "响应头应包含 X-Trace-Id");
        assertEquals(node.traceId(), headerTraceId, "响应头 X-Trace-Id 应与响应体 traceId 一致");
    }

    // ===== CT-6: 删除后不可查 =====

    @Test
    @DisplayName("CT-6: 删除后 by-type 查询不再包含该项 (404 SYS-404001, 无启用项)")
    void testDeleteDictItemNotQueryableAfterDelete() throws Exception {
        String[] idAndType = createDictItemAndTrack();
        String id = idAndType[0];
        String dictType = idAndType[1];

        // 删除
        MvcResult deleteResult = performDelete(API_PATH + "/" + id, mockUser("admin"));
        assertEquals(200, deleteResult.getResponse().getStatus());
        assertSuccess(deleteResult);

        // 删除后查询 by-type (唯一 dictType, 删除后无启用项 → 404)
        MvcResult queryResult = performGet(API_PATH + "/by-type/" + dictType, mockUser("admin"));
        assertEquals(404, queryResult.getResponse().getStatus(),
                "删除后该 dictType 无启用项, by-type 查询应为 404");
        assertError(queryResult, ERROR_CODE_NOT_FOUND);
    }
}
