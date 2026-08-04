package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.yutong.system.config.domain.SysConfig;
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
 * GA2-L187 CT-listConfigs 契约测试 (operationId: listConfigs)。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-listConfigs 验收标准)
 * <p>策略来源: operation-policies.yaml listConfigs → read profile (queryPage)
 * <p>契约来源: openapi.yaml GET /api/v1/configs (x-permission: system:config:list)
 *
 * <p>覆盖矩阵 (查询接口):
 * <ol>
 *   <li>CT-1 成功分页查询 (200, code=0, records/total/page/size + traceId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (viewer 无 system:config:list, 403 AUTH-403001)</li>
 *   <li>CT-4 参数校验 - size 越界 (0 → 钳制为 20, 200 优雅降级)</li>
 *   <li>CT-5 空结果 (keyword 不匹配, total=0)</li>
 *   <li>CT-6 过滤条件 keyword (唯一 configKey 匹配, total=1)</li>
 *   <li>CT-7 过滤条件 configGroup (精确匹配, total>=1)</li>
 *   <li>CT-8 敏感配置脱敏 (sensitive=true → configValue=******)</li>
 *   <li>CT-9 标准响应信封 (records/total/page/size 结构)</li>
 * </ol>
 *
 * <p>数据隔离: 每个用例独立创建测试数据 (UUID 后缀 configKey), @AfterEach 物理清理。
 *
 * <p>注意: SysConfigController 分页参数名为 page/size; keyword 模糊匹配 configKey; configGroup 精确匹配。
 */
@DisplayName("CT-listConfigs: GET /api/v1/configs 契约测试")
class CTListSysConfigTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/configs";
    private static final String ERROR_CODE_FORBIDDEN = "AUTH-403001";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final java.util.Set<String> createdIds = ConcurrentHashMap.newKeySet();

    @AfterEach
    void cleanupTestData() {
        for (String id : createdIds) {
            jdbcTemplate.update("DELETE FROM sys_operation_log WHERE biz_type = 'sys_config' AND biz_id = ?", id);
        }
        for (String id : createdIds) {
            jdbcTemplate.update("DELETE FROM sys_config WHERE id = ?", id);
        }
        createdIds.clear();
    }

    /** 构造合法 SysConfig (唯一 configKey, 指定 configGroup 和 sensitive)。 */
    private SysConfig buildValidSysConfig(String configGroup, boolean sensitive) {
        SysConfig c = new SysConfig();
        c.setConfigKey("ct.l187.l." + UUID.randomUUID().toString().substring(0, 8));
        c.setConfigValue("CT列表测试配置值-" + UUID.randomUUID().toString().substring(0, 4));
        c.setValueType("STRING");
        c.setConfigGroup(configGroup);
        c.setEditable(true);
        c.setSensitive(sensitive);
        c.setStatus("ENABLED");
        return c;
    }

    /** 创建参数配置并跟踪 ID, 返回 configKey (用于 keyword 过滤)。 */
    private String createSysConfigAndTrack(String configGroup, boolean sensitive) throws Exception {
        SysConfig request = buildValidSysConfig(configGroup, sensitive);
        MvcResult result = performPost(API_PATH, request, mockUser("admin"));
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertNotNull(data, "创建响应 data 不应为 null");
        String id = data.get("id").asText();
        createdIds.add(id);
        return request.getConfigKey();
    }

    // ===== CT-1: 成功分页查询 =====

    @Test
    @DisplayName("CT-1: 成功分页查询 (admin 用户, 200, code=0, records/total/page/size + traceId)")
    void testListSysConfigSuccess() throws Exception {
        String configKey = createSysConfigAndTrack("system", false);

        MvcResult result = performGet(API_PATH + "?page=1&size=10&keyword=" + configKey, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        ResultNode node = parseResult(result);
        JsonNode data = node.data();
        assertNotNull(data, "data 不应为 null");
        assertNotNull(data.get("records"), "records 不应为 null");
        assertTrue(data.get("records").isArray(), "records 应为数组");
        assertTrue(data.get("total").asLong() >= 1, "total 应 >= 1 (至少包含刚创建的参数配置)");
        assertEquals(1, data.get("page").asInt(), "page 应为 1");
        assertEquals(10, data.get("size").asInt(), "size 应为 10");

        // 验证刚创建的参数配置在结果中
        ArrayNode records = (ArrayNode) data.get("records");
        boolean found = false;
        for (JsonNode rec : records) {
            if (configKey.equals(rec.get("configKey").asText())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "结果中应包含刚创建的参数配置 (configKey=" + configKey + ")");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testListSysConfigUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (viewer 无 system:config:list, 403 AUTH-403001)")
    void testListSysConfigForbidden() throws Exception {
        MvcResult result = performGet(API_PATH + "?page=1&size=10", mockUser("viewer"));

        assertEquals(403, result.getResponse().getStatus(), "viewer 无列表权限, HTTP 应为 403");
        assertError(result, ERROR_CODE_FORBIDDEN);
        assertTraceIdPresent(result);
    }

    // ===== CT-4: 参数校验 - size 越界 =====

    @Test
    @DisplayName("CT-4: 参数校验 - size=0 越界 (PageRequest 钳制为 20, 200 优雅降级)")
    void testListSysConfigSizeOutOfRange() throws Exception {
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
    void testListSysConfigEmptyResult() throws Exception {
        String nonExistentKeyword = "nosuchkey-ct-l187-" + UUID.randomUUID().toString().substring(0, 8);

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
    @DisplayName("CT-6: 过滤条件 keyword (唯一 configKey 匹配, total=1)")
    void testListSysConfigFilterByKeyword() throws Exception {
        String configKey = createSysConfigAndTrack("system", false);

        MvcResult result = performGet(API_PATH + "?page=1&size=10&keyword=" + configKey, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        ResultNode node = parseResult(result);
        assertEquals(1, node.data().get("total").asLong(), "唯一 configKey 应匹配 total=1");
        ArrayNode records = (ArrayNode) node.data().get("records");
        assertEquals(1, records.size(), "records 应只有 1 条");
        assertEquals(configKey, records.get(0).get("configKey").asText(), "返回的 configKey 应匹配");
    }

    // ===== CT-7: 过滤条件 configGroup =====

    @Test
    @DisplayName("CT-7: 过滤条件 configGroup (精确匹配, total>=1)")
    void testListSysConfigFilterByConfigGroup() throws Exception {
        String uniqueGroup = "ct-group-" + UUID.randomUUID().toString().substring(0, 6);
        String configKey = createSysConfigAndTrack(uniqueGroup, false);

        MvcResult result = performGet(
                API_PATH + "?page=1&size=10&keyword=" + configKey + "&configGroup=" + uniqueGroup,
                mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        ResultNode node = parseResult(result);
        assertTrue(node.data().get("total").asLong() >= 1, "configGroup 精确匹配应返回 total>=1");

        ArrayNode records = (ArrayNode) node.data().get("records");
        for (JsonNode rec : records) {
            assertEquals(uniqueGroup, rec.get("configGroup").asText(),
                    "所有返回记录的 configGroup 应为过滤值: " + uniqueGroup);
        }
    }

    // ===== CT-8: 敏感配置脱敏 =====

    @Test
    @DisplayName("CT-8: 敏感配置脱敏 (sensitive=true → configValue=******)")
    void testListSysConfigSensitiveMasking() throws Exception {
        String configKey = createSysConfigAndTrack("system", true);

        MvcResult result = performGet(API_PATH + "?page=1&size=10&keyword=" + configKey, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        ResultNode node = parseResult(result);
        ArrayNode records = (ArrayNode) node.data().get("records");
        assertEquals(1, records.size(), "应返回 1 条记录");

        JsonNode rec = records.get(0);
        assertEquals(configKey, rec.get("configKey").asText(), "configKey 应匹配");
        assertEquals("******", rec.get("configValue").asText(),
                "敏感配置 configValue 应脱敏为 ******");
        assertTrue(rec.get("sensitive").asBoolean(), "sensitive 应为 true");
    }

    // ===== CT-9: 标准响应信封 =====

    @Test
    @DisplayName("CT-9: 标准响应信封 (records/total/page/size 结构齐全)")
    void testListSysConfigResponseEnvelope() throws Exception {
        createSysConfigAndTrack("system", false);

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
