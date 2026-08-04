package com.yutong.ai.gateway.service;

import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/**
 * AI 工具注册表（白名单）。设计来源: 13-AI能力设计
 * <p>
 * 第一版禁止 AI 直接执行: delete_data, update_config, execute_sql, auto_approve。
 * 仅允许受控只读查询与草稿生成类工具。
 */
@Service
public class AiToolRegistry {

    /** 允许的工具映射 */
    private static final Map<String, ToolMeta> TOOLS = Map.of(
            "query_meta_model", new ToolMeta("query_meta_model", "ai:tool:meta", "A3", "受控只读元模型查询", 100),
            "query_openapi", new ToolMeta("query_openapi", "ai:tool:api", "A3", "OpenAPI 查询", 200),
            "query_dict", new ToolMeta("query_dict", "ai:tool:dict", "A3", "字典查询", 200),
            "query_operation_log_summary", new ToolMeta("query_operation_log_summary", "ai:tool:log-summary", "A3", "操作日志摘要（脱敏）", 50),
            "generate_page_draft", new ToolMeta("generate_page_draft", "ai:tool:generate", "A2", "生成页面草稿", 1),
            "generate_sql_draft", new ToolMeta("generate_sql_draft", "ai:tool:sql", "A2", "生成 SQL 草稿", 1)
    );

    /** 禁止的工具 */
    private static final Set<String> FORBIDDEN_TOOLS = Set.of(
            "delete_data", "update_config", "execute_sql", "auto_approve"
    );

    /** 工具元信息 */
    public record ToolMeta(String name, String permissionCode, String riskLevel, String description, int maxResults) {}

    /**
     * 获取工具元信息。不存在返回 null，不抛异常。
     */
    public ToolMeta getTool(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return TOOLS.get(name);
    }

    /**
     * 判断工具是否在允许的白名单中。
     */
    public boolean isAllowed(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return TOOLS.containsKey(name);
    }

    /**
     * 判断工具是否被禁止使用。
     */
    public boolean isForbidden(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return FORBIDDEN_TOOLS.contains(name);
    }

    /**
     * 校验工具合法性。
     * GA2-03-5: 抛 AI-403001 (ai.error.toolDenied)，对齐 errors.yaml 与 64-安全威胁模型 TC-SEC-AI-001。
     *
     * @throws BusinessException (AI-403001) 工具名为空 / 被禁止 / 未注册时抛出
     */
    public void validateTool(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.AI_TOOL_DENIED, "工具名不能为空");
        }
        if (FORBIDDEN_TOOLS.contains(name)) {
            throw new BusinessException(ErrorCode.AI_TOOL_DENIED, "禁止使用的工具: " + name);
        }
        if (!TOOLS.containsKey(name)) {
            throw new BusinessException(ErrorCode.AI_TOOL_DENIED, "未注册的工具: " + name);
        }
    }

    /**
     * 获取所有允许的工具名称。
     */
    public Set<String> getAllowedToolNames() {
        return TOOLS.keySet();
    }

    /**
     * 获取所有禁止的工具名称。
     */
    public Set<String> getForbiddenToolNames() {
        return FORBIDDEN_TOOLS;
    }
}
