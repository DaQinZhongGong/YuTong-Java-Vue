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
 * GA2-L185 CT-listDictItem 契约测试 (operationId: listDictItems)。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-listDictItems 验收标准)
 * <p>策略来源: operation-policies.yaml listDictItems → read profile (queryPage)
 * <p>契约来源: openapi.yaml GET /api/v1/dict-items (x-permission: system:dict-item:list)
 *
 * <p>覆盖矩阵 (查询接口):
 * <ol>
 *   <li>CT-1 成功分页查询 (200, code=0, records/total/page/size + traceId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (viewer 无 system:dict-item:list, 403 AUTH-403001)</li>
 *   <li>CT-4 参数校验 - size 越界 (0 → 钳制为 20, 200 优雅降级)</li>
 *   <li>CT-5 空结果 (dictType 不匹配, total=0)</li>
 *   <li>CT-6 过滤条件 dictType (唯一 dictType 匹配, total>=1)</li>
 *   <li>CT-7 排序 (sortNo ASC 非递减)</li>
 *   <li>CT-8 数据范围 (admin ALL 可见本租户数据)</li>
 *   <li>CT-9 标准响应信封 (records/total/page/size 结构)</li>
 * </ol>
 *
 * <p>数据隔离: 每个用例独立创建测试数据 (UUID 后缀 dictType/itemCode), @AfterEach 物理清理。
 *
 * <p>注意: DictItemController 分页参数名为 page/size; dictType 精确匹配过滤。
 * 测试数据通过 POST /api/v1/dict-items (admin 通配权限) 创建, 无需预先存在 sys_dict_type 记录 (无外键)。
 */
@DisplayName("CT-listDictItem: GET /api/v1/dict-items 契约测试")
class CTListDictItemTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/dict-items";
    private static final String ERROR_CODE_FORBIDDEN = "AUTH-403001";

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

    /** 构造合法 DictItem (指定 dictType + sortNo, 唯一 itemCode)。 */
    private DictItem buildValidDictItem(String dictType, int sortNo) {
        DictItem item = new DictItem();
        item.setDictType(dictType);
        item.setItemCode("ITEM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        item.setItemLabel("CT测试字典项");
        item.setStatus("ENABLED");
        item.setSortNo(sortNo);
        return item;
    }

    /** 创建字典项并跟踪 ID, 返回 itemCode。 */
    private String createDictItemAndTrack(String dictType, int sortNo) throws Exception {
        DictItem request = buildValidDictItem(dictType, sortNo);
        MvcResult result = performPost(API_PATH, request, mockUser("admin"));
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertNotNull(data, "创建响应 data 不应为 null");
        String id = data.get("id").asText();
        createdItemIds.add(id);
        return request.getItemCode();
    }

    /** 生成唯一 dictType 字符串。 */
    private String uniqueDictType() {
        return "CT-L185-DI-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    // ===== CT-1: 成功分页查询 =====

    @Test
    @DisplayName("CT-1: 成功分页查询 (admin 用户, 200, code=0, records/total/page/size + traceId)")
    void testListDictItemSuccess() throws Exception {
        String dictType = uniqueDictType();
        String itemCode = createDictItemAndTrack(dictType, 0);

        MvcResult result = performGet(API_PATH + "?page=1&size=10&dictType=" + dictType, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        ResultNode node = parseResult(result);
        JsonNode data = node.data();
        assertNotNull(data, "data 不应为 null");
        assertNotNull(data.get("records"), "records 不应为 null");
        assertTrue(data.get("records").isArray(), "records 应为数组");
        assertTrue(data.get("total").asLong() >= 1, "total 应 >= 1 (至少包含刚创建的字典项)");
        assertEquals(1, data.get("page").asInt(), "page 应为 1");
        assertEquals(10, data.get("size").asInt(), "size 应为 10");

        // 验证刚创建的字典项在结果中
        ArrayNode records = (ArrayNode) data.get("records");
        boolean found = false;
        for (JsonNode rec : records) {
            if (itemCode.equals(rec.get("itemCode").asText())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "结果中应包含刚创建的字典项 (itemCode=" + itemCode + ")");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testListDictItemUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (viewer 无 system:dict-item:list, 403 AUTH-403001)")
    void testListDictItemForbidden() throws Exception {
        MvcResult result = performGet(API_PATH + "?page=1&size=10", mockUser("viewer"));

        assertEquals(403, result.getResponse().getStatus(), "viewer 无列表权限, HTTP 应为 403");
        assertError(result, ERROR_CODE_FORBIDDEN);
        assertTraceIdPresent(result);
    }

    // ===== CT-4: 参数校验 - size 越界 =====

    @Test
    @DisplayName("CT-4: 参数校验 - size=0 越界 (PageRequest 钳制为 20, 200 优雅降级)")
    void testListDictItemSizeOutOfRange() throws Exception {
        String dictType = uniqueDictType();
        createDictItemAndTrack(dictType, 0);

        MvcResult result = performGet(API_PATH + "?page=1&size=0&dictType=" + dictType, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "size 越界应优雅降级, HTTP 200");
        assertSuccess(result);
        ResultNode node = parseResult(result);
        // PageRequest: size < 1 → 钳制为 20
        assertEquals(20, node.data().get("size").asInt(), "size=0 应钳制为默认值 20");
    }

    // ===== CT-5: 空结果 =====

    @Test
    @DisplayName("CT-5: 空结果 (dictType 不匹配, total=0, records 空数组)")
    void testListDictItemEmptyResult() throws Exception {
        String nonExistentDictType = "NOSUCHTYPE-CT-L185-" + UUID.randomUUID().toString().substring(0, 8);

        MvcResult result = performGet(API_PATH + "?page=1&size=10&dictType=" + nonExistentDictType, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        ResultNode node = parseResult(result);
        assertEquals(0, node.data().get("total").asLong(), "不匹配的 dictType 应返回 total=0");
        assertTrue(node.data().get("records").isArray(), "records 应为数组");
        assertEquals(0, node.data().get("records").size(), "records 应为空数组");
    }

    // ===== CT-6: 过滤条件 dictType =====

    @Test
    @DisplayName("CT-6: 过滤条件 dictType (唯一 dictType 匹配, total>=2)")
    void testListDictItemFilterByDictType() throws Exception {
        String dictType = uniqueDictType();
        String code1 = createDictItemAndTrack(dictType, 0);
        String code2 = createDictItemAndTrack(dictType, 1);

        MvcResult result = performGet(API_PATH + "?page=1&size=10&dictType=" + dictType, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        ResultNode node = parseResult(result);
        assertEquals(2, node.data().get("total").asLong(), "唯一 dictType 应匹配 total=2");
        ArrayNode records = (ArrayNode) node.data().get("records");
        assertEquals(2, records.size(), "records 应有 2 条");

        // 验证两个 itemCode 都在结果中
        boolean found1 = false, found2 = false;
        for (JsonNode rec : records) {
            String code = rec.get("itemCode").asText();
            if (code.equals(code1)) found1 = true;
            if (code.equals(code2)) found2 = true;
        }
        assertTrue(found1 && found2, "两个字典项都应在结果中");
    }

    // ===== CT-7: 排序 =====

    @Test
    @DisplayName("CT-7: 排序 (sortNo ASC 非递减)")
    void testListDictItemSortOrder() throws Exception {
        String dictType = uniqueDictType();
        // 创建 2 个字典项, sortNo 分别 10 和 1
        createDictItemAndTrack(dictType, 10);
        createDictItemAndTrack(dictType, 1);

        MvcResult result = performGet(API_PATH + "?page=1&size=50&dictType=" + dictType, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        ArrayNode records = (ArrayNode) parseResult(result).data().get("records");
        assertTrue(records.size() >= 2, "应至少返回 2 条记录");

        // 验证 sortNo ASC (非递减: 前一条 sortNo <= 后一条 sortNo)
        for (int i = 1; i < records.size(); i++) {
            int prev = records.get(i - 1).get("sortNo").asInt();
            int curr = records.get(i).get("sortNo").asInt();
            assertTrue(prev <= curr,
                    "sortNo 应为 ASC 排序 (非递减), 但 " + prev + " > " + curr);
        }
    }

    // ===== CT-8: 数据范围 (admin ALL) =====

    @Test
    @DisplayName("CT-8: 数据范围 (admin ALL 可见本租户数据)")
    void testListDictItemAdminDataScope() throws Exception {
        String dictType = uniqueDictType();
        String itemCode = createDictItemAndTrack(dictType, 0);

        // admin (ALL 范围) 查询, 应能看到刚创建的数据
        MvcResult result = performGet(API_PATH + "?page=1&size=10&dictType=" + dictType, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "admin 应能查询 (有 system:dict-item:list)");
        assertSuccess(result);
        ResultNode node = parseResult(result);
        assertTrue(node.data().get("total").asLong() >= 1, "admin ALL 范围应可见本租户数据");

        ArrayNode records = (ArrayNode) node.data().get("records");
        boolean found = false;
        for (JsonNode rec : records) {
            if (itemCode.equals(rec.get("itemCode").asText())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "admin 应能看到刚创建的字典项 (ALL 范围)");
    }

    // ===== CT-9: 标准响应信封 =====

    @Test
    @DisplayName("CT-9: 标准响应信封 (records/total/page/size 结构齐全)")
    void testListDictItemResponseEnvelope() throws Exception {
        String dictType = uniqueDictType();
        createDictItemAndTrack(dictType, 0);

        MvcResult result = performGet(API_PATH + "?page=1&size=5&dictType=" + dictType, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        assertTraceIdPresent(result);
        ResultNode node = parseResult(result);
        JsonNode data = node.data();
        assertNotNull(data, "data 不应为 null");
        assertNotNull(data.get("records"), "信封应包含 records 字段");
        assertTrue(data.get("records").isArray(), "records 应为数组");
        assertNotNull(data.get("total"), "信封应包含 total 字段");
        assertNotNull(data.get("page"), "信封应包含 page 字段");
        assertNotNull(data.get("size"), "信封应包含 size 字段");
        assertEquals(1, data.get("page").asInt(), "page 应为 1");
        assertEquals(5, data.get("size").asInt(), "size 应为 5");
    }
}
