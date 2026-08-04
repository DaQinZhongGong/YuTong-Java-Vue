package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.yutong.system.dict.domain.DictType;
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
 * GA2-L185 CT-listDictType 契约测试 (operationId: listDictTypes)。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-listDictTypes 验收标准)
 * <p>策略来源: operation-policies.yaml listDictTypes → read profile (queryPage)
 * <p>契约来源: openapi.yaml GET /api/v1/dict-types (x-permission: system:dict:list)
 *
 * <p>覆盖矩阵 (查询接口):
 * <ol>
 *   <li>CT-1 成功分页查询 (200, code=0, records/total/page/size + traceId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (viewer 无 system:dict:list, 403 AUTH-403001)</li>
 *   <li>CT-4 参数校验 - size 越界 (0 → 钳制为 20, 200 优雅降级)</li>
 *   <li>CT-5 空结果 (keyword 不匹配, total=0)</li>
 *   <li>CT-6 过滤条件 keyword (唯一 dictName 匹配, total=1)</li>
 *   <li>CT-7 排序 (sortNo ASC 非递减)</li>
 *   <li>CT-8 数据范围 (admin ALL 可见本租户数据)</li>
 *   <li>CT-9 标准响应信封 (records/total/page/size 结构)</li>
 * </ol>
 *
 * <p>数据隔离: 每个用例独立创建测试数据 (UUID 后缀 dictType), @AfterEach 物理清理。
 *
 * <p>注意: DictTypeController 分页参数名为 page/size (非 pageNo/pageSize); keyword 模糊匹配 dictName。
 */
@DisplayName("CT-listDictType: GET /api/v1/dict-types 契约测试")
class CTListDictTypeTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/dict-types";
    private static final String ERROR_CODE_FORBIDDEN = "AUTH-403001";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final java.util.Set<String> createdIds = ConcurrentHashMap.newKeySet();

    @AfterEach
    void cleanupTestData() {
        for (String id : createdIds) {
            jdbcTemplate.update("DELETE FROM sys_operation_log WHERE biz_type = 'sys_dict_type' AND biz_id = ?", id);
        }
        for (String id : createdIds) {
            jdbcTemplate.update("DELETE FROM sys_dict_type WHERE id = ?", id);
        }
        createdIds.clear();
    }

    /** 构造合法 DictType (唯一 dictType, 指定 dictName 前缀)。 */
    private DictType buildValidDictType(String namePrefix, int sortNo) {
        DictType t = new DictType();
        t.setDictType("CT-L185-L-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        t.setDictName(namePrefix + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        t.setStatus("ENABLED");
        t.setSortNo(sortNo);
        return t;
    }

    /** 创建字典类型并跟踪 ID, 返回 dictName (用于 keyword 过滤)。 */
    private String createDictTypeAndTrack(String namePrefix, int sortNo) throws Exception {
        DictType request = buildValidDictType(namePrefix, sortNo);
        MvcResult result = performPost(API_PATH, request, mockUser("admin"));
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertNotNull(data, "创建响应 data 不应为 null");
        String id = data.get("id").asText();
        createdIds.add(id);
        return request.getDictName();
    }

    // ===== CT-1: 成功分页查询 =====

    @Test
    @DisplayName("CT-1: 成功分页查询 (admin 用户, 200, code=0, records/total/page/size + traceId)")
    void testListDictTypeSuccess() throws Exception {
        String name = createDictTypeAndTrack("CT-L185-L", 0);

        MvcResult result = performGet(API_PATH + "?page=1&size=10&keyword=" + name, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        ResultNode node = parseResult(result);
        JsonNode data = node.data();
        assertNotNull(data, "data 不应为 null");
        assertNotNull(data.get("records"), "records 不应为 null");
        assertTrue(data.get("records").isArray(), "records 应为数组");
        assertTrue(data.get("total").asLong() >= 1, "total 应 >= 1 (至少包含刚创建的字典类型)");
        assertEquals(1, data.get("page").asInt(), "page 应为 1");
        assertEquals(10, data.get("size").asInt(), "size 应为 10");

        // 验证刚创建的字典类型在结果中
        ArrayNode records = (ArrayNode) data.get("records");
        boolean found = false;
        for (JsonNode rec : records) {
            if (name.equals(rec.get("dictName").asText())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "结果中应包含刚创建的字典类型 (name=" + name + ")");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testListDictTypeUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (viewer 无 system:dict:list, 403 AUTH-403001)")
    void testListDictTypeForbidden() throws Exception {
        MvcResult result = performGet(API_PATH + "?page=1&size=10", mockUser("viewer"));

        assertEquals(403, result.getResponse().getStatus(), "viewer 无列表权限, HTTP 应为 403");
        assertError(result, ERROR_CODE_FORBIDDEN);
        assertTraceIdPresent(result);
    }

    // ===== CT-4: 参数校验 - size 越界 =====

    @Test
    @DisplayName("CT-4: 参数校验 - size=0 越界 (PageRequest 钳制为 20, 200 优雅降级)")
    void testListDictTypeSizeOutOfRange() throws Exception {
        MvcResult result = performGet(API_PATH + "?page=1&size=0", mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "size 越界应优雅降级, HTTP 200");
        assertSuccess(result);
        ResultNode node = parseResult(result);
        // PageRequest: size < 1 → 钳制为 20
        assertEquals(20, node.data().get("size").asInt(), "size=0 应钳制为默认值 20");
    }

    // ===== CT-5: 空结果 =====

    @Test
    @DisplayName("CT-5: 空结果 (keyword 不匹配, total=0, records 空数组)")
    void testListDictTypeEmptyResult() throws Exception {
        String nonExistentKeyword = "NOSUCHNAME-CT-L185-" + UUID.randomUUID().toString().substring(0, 8);

        MvcResult result = performGet(API_PATH + "?page=1&size=10&keyword=" + nonExistentKeyword, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        ResultNode node = parseResult(result);
        assertEquals(0, node.data().get("total").asLong(), "不匹配的 keyword 应返回 total=0");
        assertTrue(node.data().get("records").isArray(), "records 应为数组");
        assertEquals(0, node.data().get("records").size(), "records 应为空数组");
    }

    // ===== CT-6: 过滤条件 keyword =====

    @Test
    @DisplayName("CT-6: 过滤条件 keyword (唯一 dictName 匹配, total=1)")
    void testListDictTypeFilterByKeyword() throws Exception {
        String name = createDictTypeAndTrack("CT-L185-L-KW", 0);

        MvcResult result = performGet(API_PATH + "?page=1&size=10&keyword=" + name, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        ResultNode node = parseResult(result);
        assertEquals(1, node.data().get("total").asLong(), "唯一 dictName 应匹配 total=1");
        ArrayNode records = (ArrayNode) node.data().get("records");
        assertEquals(1, records.size(), "records 应只有 1 条");
        assertEquals(name, records.get(0).get("dictName").asText(), "返回的 dictName 应匹配");
    }

    // ===== CT-7: 排序 =====

    @Test
    @DisplayName("CT-7: 排序 (sortNo ASC 非递减)")
    void testListDictTypeSortOrder() throws Exception {
        // 创建 2 个字典类型使用共同 name 前缀, sortNo 分别 10 和 1
        String name1 = createDictTypeAndTrack("CT-L185-L-SO", 10);
        String name2 = createDictTypeAndTrack("CT-L185-L-SO", 1);

        MvcResult result = performGet(
                API_PATH + "?page=1&size=50&keyword=CT-L185-L-SO", mockUser("admin"));

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
    void testListDictTypeAdminDataScope() throws Exception {
        String name = createDictTypeAndTrack("CT-L185-L-DS", 0);

        // admin (ALL 范围) 查询, 应能看到刚创建的数据
        MvcResult result = performGet(API_PATH + "?page=1&size=10&keyword=" + name, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "admin 应能查询 (有 system:dict:list)");
        assertSuccess(result);
        ResultNode node = parseResult(result);
        assertTrue(node.data().get("total").asLong() >= 1, "admin ALL 范围应可见本租户数据");

        ArrayNode records = (ArrayNode) node.data().get("records");
        boolean found = false;
        for (JsonNode rec : records) {
            if (name.equals(rec.get("dictName").asText())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "admin 应能看到刚创建的字典类型 (ALL 范围)");
    }

    // ===== CT-9: 标准响应信封 =====

    @Test
    @DisplayName("CT-9: 标准响应信封 (records/total/page/size 结构齐全)")
    void testListDictTypeResponseEnvelope() throws Exception {
        createDictTypeAndTrack("CT-L185-L-ENV", 0);

        MvcResult result = performGet(API_PATH + "?page=1&size=5", mockUser("admin"));

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
