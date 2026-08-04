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
 * GA2-L185 CT-updateDictType 契约测试。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-updateDictType 验收标准)
 * <p>策略来源: operation-policies.yaml updateDictType → versionedUpdate profile (optimistic-version)
 * <p>契约来源: openapi.yaml PUT /api/v1/dict-types/{id} (x-permission: system:dict:edit,
 *    x-error-codes: [SYS-404001, SYS-409001, SYS-409004])
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功更新 (200, code=0, 字段更新 + traceId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (viewer 无 system:dict:edit, 403 AUTH-403001)</li>
 *   <li>CT-4 参数校验失败 - dictName 空 (400)</li>
 *   <li>CT-5 字典类型不存在 (404 SYS-404001)</li>
 *   <li>CT-6 版本冲突 (乐观锁 SYS-409001)</li>
 *   <li>CT-7 重复编码 (409 SYS-409004)</li>
 *   <li>CT-8 审计落库 (sys_operation_log UPDATE 记录)</li>
 *   <li>CT-9 traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)</li>
 * </ol>
 *
 * <p>数据隔离: 每个用例独立创建测试数据 (UUID 后缀 dictType), @AfterEach 清理。
 *
 * <p>注意: DictType 无 GET /{id} 详情接口, 更新前通过创建请求保留原始 dictType 用于更新体。
 */
@DisplayName("CT-updateDictType: PUT /api/v1/dict-types/{id} 契约测试")
class CTUpdateDictTypeTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/dict-types";
    private static final String ERROR_CODE_FORBIDDEN = "AUTH-403001";
    private static final String ERROR_CODE_NOT_FOUND = "SYS-404001";
    private static final String ERROR_CODE_DUPLICATE = "SYS-409004";
    private static final String ERROR_CODE_VERSION_CONFLICT = "SYS-409001";

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
        t.setDictType("CT-L185-U-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        t.setDictName("CT更新测试字典类型");
        t.setStatus("ENABLED");
        t.setSortNo(0);
        return t;
    }

    /** 创建字典类型并跟踪 ID, 返回创建请求 (含原始 dictType)。 */
    private DictType createDictTypeAndTrack() throws Exception {
        DictType request = buildValidDictType();
        MvcResult result = performPost(API_PATH, request, mockUser("admin"));
        assertSuccess(result);
        JsonNode data = parseResult(result).data();
        assertNotNull(data, "创建响应 data 不应为 null");
        String id = data.get("id").asText();
        createdIds.add(id);
        request.setId(id);
        return request;
    }

    /** 构造更新请求体 (保持原 dictType, 修改 name)。 */
    private DictType buildUpdateRequest(String originalDictType, int version) {
        DictType t = new DictType();
        t.setDictType(originalDictType);
        t.setDictName("CT更新后字典类型-" + UUID.randomUUID().toString().substring(0, 4));
        t.setStatus("ENABLED");
        t.setSortNo(1);
        t.setVersion(version);
        return t;
    }

    // ===== CT-1: 成功更新 =====

    @Test
    @DisplayName("CT-1: 成功更新字典类型 (admin 用户, 200, code=0, 字段更新 + traceId)")
    void testUpdateDictTypeSuccess() throws Exception {
        DictType created = createDictTypeAndTrack();
        String id = created.getId();

        DictType updateReq = buildUpdateRequest(created.getDictType(), 0);
        MvcResult result = performPut(API_PATH + "/" + id, updateReq, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        ResultNode node = parseResult(result);
        JsonNode data = node.data();
        assertEquals(id, data.get("id").asText(), "返回 id 应与请求 id 一致");
        assertEquals(updateReq.getDictName(), data.get("dictName").asText(), "dictName 应已更新");
        assertEquals("ENABLED", data.get("status").asText());
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testUpdateDictTypeUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (viewer 无 system:dict:edit, 403 AUTH-403001)")
    void testUpdateDictTypeForbidden() throws Exception {
        DictType created = createDictTypeAndTrack();
        DictType update = buildUpdateRequest(created.getDictType(), 0);

        MvcResult result = performPut(API_PATH + "/" + created.getId(), update, mockUser("viewer"));

        assertEquals(403, result.getResponse().getStatus(), "viewer 无写权限, HTTP 应为 403");
        assertError(result, ERROR_CODE_FORBIDDEN);
        assertTraceIdPresent(result);
    }

    // ===== CT-4: 参数校验失败 =====

    @Test
    @DisplayName("CT-4: 参数校验失败 - dictName 为空 (400)")
    void testUpdateDictTypeValidationBlankName() throws Exception {
        DictType created = createDictTypeAndTrack();
        DictType update = buildUpdateRequest(created.getDictType(), 0);
        update.setDictName(""); // @NotBlank 校验

        MvcResult result = performPut(API_PATH + "/" + created.getId(), update, mockUser("admin"));

        assertEquals(400, result.getResponse().getStatus(), "dictName 为空, HTTP 应为 400");
        ResultNode node = parseResult(result);
        assertNotEquals("0", node.code(), "校验失败不应返回 code=0");
        assertTraceIdPresent(result);
    }

    // ===== CT-5: 字典类型不存在 =====

    @Test
    @DisplayName("CT-5: 字典类型不存在 (404 SYS-404001)")
    void testUpdateDictTypeNotFound() throws Exception {
        DictType update = buildUpdateRequest("ANYTYPE", 0);
        String nonExistentId = "01NOTEXIST00000000000000CT";

        MvcResult result = performPut(API_PATH + "/" + nonExistentId, update, mockUser("admin"));

        assertEquals(404, result.getResponse().getStatus(), "字典类型不存在, HTTP 应为 404");
        assertError(result, ERROR_CODE_NOT_FOUND);
        assertTraceIdPresent(result);
    }

    // ===== CT-6: 版本冲突 (乐观锁) =====

    @Test
    @DisplayName("CT-6: 版本冲突 (versionedUpdate 乐观锁, 旧 version → SYS-409001)")
    void testUpdateDictTypeVersionConflict() throws Exception {
        DictType created = createDictTypeAndTrack();
        String id = created.getId();
        String originalDictType = created.getDictType();

        // 第一次更新 version=0 → 成功 (DB version 0→1)
        DictType firstUpdate = buildUpdateRequest(originalDictType, 0);
        MvcResult firstResult = performPut(API_PATH + "/" + id, firstUpdate, mockUser("admin"));
        assertEquals(200, firstResult.getResponse().getStatus(), "第一次更新应成功");
        assertSuccess(firstResult);

        // 第二次更新仍用 version=0 (stale, DB 已是 version=1) → 乐观锁冲突 SYS-409001
        DictType staleUpdate = buildUpdateRequest(originalDictType, 0);
        staleUpdate.setDictName("冲突更新-应失败");
        MvcResult conflictResult = performPut(API_PATH + "/" + id, staleUpdate, mockUser("admin"));

        assertEquals(409, conflictResult.getResponse().getStatus(), "版本冲突 HTTP 应为 409");
        assertError(conflictResult, ERROR_CODE_VERSION_CONFLICT);
        assertTraceIdPresent(conflictResult);
    }

    // ===== CT-7: 重复编码 =====

    @Test
    @DisplayName("CT-7: 重复编码 (更新时改为已存在 dictType, 409 SYS-409004)")
    void testUpdateDictTypeDuplicateCode() throws Exception {
        // 创建字典类型 A 和 B (独立 dictType)
        DictType createdA = createDictTypeAndTrack();
        DictType createdB = createDictTypeAndTrack();

        // 更新 A, 把 dictType 改为 B 的 dictType → 409 SYS-409004
        DictType update = buildUpdateRequest(createdB.getDictType(), 0);
        update.setDictName("尝试占用B的编码");
        MvcResult result = performPut(API_PATH + "/" + createdA.getId(), update, mockUser("admin"));

        assertEquals(409, result.getResponse().getStatus(), "重复编码 HTTP 应为 409");
        assertError(result, ERROR_CODE_DUPLICATE);
        assertTraceIdPresent(result);
    }

    // ===== CT-8: 审计落库 =====

    @Test
    @DisplayName("CT-8: 审计落库 (@Auditable UPDATE → sys_operation_log 有记录)")
    void testUpdateDictTypeAuditLog() throws Exception {
        DictType created = createDictTypeAndTrack();
        String id = created.getId();

        DictType update = buildUpdateRequest(created.getDictType(), 0);
        MvcResult result = performPut(API_PATH + "/" + id, update, mockUser("admin"));
        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);

        String traceId = parseResult(result).traceId();
        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_operation_log " +
                        "WHERE biz_type = 'sys_dict_type' AND operation_type = 'UPDATE' " +
                        "AND trace_id = ?",
                Integer.class, traceId);

        assertNotNull(auditCount, "审计日志查询不应返回 null");
        assertTrue(auditCount >= 1,
                "sys_operation_log 应至少有 1 条 UPDATE 审计记录 (traceId=" + traceId + "), 实际: " + auditCount);

        jdbcTemplate.query("SELECT biz_id, module, operation_type, biz_type FROM sys_operation_log " +
                        "WHERE biz_type = 'sys_dict_type' AND operation_type = 'UPDATE' AND trace_id = ?",
                rs -> {
                    assertEquals(id, rs.getString("biz_id"), "审计记录 biz_id 应为更新的字典类型 ID");
                    assertEquals("system", rs.getString("module"), "审计记录 module 应为 system");
                    assertEquals("UPDATE", rs.getString("operation_type"), "审计记录 operation_type 应为 UPDATE");
                    assertEquals("sys_dict_type", rs.getString("biz_type"), "审计记录 biz_type 应为 sys_dict_type");
                }, traceId);
    }

    // ===== CT-9: traceId 透传 =====

    @Test
    @DisplayName("CT-9: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)")
    void testUpdateDictTypeTraceIdPropagation() throws Exception {
        DictType created = createDictTypeAndTrack();
        DictType update = buildUpdateRequest(created.getDictType(), 0);

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
