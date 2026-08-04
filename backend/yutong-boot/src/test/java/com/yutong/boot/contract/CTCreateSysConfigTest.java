package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.yutong.system.config.domain.SysConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GA2-L187 CT-createConfig 契约测试。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-createConfig 验收标准)
 * <p>策略来源: operation-policies.yaml createConfig → uniqueCreate profile (database-unique-constraint)
 * <p>契约来源: openapi.yaml POST /api/v1/configs (x-permission: system:config:add, x-error-codes: [SYS-409004])
 *
 * <p>覆盖 58 号文档通用覆盖矩阵 9 类场景:
 * <ol>
 *   <li>CT-1 成功创建 (200, code=0, 返回 SysConfig + traceId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (viewer 无 system:config:add, 403 AUTH-403001)</li>
 *   <li>CT-4 参数校验失败 (configKey 空/status 非法/valueType 缺失, 400)</li>
 *   <li>CT-5 重复 configKey (409 SYS-409004)</li>
 *   <li>CT-6 标准错误信封 (code/message/traceId 字段齐全)</li>
 *   <li>CT-7 traceId 透传 (响应头 + 响应体)</li>
 *   <li>CT-8 幂等 (重复提交同 configKey, 第二次 409)</li>
 *   <li>CT-9 审计落库 (sys_operation_log 有 CREATE 记录)</li>
 * </ol>
 *
 * <p>数据隔离: 每个用例使用唯一 configKey (UUID 后缀), @AfterEach 清理创建的数据。
 * 不使用 @Transactional 以便 CT-9 审计验证能查询 sys_operation_log。
 *
 * <p>注意: SysConfig 为系统模块, 仅 admin (通配权限 *) 有 system:config:add; viewer 均无该权限。
 */
@DisplayName("CT-createConfig: POST /api/v1/configs 契约测试")
class CTCreateSysConfigTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/configs";
    private static final String ERROR_CODE_FORBIDDEN = "AUTH-403001";
    private static final String ERROR_CODE_DUPLICATE = "SYS-409004";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 本测试创建的参数配置 ID 集合, @AfterEach 清理。 */
    private final java.util.Set<String> createdIds = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @AfterEach
    void cleanupTestData() {
        // 清理本测试产生的审计日志 (按 biz_id 精确清理, sys_operation_log 实际列为 before_json/after_json)
        for (String id : createdIds) {
            jdbcTemplate.update("DELETE FROM sys_operation_log WHERE biz_type = 'sys_config' AND biz_id = ?", id);
        }
        // 清理本测试创建的参数配置数据 (物理删除, 避免逻辑删除残留影响唯一索引)
        for (String id : createdIds) {
            jdbcTemplate.update("DELETE FROM sys_config WHERE id = ?", id);
        }
        createdIds.clear();
    }

    /** 构造合法 SysConfig 请求体 (唯一 configKey)。 */
    private SysConfig buildValidSysConfig() {
        SysConfig c = new SysConfig();
        c.setConfigKey("ct.l187." + UUID.randomUUID().toString().substring(0, 8));
        c.setConfigValue("CT测试配置值");
        c.setValueType("STRING");
        c.setConfigGroup("system");
        c.setEditable(true);
        c.setSensitive(false);
        c.setStatus("ENABLED");
        return c;
    }

    /** 从成功响应中提取参数配置 ID 并记录到清理集合。 */
    private String extractAndTrackId(MvcResult result) throws Exception {
        ResultNode node = parseResult(result);
        JsonNode data = node.data();
        assertNotNull(data, "成功响应 data 不应为 null");
        String id = data.get("id").asText();
        createdIds.add(id);
        return id;
    }

    // ===== CT-1: 成功创建 =====

    @Test
    @DisplayName("CT-1: 成功创建参数配置 (admin 用户, 200, code=0, 返回 id/version/traceId)")
    void testCreateSysConfigSuccess() throws Exception {
        SysConfig request = buildValidSysConfig();

        MvcResult result = performPost(API_PATH, request, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        // 验证返回的 SysConfig 数据
        ResultNode node = parseResult(result);
        JsonNode data = node.data();
        assertNotNull(data.get("id"), "返回的 id 不应为 null");
        assertEquals(request.getConfigKey(), data.get("configKey").asText());
        assertEquals(request.getConfigValue(), data.get("configValue").asText());
        assertEquals("STRING", data.get("valueType").asText());
        assertEquals("ENABLED", data.get("status").asText());
        assertNotNull(data.get("version"), "version 不应为 null");
        assertNotNull(data.get("updatedTime"), "updatedTime 不应为 null");

        extractAndTrackId(result);
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testCreateSysConfigUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (viewer 无 system:config:add, 403 AUTH-403001)")
    void testCreateSysConfigForbidden() throws Exception {
        SysConfig request = buildValidSysConfig();

        MvcResult result = performPost(API_PATH, request, mockUser("viewer"));

        assertEquals(403, result.getResponse().getStatus(), "viewer 无写权限, HTTP 应为 403");
        assertError(result, ERROR_CODE_FORBIDDEN);
        assertTraceIdPresent(result);
    }

    // ===== CT-4: 参数校验失败 =====

    @Test
    @DisplayName("CT-4a: 参数校验失败 - configKey 为空 (400)")
    void testCreateSysConfigValidationBlankKey() throws Exception {
        SysConfig request = buildValidSysConfig();
        request.setConfigKey(""); // @NotBlank 校验

        MvcResult result = performPost(API_PATH, request, mockUser("admin"));

        assertEquals(400, result.getResponse().getStatus(), "configKey 为空, HTTP 应为 400");
        ResultNode node = parseResult(result);
        assertNotEquals("0", node.code(), "校验失败不应返回 code=0");
        assertTraceIdPresent(result);
    }

    @Test
    @DisplayName("CT-4b: 参数校验失败 - status 非法值 (400)")
    void testCreateSysConfigValidationInvalidStatus() throws Exception {
        SysConfig request = buildValidSysConfig();
        request.setStatus("INVALID"); // @Pattern(ENABLED|DISABLED) 校验

        MvcResult result = performPost(API_PATH, request, mockUser("admin"));

        assertEquals(400, result.getResponse().getStatus(), "status 非法, HTTP 应为 400");
        ResultNode node = parseResult(result);
        assertNotEquals("0", node.code(), "校验失败不应返回 code=0");
        assertTraceIdPresent(result);
    }

    @Test
    @DisplayName("CT-4c: 参数校验失败 - valueType 缺失 (400)")
    void testCreateSysConfigValidationMissingValueType() throws Exception {
        SysConfig request = buildValidSysConfig();
        request.setValueType(null); // @NotBlank 校验

        MvcResult result = performPost(API_PATH, request, mockUser("admin"));

        assertEquals(400, result.getResponse().getStatus(), "valueType 缺失, HTTP 应为 400");
        ResultNode node = parseResult(result);
        assertNotEquals("0", node.code(), "校验失败不应返回 code=0");
        assertTraceIdPresent(result);
    }

    // ===== CT-5: 重复 configKey =====

    @Test
    @DisplayName("CT-5: 重复 configKey (同租户同 configKey, 409 SYS-409004)")
    void testCreateSysConfigDuplicateKey() throws Exception {
        SysConfig request = buildValidSysConfig();

        // 第一次创建: 成功
        MvcResult firstResult = performPost(API_PATH, request, mockUser("admin"));
        assertEquals(200, firstResult.getResponse().getStatus());
        assertSuccess(firstResult);
        extractAndTrackId(firstResult);

        // 第二次创建同 configKey: 应失败 409 SYS-409004
        MvcResult secondResult = performPost(API_PATH, request, mockUser("admin"));
        assertEquals(409, secondResult.getResponse().getStatus(), "重复 configKey HTTP 应为 409");
        assertError(secondResult, ERROR_CODE_DUPLICATE);
        assertTraceIdPresent(secondResult);
    }

    // ===== CT-6: 标准错误信封结构 =====

    @Test
    @DisplayName("CT-6: 标准错误信封 (code/message/traceId 字段齐全, 非成功响应)")
    void testCreateSysConfigErrorEnvelopeStructure() throws Exception {
        SysConfig request = buildValidSysConfig();
        request.setConfigKey(""); // 触发 400

        MvcResult result = performPost(API_PATH, request, mockUser("admin"));

        ResultNode node = parseResult(result);
        assertNotNull(node.code(), "错误信封必须包含 code 字段");
        assertNotEquals("0", node.code(), "错误响应 code 不应为 0");
        assertNotNull(node.message(), "错误信封必须包含 message 字段");
        assertNotNull(node.traceId(), "错误信封必须包含 traceId 字段");
        assertTrue(node.traceId().length() >= 16 && node.traceId().length() <= 64,
                "traceId 长度应在 16-64 之间");
    }

    // ===== CT-7: traceId 透传 =====

    @Test
    @DisplayName("CT-7: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)")
    void testCreateSysConfigTraceIdPropagation() throws Exception {
        SysConfig request = buildValidSysConfig();

        MvcResult result = performPost(API_PATH, request, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());

        ResultNode node = parseResult(result);
        assertNotNull(node.traceId(), "响应体应包含 traceId");
        assertTrue(node.traceId().length() >= 16 && node.traceId().length() <= 64,
                "traceId 长度应在 16-64 之间");

        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertNotNull(headerTraceId, "响应头应包含 X-Trace-Id");
        assertEquals(node.traceId(), headerTraceId,
                "响应头 X-Trace-Id 应与响应体 traceId 一致");

        extractAndTrackId(result);
    }

    // ===== CT-8: 幂等 (重复提交同 configKey 触发唯一约束) =====

    @Test
    @DisplayName("CT-8: 幂等-重复提交同 configKey (uniqueCreate 策略, 第二次 409 SYS-409004)")
    void testCreateSysConfigIdempotency() throws Exception {
        SysConfig request = buildValidSysConfig();

        // 第一次: 成功
        MvcResult first = performPost(API_PATH, request, mockUser("admin"));
        assertEquals(200, first.getResponse().getStatus());
        assertSuccess(first);
        String firstId = extractAndTrackId(first);

        // 第二次同 configKey: 幂等校验 → 409 (uniqueCreate 策略: database-unique-constraint)
        MvcResult second = performPost(API_PATH, request, mockUser("admin"));
        assertEquals(409, second.getResponse().getStatus());
        assertError(second, ERROR_CODE_DUPLICATE);

        // 验证不会产生重复数据: 查询同 configKey 只有一条
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_config WHERE config_key = ? AND deleted = false",
                Integer.class, request.getConfigKey());
        assertNotNull(count);
        assertEquals(1, count, "幂等校验后同 configKey 应只有 1 条记录");
        assertEquals(firstId, jdbcTemplate.queryForObject(
                "SELECT id FROM sys_config WHERE config_key = ? AND deleted = false",
                String.class, request.getConfigKey()), "应保留第一次创建的记录 id");
    }

    // ===== CT-9: 审计落库 =====

    @Test
    @DisplayName("CT-9: 审计落库 (@Auditable CREATE → sys_operation_log 有记录)")
    void testCreateSysConfigAuditLog() throws Exception {
        SysConfig request = buildValidSysConfig();

        MvcResult result = performPost(API_PATH, request, mockUser("admin"));
        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);
        String configId = extractAndTrackId(result);

        // 查询 sys_operation_log 表验证审计记录
        String traceId = parseResult(result).traceId();
        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_operation_log " +
                        "WHERE biz_type = 'sys_config' AND operation_type = 'CREATE' " +
                        "AND trace_id = ?",
                Integer.class, traceId);

        assertNotNull(auditCount, "审计日志查询不应返回 null");
        assertTrue(auditCount >= 1,
                "sys_operation_log 应至少有 1 条 CREATE 审计记录 (traceId=" + traceId + "), 实际: " + auditCount);

        // 验证审计记录的关键字段
        jdbcTemplate.query("SELECT biz_id, module, operation_type, biz_type FROM sys_operation_log " +
                        "WHERE biz_type = 'sys_config' AND operation_type = 'CREATE' AND trace_id = ?",
                rs -> {
                    String bizId = rs.getString("biz_id");
                    String module = rs.getString("module");
                    String opType = rs.getString("operation_type");
                    String bizType = rs.getString("biz_type");
                    assertEquals(configId, bizId, "审计记录 biz_id 应为创建的参数配置 ID");
                    assertEquals("system", module, "审计记录 module 应为 system");
                    assertEquals("CREATE", opType, "审计记录 operation_type 应为 CREATE");
                    assertEquals("sys_config", bizType, "审计记录 biz_type 应为 sys_config");
                }, traceId);
    }
}
