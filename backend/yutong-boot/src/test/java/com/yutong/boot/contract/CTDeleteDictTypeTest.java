package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
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
 * GA2-L185 CT-deleteDictType 契约测试。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-deleteDictType 验收标准)
 * <p>策略来源: operation-policies.yaml deleteDictType → guardedDelete profile (natural-by-resource-state)
 * <p>契约来源: openapi.yaml DELETE /api/v1/dict-types/{id} (x-permission: system:dict:delete,
 *    x-error-codes: [SYS-404001])
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功删除 (200, code=0, 逻辑删除 deleted=true)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (viewer 无 system:dict:delete, 403 AUTH-403001)</li>
 *   <li>CT-4 字典类型不存在 (404 SYS-404001)</li>
 *   <li>CT-5 已删除再删 (404 SYS-404001, guardedDelete 自然幂等)</li>
 *   <li>CT-6 审计落库 (sys_operation_log DELETE 记录)</li>
 *   <li>CT-7 traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)</li>
 * </ol>
 *
 * <p>数据隔离: 每个用例独立创建测试数据 (UUID 后缀 dictType), @AfterEach 物理清理。
 */
@DisplayName("CT-deleteDictType: DELETE /api/v1/dict-types/{id} 契约测试")
class CTDeleteDictTypeTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/dict-types";
    private static final String ERROR_CODE_FORBIDDEN = "AUTH-403001";
    private static final String ERROR_CODE_NOT_FOUND = "SYS-404001";

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

    /** 构造合法 DictType 请求体 (唯一 dictType)。 */
    private DictType buildValidDictType() {
        DictType t = new DictType();
        t.setDictType("CT-L185-D-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        t.setDictName("CT删除测试字典类型");
        t.setStatus("ENABLED");
        t.setSortNo(0);
        return t;
    }

    /** 创建字典类型并跟踪 ID。 */
    private String createDictTypeAndTrack() throws Exception {
        DictType request = buildValidDictType();
        MvcResult result = performPost(API_PATH, request, mockUser("admin"));
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertNotNull(data, "创建响应 data 不应为 null");
        String id = data.get("id").asText();
        createdIds.add(id);
        return id;
    }

    // ===== CT-1: 成功删除 =====

    @Test
    @DisplayName("CT-1: 成功删除字典类型 (admin 用户, 200, code=0, 逻辑删除 deleted=true)")
    void testDeleteDictTypeSuccess() throws Exception {
        String id = createDictTypeAndTrack();

        MvcResult result = performDelete(API_PATH + "/" + id, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        // 验证逻辑删除: deleted=true (物理查询绕过逻辑删除)
        Boolean deleted = jdbcTemplate.queryForObject(
                "SELECT deleted FROM sys_dict_type WHERE id = ?", Boolean.class, id);
        assertNotNull(deleted, "字典类型记录应存在 (物理查询)");
        assertTrue(deleted, "删除后 deleted 应为 true (逻辑删除)");
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testDeleteDictTypeUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (viewer 无 system:dict:delete, 403 AUTH-403001)")
    void testDeleteDictTypeForbidden() throws Exception {
        String id = createDictTypeAndTrack();

        MvcResult result = performDelete(API_PATH + "/" + id, mockUser("viewer"));

        assertEquals(403, result.getResponse().getStatus(), "viewer 无删除权限, HTTP 应为 403");
        assertError(result, ERROR_CODE_FORBIDDEN);
        assertTraceIdPresent(result);

        // 验证字典类型未被删除 (权限拒绝不应有副作用)
        Boolean deleted = jdbcTemplate.queryForObject(
                "SELECT deleted FROM sys_dict_type WHERE id = ?", Boolean.class, id);
        assertNotNull(deleted, "字典类型记录应存在");
        assertFalse(deleted, "权限拒绝时字典类型不应被删除");
    }

    // ===== CT-4: 字典类型不存在 =====

    @Test
    @DisplayName("CT-4: 字典类型不存在 (404 SYS-404001)")
    void testDeleteDictTypeNotFound() throws Exception {
        String nonExistentId = "01NOTEXIST00000000000000CT";

        MvcResult result = performDelete(API_PATH + "/" + nonExistentId, mockUser("admin"));

        assertEquals(404, result.getResponse().getStatus(), "字典类型不存在, HTTP 应为 404");
        assertError(result, ERROR_CODE_NOT_FOUND);
        assertTraceIdPresent(result);
    }

    // ===== CT-5: 已删除再删 =====

    @Test
    @DisplayName("CT-5: 已删除再删 (guardedDelete 自然幂等, 404 SYS-404001)")
    void testDeleteDictTypeAlreadyDeleted() throws Exception {
        String id = createDictTypeAndTrack();

        // 第一次删除: 成功
        MvcResult firstResult = performDelete(API_PATH + "/" + id, mockUser("admin"));
        assertEquals(200, firstResult.getResponse().getStatus(), "第一次删除应成功");
        assertSuccess(firstResult);

        // 第二次删除: 已删除 → selectById 返回 null → 404 SYS-404001
        MvcResult secondResult = performDelete(API_PATH + "/" + id, mockUser("admin"));
        assertEquals(404, secondResult.getResponse().getStatus(), "已删除再删应返回 404");
        assertError(secondResult, ERROR_CODE_NOT_FOUND);
        assertTraceIdPresent(secondResult);
    }

    // ===== CT-6: 审计落库 =====

    @Test
    @DisplayName("CT-6: 审计落库 (@Auditable DELETE → sys_operation_log 有记录)")
    void testDeleteDictTypeAuditLog() throws Exception {
        String id = createDictTypeAndTrack();

        MvcResult result = performDelete(API_PATH + "/" + id, mockUser("admin"));
        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);

        String traceId = parseResult(result).traceId();
        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_operation_log " +
                        "WHERE biz_type = 'sys_dict_type' AND operation_type = 'DELETE' " +
                        "AND trace_id = ?",
                Integer.class, traceId);

        assertNotNull(auditCount, "审计日志查询不应返回 null");
        assertTrue(auditCount >= 1,
                "sys_operation_log 应至少有 1 条 DELETE 审计记录 (traceId=" + traceId + "), 实际: " + auditCount);

        jdbcTemplate.query("SELECT biz_id, module, operation_type, biz_type FROM sys_operation_log " +
                        "WHERE biz_type = 'sys_dict_type' AND operation_type = 'DELETE' AND trace_id = ?",
                rs -> {
                    assertEquals(id, rs.getString("biz_id"), "审计记录 biz_id 应为删除的字典类型 ID");
                    assertEquals("system", rs.getString("module"), "审计记录 module 应为 system");
                    assertEquals("DELETE", rs.getString("operation_type"), "审计记录 operation_type 应为 DELETE");
                    assertEquals("sys_dict_type", rs.getString("biz_type"), "审计记录 biz_type 应为 sys_dict_type");
                }, traceId);
    }

    // ===== CT-7: traceId 透传 =====

    @Test
    @DisplayName("CT-7: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)")
    void testDeleteDictTypeTraceIdPropagation() throws Exception {
        String id = createDictTypeAndTrack();

        MvcResult result = performDelete(API_PATH + "/" + id, mockUser("admin"));

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
