package com.yutong.ai.gateway.service;

import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AI 工具注册表（白名单）单元测试。设计来源: 13-AI能力设计 工具路由白名单
 * 约束:
 * - 仅允许受控只读查询与草稿生成类工具
 * - 禁止 delete_data / update_config / execute_sql / auto_approve
 */
@DisplayName("AI 工具白名单注册表")
class AiToolRegistryTest {

    private final AiToolRegistry registry = new AiToolRegistry();

    @Nested
    @DisplayName("允许的工具白名单")
    class AllowedTools {

        @Test
        @DisplayName("query_meta_model 允许")
        void queryMetaModelAllowed() {
            assertTrue(registry.isAllowed("query_meta_model"));
            assertFalse(registry.isForbidden("query_meta_model"));
            assertNotNull(registry.getTool("query_meta_model"));
            assertEquals("A3", registry.getTool("query_meta_model").riskLevel());
        }

        @Test
        @DisplayName("query_openapi 允许")
        void queryOpenApiAllowed() {
            assertTrue(registry.isAllowed("query_openapi"));
            assertNotNull(registry.getTool("query_openapi"));
        }

        @Test
        @DisplayName("query_dict 允许")
        void queryDictAllowed() {
            assertTrue(registry.isAllowed("query_dict"));
        }

        @Test
        @DisplayName("query_operation_log_summary 允许")
        void queryOperationLogSummaryAllowed() {
            assertTrue(registry.isAllowed("query_operation_log_summary"));
        }

        @Test
        @DisplayName("generate_page_draft 允许 (A2 风险)")
        void generatePageDraftAllowed() {
            assertTrue(registry.isAllowed("generate_page_draft"));
            assertEquals("A2", registry.getTool("generate_page_draft").riskLevel());
        }

        @Test
        @DisplayName("generate_sql_draft 允许 (A2 风险)")
        void generateSqlDraftAllowed() {
            assertTrue(registry.isAllowed("generate_sql_draft"));
            assertEquals("A2", registry.getTool("generate_sql_draft").riskLevel());
        }

        @Test
        @DisplayName("getAllowedToolNames 返回 6 个允许工具")
        void allowedToolCount() {
            assertEquals(6, registry.getAllowedToolNames().size());
        }
    }

    @Nested
    @DisplayName("禁止的工具")
    class ForbiddenTools {

        @Test
        @DisplayName("delete_data 禁止")
        void deleteDataForbidden() {
            assertTrue(registry.isForbidden("delete_data"));
            assertFalse(registry.isAllowed("delete_data"));
            assertNull(registry.getTool("delete_data"));
        }

        @Test
        @DisplayName("update_config 禁止")
        void updateConfigForbidden() {
            assertTrue(registry.isForbidden("update_config"));
        }

        @Test
        @DisplayName("execute_sql 禁止")
        void executeSqlForbidden() {
            assertTrue(registry.isForbidden("execute_sql"));
        }

        @Test
        @DisplayName("auto_approve 禁止")
        void autoApproveForbidden() {
            assertTrue(registry.isForbidden("auto_approve"));
        }

        @Test
        @DisplayName("getForbiddenToolNames 返回 4 个禁止工具")
        void forbiddenToolCount() {
            assertEquals(4, registry.getForbiddenToolNames().size());
        }
    }

    @Nested
    @DisplayName("validateTool: 合法工具不抛异常")
    class ValidateToolLegal {

        @Test
        @DisplayName("query_meta_model 通过校验")
        void queryMetaModelPasses() {
            assertDoesNotThrow(() -> registry.validateTool("query_meta_model"));
        }

        @Test
        @DisplayName("generate_page_draft 通过校验")
        void generatePageDraftPasses() {
            assertDoesNotThrow(() -> registry.validateTool("generate_page_draft"));
        }

        @Test
        @DisplayName("generate_sql_draft 通过校验")
        void generateSqlDraftPasses() {
            assertDoesNotThrow(() -> registry.validateTool("generate_sql_draft"));
        }
    }

    @Nested
    @DisplayName("validateTool: 非法工具抛异常")
    class ValidateToolIllegal {

        @Test
        @DisplayName("禁止工具 delete_data 抛 AI-403001")
        void deleteDataThrows() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> registry.validateTool("delete_data"));
            assertEquals(ErrorCode.AI_TOOL_DENIED, ex.errorCode());
            assertTrue(ex.getMessage().contains("禁止"));
        }

        @Test
        @DisplayName("禁止工具 update_config 抛 AI-403001")
        void updateConfigThrows() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> registry.validateTool("update_config"));
            assertEquals(ErrorCode.AI_TOOL_DENIED, ex.errorCode());
        }

        @Test
        @DisplayName("禁止工具 execute_sql 抛 AI-403001")
        void executeSqlThrows() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> registry.validateTool("execute_sql"));
            assertEquals(ErrorCode.AI_TOOL_DENIED, ex.errorCode());
        }

        @Test
        @DisplayName("禁止工具 auto_approve 抛 AI-403001")
        void autoApproveThrows() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> registry.validateTool("auto_approve"));
            assertEquals(ErrorCode.AI_TOOL_DENIED, ex.errorCode());
        }

        @Test
        @DisplayName("未注册工具抛 AI-403001")
        void unknownToolThrows() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> registry.validateTool("hack_server"));
            assertEquals(ErrorCode.AI_TOOL_DENIED, ex.errorCode());
            assertTrue(ex.getMessage().contains("未注册"));
        }

        @Test
        @DisplayName("null 工具名抛 AI-403001")
        void nullThrows() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> registry.validateTool(null));
            assertEquals(ErrorCode.AI_TOOL_DENIED, ex.errorCode());
        }

        @Test
        @DisplayName("空字符串工具名抛 AI-403001")
        void blankThrows() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> registry.validateTool("   "));
            assertEquals(ErrorCode.AI_TOOL_DENIED, ex.errorCode());
        }
    }

    @Nested
    @DisplayName("getTool: 元信息查询")
    class GetTool {

        @Test
        @DisplayName("返回的 ToolMeta 字段完整")
        void toolMetaFields() {
            AiToolRegistry.ToolMeta meta = registry.getTool("query_meta_model");
            assertNotNull(meta);
            assertEquals("query_meta_model", meta.name());
            assertEquals("ai:tool:meta", meta.permissionCode());
            assertEquals("A3", meta.riskLevel());
            assertEquals(100, meta.maxResults());
            assertFalse(meta.description().isBlank());
        }

        @Test
        @DisplayName("null 名返回 null")
        void nullNameReturnsNull() {
            assertNull(registry.getTool(null));
        }

        @Test
        @DisplayName("空白名返回 null")
        void blankNameReturnsNull() {
            assertNull(registry.getTool("  "));
        }

        @Test
        @DisplayName("未知名返回 null")
        void unknownNameReturnsNull() {
            assertNull(registry.getTool("unknown_tool"));
        }
    }
}
