package com.yutong.boot.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CT-applyAiSuggestion 契约测试 (operationId: applyAiSuggestion)。
 *
 * <p>契约来源: routes.yaml web.ai.assistant (permission: ai:assistant:use),
 *   AiChatController POST /api/v1/ai/suggestions/apply
 * <p>错误码来源: errors.yaml SYS-409004 (BusinessConflictException, 必填字段缺失)
 *
 * <p>覆盖矩阵:
 * <ol>
 *   <li>CT-1 成功应用 (admin, 所有必填字段, 200, code=0, 返回确认消息 + traceId)</li>
 *   <li>CT-2 认证失败 (Mock 模式限制, @Disabled)</li>
 *   <li>CT-3 参数校验 - draftId 为空 (409 SYS-409004)</li>
 *   <li>CT-4 参数校验 - schemaVersion 为空 (409 SYS-409004)</li>
 *   <li>CT-5 参数校验 - configHash 为空 (409 SYS-409004)</li>
 *   <li>CT-6 参数校验 - expectedVersion 为空 (409 SYS-409004)</li>
 *   <li>CT-7 参数校验 - idempotencyKey 为空 (409 SYS-409004)</li>
 *   <li>CT-8 traceId 透传 (响应头 X-Trace-Id + 响应体 traceId)</li>
 * </ol>
 *
 * <p>注意: applyAiSuggestion 仅校验必填字段后返回确认字符串, 不写低代码草稿表 (第一版)。
 * 必填字段缺失抛 BusinessConflictException → SYS-409004 (409, 非 400)。
 * AI 控制器无 @RequiresPermission, 无路由级权限校验。
 * 无 DB 写入, 无需 @AfterEach 数据清理。
 */
@DisplayName("CT-applyAiSuggestion: POST /api/v1/ai/suggestions/apply 契约测试")
class CTApplyAiSuggestionTest extends AbstractContractTest {

    private static final String API_PATH = "/api/v1/ai/suggestions/apply";
    private static final String ERROR_CODE_BUSINESS_CONFLICT = "SYS-409004";

    /** 构造合法 ApplySuggestion 请求体 (所有必填字段齐全)。 */
    private Map<String, Object> buildValidRequest() {
        Map<String, Object> req = new HashMap<>();
        req.put("draftId", "ct-draft-" + UUID.randomUUID());
        req.put("draftType", "PAGE");
        req.put("draftContent", "{\"components\":[]}");
        req.put("schemaVersion", "1.0");
        req.put("configHash", "ct-hash-" + UUID.randomUUID().toString().substring(0, 8));
        req.put("expectedVersion", 1);
        req.put("idempotencyKey", "ct-apply-" + UUID.randomUUID());
        return req;
    }

    // ===== CT-1: 成功应用 =====

    @Test
    @DisplayName("CT-1: 成功应用 AI 建议 (admin, 200, code=0, 返回确认消息 + traceId)")
    void testApplyAiSuggestionSuccess() throws Exception {
        Map<String, Object> request = buildValidRequest();

        MvcResult result = performPost(API_PATH, request, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus(), "HTTP 状态码应为 200");
        assertSuccess(result);
        assertTraceIdPresent(result);

        JsonNode data = parseResult(result).data();
        assertNotNull(data, "data 不应为 null");
        assertTrue(data.isTextual(), "data 应为确认消息字符串");
        String message = data.asText();
        assertTrue(message.contains("AI 建议已校验通过"), "返回消息应包含确认信息: " + message);
        assertTrue(message.contains(request.get("draftId").toString()),
                "返回消息应包含 draftId: " + message);
    }

    // ===== CT-2: 认证失败 (Mock 模式限制) =====

    @Test
    @DisplayName("CT-2: 认证失败 (Mock 模式默认降级 admin, @Disabled 待真实 AuthAdapter)")
    @org.junit.jupiter.api.Disabled("Mock 模式无 X-Mock-User 头时默认降级 admin, 无法测未认证场景; " +
            "生产环境真实 AuthAdapter 接入后启用此用例")
    void testApplyAiSuggestionUnauthenticated() {
        // Mock 模式限制
    }

    // ===== CT-3: 参数校验 - draftId 为空 =====

    @Test
    @DisplayName("CT-3: 参数校验 - draftId 为空 (409 SYS-409004)")
    void testApplyAiSuggestionValidationBlankDraftId() throws Exception {
        Map<String, Object> request = buildValidRequest();
        request.put("draftId", "");

        MvcResult result = performPost(API_PATH, request, mockUser("admin"));

        assertEquals(409, result.getResponse().getStatus(), "draftId 为空 HTTP 应为 409");
        assertError(result, ERROR_CODE_BUSINESS_CONFLICT);
        assertTraceIdPresent(result);
    }

    // ===== CT-4: 参数校验 - schemaVersion 为空 =====

    @Test
    @DisplayName("CT-4: 参数校验 - schemaVersion 为空 (409 SYS-409004)")
    void testApplyAiSuggestionValidationBlankSchemaVersion() throws Exception {
        Map<String, Object> request = buildValidRequest();
        request.put("schemaVersion", "");

        MvcResult result = performPost(API_PATH, request, mockUser("admin"));

        assertEquals(409, result.getResponse().getStatus(), "schemaVersion 为空 HTTP 应为 409");
        assertError(result, ERROR_CODE_BUSINESS_CONFLICT);
        assertTraceIdPresent(result);
    }

    // ===== CT-5: 参数校验 - configHash 为空 =====

    @Test
    @DisplayName("CT-5: 参数校验 - configHash 为空 (409 SYS-409004)")
    void testApplyAiSuggestionValidationBlankConfigHash() throws Exception {
        Map<String, Object> request = buildValidRequest();
        request.put("configHash", "");

        MvcResult result = performPost(API_PATH, request, mockUser("admin"));

        assertEquals(409, result.getResponse().getStatus(), "configHash 为空 HTTP 应为 409");
        assertError(result, ERROR_CODE_BUSINESS_CONFLICT);
        assertTraceIdPresent(result);
    }

    // ===== CT-6: 参数校验 - expectedVersion 为空 =====

    @Test
    @DisplayName("CT-6: 参数校验 - expectedVersion 为空 (409 SYS-409004)")
    void testApplyAiSuggestionValidationNullExpectedVersion() throws Exception {
        Map<String, Object> request = buildValidRequest();
        request.put("expectedVersion", null);

        MvcResult result = performPost(API_PATH, request, mockUser("admin"));

        assertEquals(409, result.getResponse().getStatus(), "expectedVersion 为空 HTTP 应为 409");
        assertError(result, ERROR_CODE_BUSINESS_CONFLICT);
        assertTraceIdPresent(result);
    }

    // ===== CT-7: 参数校验 - idempotencyKey 为空 =====

    @Test
    @DisplayName("CT-7: 参数校验 - idempotencyKey 为空 (409 SYS-409004)")
    void testApplyAiSuggestionValidationBlankIdempotencyKey() throws Exception {
        Map<String, Object> request = buildValidRequest();
        request.put("idempotencyKey", "");

        MvcResult result = performPost(API_PATH, request, mockUser("admin"));

        assertEquals(409, result.getResponse().getStatus(), "idempotencyKey 为空 HTTP 应为 409");
        assertError(result, ERROR_CODE_BUSINESS_CONFLICT);
        assertTraceIdPresent(result);
    }

    // ===== CT-8: traceId 透传 =====

    @Test
    @DisplayName("CT-8: traceId 透传 (响应头 X-Trace-Id + 响应体 traceId 一致)")
    void testApplyAiSuggestionTraceIdPropagation() throws Exception {
        Map<String, Object> request = buildValidRequest();

        MvcResult result = performPost(API_PATH, request, mockUser("admin"));

        assertEquals(200, result.getResponse().getStatus());
        assertSuccess(result);

        ResultNode node = parseResult(result);
        assertNotNull(node.traceId(), "响应体应包含 traceId");
        assertTrue(node.traceId().length() >= 16 && node.traceId().length() <= 64,
                "traceId 长度应在 16-64 之间");

        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertNotNull(headerTraceId, "响应头应包含 X-Trace-Id");
        assertEquals(node.traceId(), headerTraceId,
                "响应头 X-Trace-Id 应与响应体 traceId 一致");
    }
}
