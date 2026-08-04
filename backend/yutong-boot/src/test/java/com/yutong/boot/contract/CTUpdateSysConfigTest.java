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
 * GA2-L187 CT-updateConfig 契约测试。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-updateConfig 验收标准)
 * <p>策略来源: operation-policies.yaml updateConfig → versionedUpdate profile (optimistic-version)
 * <p>契约来源: openapi.yaml PUT /api/v1/configs/{id} (x-permission: system:config:edit,
 *    x-error-codes: [SYS-404001, SYS-409001])
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功更新 (200, code=0, 字段更新 + traceId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (viewer 无 system:config:edit, 403 AUTH-403001)</li>
 *   <li>CT-4 参数校验失败 - status 非法 (400)</li>
 *   <li>CT-5 参数配置不存在 (404 SYS-404001)</li>
 *   <li>CT-6 版本冲突 (乐观锁 SYS-409001)</li>
 *   <li>CT-7 configKey 不可变 (更新时改 configKey, 响应仍为原值)</li>
 *   <li>CT-8 审计落库 (sys_operation_log UPDATE 记录)</li>
 *   <li>CT-9 traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)</li>
 * </ol>
 *
 * <p>数据隔离: 每个用例独立创建测试数据 (UUID 后缀 configKey), @AfterEach 清理。
 */
@DisplayName("CT-updateConfig: PUT /api/v1/configs/{id} 契约测试")
class CTUpdateSysConfigTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/configs";
    private static final String ERROR_CODE_FORBIDDEN = "AUTH-403001";
    private static final String ERROR_CODE_NOT_FOUND = "SYS-404001";
    private static final String ERROR_CODE_VERSION_CONFLICT = "SYS-409001";

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

    /** 构造合法 SysConfig 请求体 (唯一 configKey)。 */
    private SysConfig buildValidSysConfig() {
        SysConfig c = new SysConfig();
        c.setConfigKey("ct.l187.u." + UUID.randomUUID().toString().substring(0, 8));
        c.setConfigValue("CT更新前配置值");
        c.setValueType("STRING");
        c.setConfigGroup("system");
        c.setEditable(true);
        c.setSensitive(false);
        c.setStatus("ENABLED");
        return c;
    }

    /** 创建参数配置并跟踪 ID, 返回创建请求 (含原始 configKey)。 */
    private SysConfig createSysConfigAndTrack() throws Exception {
        SysConfig request = buildValidSysConfig();
        MvcResult result = performPost(API_PATH, request, mockUser("admin"));
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertNotNull(data, "创建响应 data 不应为 null");
        String id = data.get("id").asText();
        createdIds.add(id);
        request.setId(id);
        return request;
    }

    /** 构造更新请求体 (保持原 configKey, 修改 value, 指定 version)。 */
    private SysConfig buildUpdateRequest(String originalConfigKey, int version) {
        SysConfig c = new SysConfig();
        c.setConfigKey(originalConfigKey);
        c.setConfigValue("CT更新后配置值-" + UUID.randomUUID().toString().substring(0, 4));
        c.setValueType("STRING");
        c.setConfigGroup("system");
        c.setEditable(true);
        c.setSensitive(false);
        c.setStatus("ENABLED");
        c.setVersion(version);
        return c;
    }

    // ===== CT-1: 成功更新 =====

    @Test
    @DisplayName("CT-1: 成功更新参数配置 (admin 用户, 200, code=0, 字段更新 + traceId)")
    void testUpdateSysConfigSuccess() throws Exception {
        SysConfig created = createSysConfigAndTrack();
        String id = created.getId();

        SysConfig updateReq = buildUpdateRequest(created.getConfigKey(), 0);
        MvcResult result = performPut(API_PATH + "/" + id, updateReq, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        ResultNode node = parseResult(result);
        JsonNode data = node.data();
        assertEquals(id, data.get("id").asText(), "返回 id 应与请求 id 一致");
        assertEquals(updateReq.getConfigValue(), data.get("configValue").asText(), "configValue 应已更新");
        assertEquals("ENABLED", data.get("status").asText());
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testUpdateSysConfigUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (viewer 无 system:config:edit, 403 AUTH-403001)")
    void testUpdateSysConfigForbidden() throws Exception {
        SysConfig created = createSysConfigAndTrack();
        SysConfig update = buildUpdateRequest(created.getConfigKey(), 0);

        MvcResult result = performPut(API_PATH + "/" + created.getId(), update, mockUser("viewer"));

        assertEquals(403, result.getResponse().getStatus(), "viewer 无写权限, HTTP 应为 403");
        assertError(result, ERROR_CODE_FORBIDDEN);
        assertTraceIdPresent(result);
    }

    // ===== CT-4: 参数校验失败 =====

    @Test
    @DisplayName("CT-4: 参数校验失败 - status 非法值 (400)")
    void testUpdateSysConfigValidationInvalidStatus() throws Exception {
        SysConfig created = createSysConfigAndTrack();
        SysConfig update = buildUpdateRequest(created.getConfigKey(), 0);
        update.setStatus("INVALID"); // @Pattern(ENABLED|DISABLED) 校验

        MvcResult result = performPut(API_PATH + "/" + created.getId(), update, mockUser("admin"));

        assertEquals(400, result.getResponse().getStatus(), "status 非法, HTTP 应为 400");
        ResultNode node = parseResult(result);
        assertNotEquals("0", node.code(), "校验失败不应返回 code=0");
        assertTraceIdPresent(result);
    }

    // ===== CT-5: 参数配置不存在 =====

    @Test
    @DisplayName("CT-5: 参数配置不存在 (404 SYS-404001)")
    void testUpdateSysConfigNotFound() throws Exception {
        SysConfig update = buildUpdateRequest("any.config.key", 0);
        String nonExistentId = "01NOTEXIST00000000000000CT";

        MvcResult result = performPut(API_PATH + "/" + nonExistentId, update, mockUser("admin"));

        assertEquals(404, result.getResponse().getStatus(), "参数配置不存在, HTTP 应为 404");
        assertError(result, ERROR_CODE_NOT_FOUND);
        assertTraceIdPresent(result);
    }

    // ===== CT-6: 版本冲突 (乐观锁) =====

    @Test
    @DisplayName("CT-6: 版本冲突 (versionedUpdate 乐观锁, 旧 version → SYS-409001)")
    void testUpdateSysConfigVersionConflict() throws Exception {
        SysConfig created = createSysConfigAndTrack();
        String id = created.getId();
        String originalConfigKey = created.getConfigKey();

        // 第一次更新 version=0 → 成功 (DB version 0→1)
        SysConfig firstUpdate = buildUpdateRequest(originalConfigKey, 0);
        MvcResult firstResult = performPut(API_PATH + "/" + id, firstUpdate, mockUser("admin"));
        assertEquals(200, firstResult.getResponse().getStatus(), "第一次更新应成功");
        assertSuccess(firstResult);

        // 第二次更新仍用 version=0 (stale, DB 已是 version=1) → 乐观锁冲突 SYS-409001
        SysConfig staleUpdate = buildUpdateRequest(originalConfigKey, 0);
        staleUpdate.setConfigValue("冲突更新-应失败");
        MvcResult conflictResult = performPut(API_PATH + "/" + id, staleUpdate, mockUser("admin"));

        assertEquals(409, conflictResult.getResponse().getStatus(), "版本冲突 HTTP 应为 409");
        assertError(conflictResult, ERROR_CODE_VERSION_CONFLICT);
        assertTraceIdPresent(conflictResult);
    }

    // ===== CT-7: configKey 不可变 =====

    @Test
    @DisplayName("CT-7: configKey 不可变 (更新时改 configKey, 响应仍为原值)")
    void testUpdateSysConfigConfigKeyImmutable() throws Exception {
        SysConfig created = createSysConfigAndTrack();
        String id = created.getId();
        String originalConfigKey = created.getConfigKey();

        // 更新时尝试改 configKey (合法格式但不同值), 服务应强制沿用原值
        SysConfig update = buildUpdateRequest(originalConfigKey, 0);
        update.setConfigKey("ct.l187.hijacked." + UUID.randomUUID().toString().substring(0, 4));
        MvcResult result = performPut(API_PATH + "/" + id, update, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "更新应成功 (configKey 不可变但请求本身合法)");
        assertSuccess(result);

        // 验证响应 configKey 仍为原值 (服务强制 setConfigKey(existing))
        ResultNode node = parseResult(result);
        JsonNode data = node.data();
        assertEquals(originalConfigKey, data.get("configKey").asText(),
                "configKey 不可变, 响应应保留原值: " + originalConfigKey);
    }

    // ===== CT-8: 审计落库 =====

    @Test
    @DisplayName("CT-8: 审计落库 (@Auditable UPDATE → sys_operation_log 有记录)")
    void testUpdateSysConfigAuditLog() throws Exception {
        SysConfig created = createSysConfigAndTrack();
        String id = created.getId();

        SysConfig update = buildUpdateRequest(created.getConfigKey(), 0);
        MvcResult result = performPut(API_PATH + "/" + id, update, mockUser("admin"));
        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);

        String traceId = parseResult(result).traceId();
        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_operation_log " +
                        "WHERE biz_type = 'sys_config' AND operation_type = 'UPDATE' " +
                        "AND trace_id = ?",
                Integer.class, traceId);

        assertNotNull(auditCount, "审计日志查询不应返回 null");
        assertTrue(auditCount >= 1,
                "sys_operation_log 应至少有 1 条 UPDATE 审计记录 (traceId=" + traceId + "), 实际: " + auditCount);

        jdbcTemplate.query("SELECT biz_id, module, operation_type, biz_type FROM sys_operation_log " +
                        "WHERE biz_type = 'sys_config' AND operation_type = 'UPDATE' AND trace_id = ?",
                rs -> {
                    assertEquals(id, rs.getString("biz_id"), "审计记录 biz_id 应为更新的参数配置 ID");
                    assertEquals("system", rs.getString("module"), "审计记录 module 应为 system");
                    assertEquals("UPDATE", rs.getString("operation_type"), "审计记录 operation_type 应为 UPDATE");
                    assertEquals("sys_config", rs.getString("biz_type"), "审计记录 biz_type 应为 sys_config");
                }, traceId);
    }

    // ===== CT-9: traceId 透传 =====

    @Test
    @DisplayName("CT-9: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)")
    void testUpdateSysConfigTraceIdPropagation() throws Exception {
        SysConfig created = createSysConfigAndTrack();
        SysConfig update = buildUpdateRequest(created.getConfigKey(), 0);

        MvcResult result = performPut(API_PATH + "/" + created.getId(), update, mockUser("admin"));

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
