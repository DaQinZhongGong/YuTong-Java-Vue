package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
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
 * GA2-L187 CT-getConfig 契约测试 (operationId: getConfig)。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-getConfig 验收标准)
 * <p>策略来源: operation-policies.yaml getConfig → read profile (queryOne)
 * <p>契约来源: openapi.yaml GET /api/v1/configs/{id} (x-permission: system:config:list)
 *
 * <p>覆盖矩阵 (详情查询接口):
 * <ol>
 *   <li>CT-1 成功查询 (200, code=0, 返回 SysConfig + traceId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (viewer 无 system:config:list, 403 AUTH-403001)</li>
 *   <li>CT-4 参数配置不存在 (404 SYS-404001)</li>
 *   <li>CT-5 已删除配置查询 (404 SYS-404001, 逻辑删除过滤)</li>
 *   <li>CT-6 敏感配置脱敏 (sensitive=true → configValue=******)</li>
 *   <li>CT-7 字段完整性 (id/configKey/configValue/valueType/configGroup/status/version 全返回)</li>
 *   <li>CT-8 traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)</li>
 * </ol>
 *
 * <p>数据隔离: 每个用例独立创建测试数据 (UUID 后缀 configKey), @AfterEach 物理清理。
 */
@DisplayName("CT-getConfig: GET /api/v1/configs/{id} 契约测试")
class CTGetSysConfigTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/configs";
    private static final String ERROR_CODE_FORBIDDEN = "AUTH-403001";
    private static final String ERROR_CODE_NOT_FOUND = "SYS-404001";

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

    /** 构造合法 SysConfig 请求体 (唯一 configKey, 指定 sensitive)。 */
    private SysConfig buildValidSysConfig(boolean sensitive) {
        SysConfig c = new SysConfig();
        c.setConfigKey("ct.l187.g." + UUID.randomUUID().toString().substring(0, 8));
        c.setConfigValue("CT详情测试配置值");
        c.setValueType("STRING");
        c.setConfigGroup("system");
        c.setEditable(true);
        c.setSensitive(sensitive);
        c.setStatus("ENABLED");
        return c;
    }

    /** 创建参数配置并跟踪 ID, 返回创建请求。 */
    private SysConfig createSysConfigAndTrack(boolean sensitive) throws Exception {
        SysConfig request = buildValidSysConfig(sensitive);
        MvcResult result = performPost(API_PATH, request, mockUser("admin"));
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertNotNull(data, "创建响应 data 不应为 null");
        String id = data.get("id").asText();
        createdIds.add(id);
        request.setId(id);
        return request;
    }

    // ===== CT-1: 成功查询 =====

    @Test
    @DisplayName("CT-1: 成功查询参数配置详情 (admin 用户, 200, code=0, 返回 SysConfig + traceId)")
    void testGetSysConfigSuccess() throws Exception {
        SysConfig created = createSysConfigAndTrack(false);
        String id = created.getId();

        MvcResult result = performGet(API_PATH + "/" + id, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        ResultNode node = parseResult(result);
        JsonNode data = node.data();
        assertEquals(id, data.get("id").asText(), "返回 id 应与请求 id 一致");
        assertEquals(created.getConfigKey(), data.get("configKey").asText());
        assertEquals(created.getConfigValue(), data.get("configValue").asText());
        assertEquals("STRING", data.get("valueType").asText());
        assertEquals("system", data.get("configGroup").asText());
        assertEquals("ENABLED", data.get("status").asText());
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testGetSysConfigUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (viewer 无 system:config:list, 403 AUTH-403001)")
    void testGetSysConfigForbidden() throws Exception {
        SysConfig created = createSysConfigAndTrack(false);

        MvcResult result = performGet(API_PATH + "/" + created.getId(), mockUser("viewer"));

        assertEquals(403, result.getResponse().getStatus(), "viewer 无查询权限, HTTP 应为 403");
        assertError(result, ERROR_CODE_FORBIDDEN);
        assertTraceIdPresent(result);
    }

    // ===== CT-4: 参数配置不存在 =====

    @Test
    @DisplayName("CT-4: 参数配置不存在 (404 SYS-404001)")
    void testGetSysConfigNotFound() throws Exception {
        String nonExistentId = "01NOTEXIST00000000000000CT";

        MvcResult result = performGet(API_PATH + "/" + nonExistentId, mockUser("admin"));

        assertEquals(404, result.getResponse().getStatus(), "参数配置不存在, HTTP 应为 404");
        assertError(result, ERROR_CODE_NOT_FOUND);
        assertTraceIdPresent(result);
    }

    // ===== CT-5: 已删除配置查询 =====

    @Test
    @DisplayName("CT-5: 已删除配置查询 (selectById 逻辑删除过滤, 404 SYS-404001)")
    void testGetSysConfigAlreadyDeleted() throws Exception {
        SysConfig created = createSysConfigAndTrack(false);
        String id = created.getId();

        // 先删除 (逻辑删除)
        MvcResult deleteResult = performDelete(API_PATH + "/" + id, mockUser("admin"));
        assertEquals(200, deleteResult.getResponse().getStatus(), "删除应成功");

        // 再查询: selectById 过滤逻辑删除 → null → 404
        MvcResult result = performGet(API_PATH + "/" + id, mockUser("admin"));

        assertEquals(404, result.getResponse().getStatus(), "已删除配置查询应返回 404");
        assertError(result, ERROR_CODE_NOT_FOUND);
        assertTraceIdPresent(result);
    }

    // ===== CT-6: 敏感配置脱敏 =====

    @Test
    @DisplayName("CT-6: 敏感配置脱敏 (sensitive=true → configValue=******)")
    void testGetSysConfigSensitiveMasking() throws Exception {
        SysConfig created = createSysConfigAndTrack(true);
        String id = created.getId();

        MvcResult result = performGet(API_PATH + "/" + id, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        ResultNode node = parseResult(result);
        JsonNode data = node.data();
        assertEquals(id, data.get("id").asText());
        assertEquals(created.getConfigKey(), data.get("configKey").asText());
        assertEquals("******", data.get("configValue").asText(),
                "敏感配置详情 configValue 应脱敏为 ******");
        assertTrue(data.get("sensitive").asBoolean(), "sensitive 应为 true");
    }

    // ===== CT-7: 字段完整性 =====

    @Test
    @DisplayName("CT-7: 字段完整性 (id/configKey/configValue/valueType/configGroup/status/version/createdTime 全返回)")
    void testGetSysConfigFieldCompleteness() throws Exception {
        SysConfig created = createSysConfigAndTrack(false);
        String id = created.getId();

        MvcResult result = performGet(API_PATH + "/" + id, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertNotNull(data.get("id"), "应包含 id 字段");
        assertNotNull(data.get("configKey"), "应包含 configKey 字段");
        assertNotNull(data.get("configValue"), "应包含 configValue 字段");
        assertNotNull(data.get("valueType"), "应包含 valueType 字段");
        assertNotNull(data.get("configGroup"), "应包含 configGroup 字段");
        assertNotNull(data.get("status"), "应包含 status 字段");
        assertNotNull(data.get("version"), "应包含 version 字段");
        assertNotNull(data.get("createdTime"), "应包含 createdTime 字段");
        assertNotNull(data.get("updatedTime"), "应包含 updatedTime 字段");
        assertNotNull(data.get("deleted"), "应包含 deleted 字段");
    }

    // ===== CT-8: traceId 透传 =====

    @Test
    @DisplayName("CT-8: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)")
    void testGetSysConfigTraceIdPropagation() throws Exception {
        SysConfig created = createSysConfigAndTrack(false);

        MvcResult result = performGet(API_PATH + "/" + created.getId(), mockUser("admin"));

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
