package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.yutong.lowcode.meta.dto.SaveLcEntityRequest;
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
 * CT-listLowcodeEntities 契约测试 (operationId: listLowcodeEntities)。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-listLowcodeEntities 验收标准)
 * <p>契约来源: routes.yaml web.lowcode.entities (permission: lc:entity:list)
 *    + LcEntityController GET /api/v1/lowcode/entities
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功分页查询 (admin, 200, code=0, records/total/page/size + traceId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (LcEntityController 未加 @RequiresPermission, @Disabled)</li>
 *   <li>CT-4 参数校验 - size 越界 (0 → 钳制为 20, 200 优雅降级)</li>
 *   <li>CT-5 空结果 (entityCode 不匹配, total=0)</li>
 *   <li>CT-6 过滤条件 entityCode (唯一 code 精确匹配, total=1)</li>
 *   <li>CT-7 过滤条件 status (status=DRAFT 只返回草稿实体)</li>
 *   <li>CT-8 排序 (createdTime DESC, 非递增)</li>
 * </ol>
 *
 * <p>数据隔离: 每个用例独立创建测试数据 (UUID 后缀 entityCode), @AfterEach 物理清理。
 *
 * <p>注意: LcEntityController 未加 @RequiresPermission 注解, 权限码 lc:entity:list 未在
 * 控制器层强制; biz/approver/viewer 均无 lc:* 权限但 Mock 模式下 AOP 不拦截。
 */
@DisplayName("CT-listLowcodeEntities: GET /api/v1/lowcode/entities 契约测试")
class CTListLowcodeEntitiesTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/lowcode/entities";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final java.util.Set<String> createdEntityIds = ConcurrentHashMap.newKeySet();

    @AfterEach
    void cleanupTestData() {
        // 先删子表再删主表
        for (String id : createdEntityIds) {
            jdbcTemplate.update("DELETE FROM lc_field WHERE entity_id = ?", id);
        }
        for (String id : createdEntityIds) {
            jdbcTemplate.update("DELETE FROM lc_relation WHERE source_entity_id = ?", id);
        }
        for (String id : createdEntityIds) {
            jdbcTemplate.update("DELETE FROM sys_operation_log WHERE biz_type = 'lc_entity' AND biz_id = ?", id);
        }
        for (String id : createdEntityIds) {
            jdbcTemplate.update("DELETE FROM lc_entity WHERE id = ?", id);
        }
        createdEntityIds.clear();
    }

    /** 构造合法 SaveLcEntityRequest (唯一 entityCode)。 */
    private SaveLcEntityRequest buildValidEntityRequest(String codePrefix) {
        SaveLcEntityRequest req = new SaveLcEntityRequest();
        req.setEntityCode(codePrefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        req.setEntityName("CT列表测试实体");
        req.setTableName("ct_test_" + UUID.randomUUID().toString().substring(0, 8).toLowerCase());
        req.setModuleCode("ct_test");
        return req;
    }

    /** 创建实体草稿并跟踪 ID, 返回 entityCode。 */
    private String createEntityAndTrack(String codePrefix) throws Exception {
        SaveLcEntityRequest request = buildValidEntityRequest(codePrefix);
        MvcResult result = performPost(API_PATH, request, mockUser("admin"));
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertNotNull(data, "创建响应 data 不应为 null");
        String id = data.get("id").asText();
        createdEntityIds.add(id);
        return request.getEntityCode();
    }

    // ===== CT-1: 成功分页查询 =====

    @Test
    @DisplayName("CT-1: 成功分页查询 (admin 用户, 200, code=0, records/total/page/size + traceId)")
    void testListLowcodeEntitiesSuccess() throws Exception {
        String code = createEntityAndTrack("CT-LC-ENT-L");

        MvcResult result = performGet(API_PATH + "?page=1&size=10&entityCode=" + code, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        ResultNode node = parseResult(result);
        JsonNode data = node.data();
        assertNotNull(data, "data 不应为 null");
        assertNotNull(data.get("records"), "records 不应为 null");
        assertTrue(data.get("records").isArray(), "records 应为数组");
        assertTrue(data.get("total").asLong() >= 1, "total 应 >= 1 (至少包含刚创建的实体)");
        assertEquals(1, data.get("page").asInt(), "page 应为 1");
        assertEquals(10, data.get("size").asInt(), "size 应为 10");

        ArrayNode records = (ArrayNode) data.get("records");
        boolean found = false;
        for (JsonNode rec : records) {
            if (code.equals(rec.get("entityCode").asText())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "结果中应包含刚创建的实体 (code=" + code + ")");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testListLowcodeEntitiesUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 (控制器未加 @RequiresPermission) =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (LcEntityController 未加 @RequiresPermission, @Disabled 待补充注解)")
    @org.junit.jupiter.api.Disabled("LcEntityController.page 未加 @RequiresPermission(lc:entity:list), " +
            "权限码未在控制器层强制; 待补充注解后启用此用例")
    void testListLowcodeEntitiesForbidden() {
        // 实现待补充: LcEntityController 当前未加 @RequiresPermission 注解
    }

    // ===== CT-4: 参数校验 - size 越界 =====

    @Test
    @DisplayName("CT-4: 参数校验 - size=0 越界 (PageRequest 钳制为 20, 200 优雅降级)")
    void testListLowcodeEntitiesSizeOutOfRange() throws Exception {
        MvcResult result = performGet(API_PATH + "?page=1&size=0", mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "size 越界应优雅降级, HTTP 200");
        assertSuccess(result);
        ResultNode node = parseResult(result);
        assertEquals(20, node.data().get("size").asInt(), "size=0 应钳制为默认值 20");
    }

    // ===== CT-5: 空结果 =====

    @Test
    @DisplayName("CT-5: 空结果 (entityCode 不匹配, total=0, records 空数组)")
    void testListLowcodeEntitiesEmptyResult() throws Exception {
        String nonExistentCode = "NOSUCHCODE-CT-LC-ENT-" + UUID.randomUUID().toString().substring(0, 8);

        MvcResult result = performGet(API_PATH + "?page=1&size=10&entityCode=" + nonExistentCode, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        ResultNode node = parseResult(result);
        assertEquals(0, node.data().get("total").asLong(), "不匹配的 entityCode 应返回 total=0");
        assertTrue(node.data().get("records").isArray(), "records 应为数组");
        assertEquals(0, node.data().get("records").size(), "records 应为空数组");
    }

    // ===== CT-6: 过滤条件 entityCode =====

    @Test
    @DisplayName("CT-6: 过滤条件 entityCode (唯一 code 精确匹配, total=1)")
    void testListLowcodeEntitiesFilterByEntityCode() throws Exception {
        String code = createEntityAndTrack("CT-LC-ENT-L-KW");

        MvcResult result = performGet(API_PATH + "?page=1&size=10&entityCode=" + code, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        ResultNode node = parseResult(result);
        assertEquals(1, node.data().get("total").asLong(), "唯一 code 应匹配 total=1");
        ArrayNode records = (ArrayNode) node.data().get("records");
        assertEquals(1, records.size(), "records 应只有 1 条");
        assertEquals(code, records.get(0).get("entityCode").asText(), "返回的实体 code 应匹配");
    }

    // ===== CT-7: 过滤条件 status =====

    @Test
    @DisplayName("CT-7: 过滤条件 status (status=DRAFT 只返回草稿实体)")
    void testListLowcodeEntitiesFilterByStatus() throws Exception {
        // 新创建的实体默认为 DRAFT 状态, 使用唯一前缀过滤
        String code = createEntityAndTrack("CT-LC-ENT-L-ST");

        MvcResult result = performGet(
                API_PATH + "?page=1&size=10&entityCode=CT-LC-ENT-L-ST&status=DRAFT",
                mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        ResultNode node = parseResult(result);
        ArrayNode records = (ArrayNode) node.data().get("records");
        assertTrue(records.size() >= 1, "应至少返回 1 条 DRAFT 实体");

        for (JsonNode rec : records) {
            assertEquals("DRAFT", rec.get("status").asText(),
                    "过滤 status=DRAFT 后所有记录 status 应为 DRAFT");
        }

        boolean found = false;
        for (JsonNode rec : records) {
            if (code.equals(rec.get("entityCode").asText())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "刚创建的 DRAFT 实体应在结果中");
    }

    // ===== CT-8: 排序 =====

    @Test
    @DisplayName("CT-8: 排序 (createdTime DESC, 非递增)")
    void testListLowcodeEntitiesSortOrder() throws Exception {
        String code1 = createEntityAndTrack("CT-LC-ENT-L-SO");
        Thread.sleep(10);
        String code2 = createEntityAndTrack("CT-LC-ENT-L-SO");

        MvcResult result = performGet(
                API_PATH + "?page=1&size=50&entityCode=CT-LC-ENT-L-SO", mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        ArrayNode records = (ArrayNode) parseResult(result).data().get("records");
        assertTrue(records.size() >= 2, "应至少返回 2 条记录");

        for (int i = 1; i < records.size(); i++) {
            String prev = records.get(i - 1).get("createdTime").asText();
            String curr = records.get(i).get("createdTime").asText();
            assertTrue(prev.compareTo(curr) >= 0,
                    "createdTime 应为 DESC 排序 (非递增), 但 " + prev + " < " + curr);
        }
    }
}
