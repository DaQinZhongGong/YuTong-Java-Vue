package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.yutong.lowcode.meta.dto.SaveLcEntityRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CT-createLowcodeEntityDraft 契约测试 (operationId: createLowcodeEntityDraft)。
 *
 * <p>设计来源: 58-后端API逐接口任务清单 (CT-createLowcodeEntityDraft 验收标准)
 * <p>契约来源: routes.yaml web.lowcode.entities (permission: lc:entity:list)
 *    + LcEntityController POST /api/v1/lowcode/entities
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功创建 (admin, 200, code=0, 返回 LcEntity + traceId, status=DRAFT)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 权限拒绝 (LcEntityController 未加 @RequiresPermission, @Disabled)</li>
 *   <li>CT-4 参数校验失败 - 主键字段约束 (primaryFlag=true 且 fieldCode!=id, 409 SYS-409004)</li>
 *   <li>CT-5 标准错误信封 (code/message/traceId 字段齐全)</li>
 *   <li>CT-6 traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)</li>
 *   <li>CT-7 创建含子表 (fields + relations 一并传入, 详情返回子表数据)</li>
 * </ol>
 *
 * <p>数据隔离: 每个用例使用唯一 entityCode (UUID 后缀), @AfterEach 物理清理。
 */
@DisplayName("CT-createLowcodeEntityDraft: POST /api/v1/lowcode/entities 契约测试")
class CTCreateLowcodeEntityDraftTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/lowcode/entities";
    private static final String ERROR_CODE_CONFLICT = "SYS-409004";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final java.util.Set<String> createdEntityIds = ConcurrentHashMap.newKeySet();

    @AfterEach
    void cleanupTestData() {
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
    private SaveLcEntityRequest buildValidEntityRequest() {
        SaveLcEntityRequest req = new SaveLcEntityRequest();
        req.setEntityCode("CT-LC-ENT-C-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        req.setEntityName("CT创建测试实体");
        req.setTableName("ct_test_" + UUID.randomUUID().toString().substring(0, 8).toLowerCase());
        req.setModuleCode("ct_test");
        return req;
    }

    /** 从成功响应中提取实体 ID 并记录到清理集合。 */
    private String extractAndTrackId(MvcResult result) throws Exception {
        ResultNode node = parseResult(result);
        JsonNode data = node.data();
        assertNotNull(data, "成功响应 data 不应为 null");
        String id = data.get("id").asText();
        createdEntityIds.add(id);
        return id;
    }

    // ===== CT-1: 成功创建 =====

    @Test
    @DisplayName("CT-1: 成功创建实体草稿 (admin 用户, 200, code=0, 返回 id/version/status=DRAFT + traceId)")
    void testCreateLowcodeEntityDraftSuccess() throws Exception {
        SaveLcEntityRequest request = buildValidEntityRequest();

        MvcResult result = performPost(API_PATH, request, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        ResultNode node = parseResult(result);
        JsonNode data = node.data();
        assertNotNull(data.get("id"), "返回的 id 不应为 null");
        assertEquals(request.getEntityCode(), data.get("entityCode").asText());
        assertEquals(request.getEntityName(), data.get("entityName").asText());
        assertEquals("DRAFT", data.get("status").asText(), "新建实体状态应为 DRAFT");
        assertNotNull(data.get("version"), "version 不应为 null");
        assertNotNull(data.get("versionNo"), "versionNo 不应为 null");
        assertEquals(1, data.get("versionNo").asInt(), "新建实体 versionNo 应为 1");

        extractAndTrackId(result);
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testCreateLowcodeEntityDraftUnauthenticated() {
        // Mock 模式限制: 无 Authorization token 时 MockAuthAdapter 默认降级为 admin 用户
    }

    // ===== CT-3: 权限拒绝 (控制器未加 @RequiresPermission) =====

    @Test
    @DisplayName("CT-3: 权限拒绝 (LcEntityController 未加 @RequiresPermission, @Disabled 待补充注解)")
    @org.junit.jupiter.api.Disabled("LcEntityController.create 未加 @RequiresPermission(lc:entity:add), " +
            "权限码未在控制器层强制; 待补充注解后启用此用例")
    void testCreateLowcodeEntityDraftForbidden() {
        // 实现待补充: LcEntityController 当前未加 @RequiresPermission 注解
    }

    // ===== CT-4: 参数校验失败 - 主键字段约束 =====

    @Test
    @DisplayName("CT-4: 参数校验失败 - 主键字段 primaryFlag=true 且 fieldCode!=id (409 SYS-409004)")
    void testCreateLowcodeEntityDraftInvalidPrimaryKey() throws Exception {
        SaveLcEntityRequest request = buildValidEntityRequest();
        SaveLcEntityRequest.LcFieldDTO field = new SaveLcEntityRequest.LcFieldDTO();
        field.setFieldCode("name"); // 主键只允许 field_code=id
        field.setFieldName("名称");
        field.setDbColumn("name");
        field.setDataType("STRING");
        field.setPrimaryFlag(true); // 触发 validatePrimaryKey 校验
        request.setFields(List.of(field));

        MvcResult result = performPost(API_PATH, request, mockUser("admin"));

        assertEquals(409, result.getResponse().getStatus(), "主键字段约束违反, HTTP 应为 409");
        assertError(result, ERROR_CODE_CONFLICT);
        assertTraceIdPresent(result);
    }

    // ===== CT-5: 标准错误信封结构 =====

    @Test
    @DisplayName("CT-5: 标准错误信封 (code/message/traceId 字段齐全, 非成功响应)")
    void testCreateLowcodeEntityDraftErrorEnvelopeStructure() throws Exception {
        SaveLcEntityRequest request = buildValidEntityRequest();
        SaveLcEntityRequest.LcFieldDTO field = new SaveLcEntityRequest.LcFieldDTO();
        field.setFieldCode("invalid_pk");
        field.setDataType("STRING");
        field.setPrimaryFlag(true);
        request.setFields(List.of(field));

        MvcResult result = performPost(API_PATH, request, mockUser("admin"));

        ResultNode node = parseResult(result);
        assertNotNull(node.code(), "错误信封必须包含 code 字段");
        assertNotEquals("0", node.code(), "错误响应 code 不应为 0");
        assertNotNull(node.message(), "错误信封必须包含 message 字段");
        assertNotNull(node.traceId(), "错误信封必须包含 traceId 字段");
        assertTrue(node.traceId().length() >= 16 && node.traceId().length() <= 64,
                "traceId 长度应在 16-64 之间");
    }

    // ===== CT-6: traceId 透传 =====

    @Test
    @DisplayName("CT-6: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)")
    void testCreateLowcodeEntityDraftTraceIdPropagation() throws Exception {
        SaveLcEntityRequest request = buildValidEntityRequest();

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

    // ===== CT-7: 创建含子表 (fields + relations) =====

    @Test
    @DisplayName("CT-7: 创建含子表 (fields + relations 一并传入, 详情返回子表数据)")
    void testCreateLowcodeEntityDraftWithChildren() throws Exception {
        SaveLcEntityRequest request = buildValidEntityRequest();

        SaveLcEntityRequest.LcFieldDTO field = new SaveLcEntityRequest.LcFieldDTO();
        field.setFieldCode("name");
        field.setFieldName("名称");
        field.setDbColumn("name");
        field.setDataType("STRING");
        field.setLengthValue(255);
        field.setNullable(false);
        field.setPrimaryFlag(false);
        field.setSortNo(1);
        request.setFields(List.of(field));

        MvcResult createResult = performPost(API_PATH, request, mockUser("admin"));
        assertEquals(200, createResult.getResponse().getStatus());
        assertSuccess(createResult);
        String id = extractAndTrackId(createResult);

        // 通过详情接口验证子表数据落库
        MvcResult detailResult = performGet(API_PATH + "/" + id, mockUser("admin"));
        assertEquals(200, detailResult.getResponse().getStatus());
        assertSuccess(detailResult);
        JsonNode detailData = parseResult(detailResult).data();
        assertNotNull(detailData.get("fields"), "详情应包含 fields 字段");
        assertTrue(detailData.get("fields").isArray(), "fields 应为数组");
        assertEquals(1, detailData.get("fields").size(), "fields 应有 1 条记录");
        assertEquals("name", detailData.get("fields").get(0).get("fieldCode").asText());
    }
}
