package com.yutong.ai.tool.mcp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP 工具描述。对应 MCP 协议 tools/list 返回的单个 tool 元数据。
 * 缓存至内存，供 Agent 工具路由与 LLM function calling 使用。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class McpToolDescriptor {
    private String name;
    private String description;
    /** JSON Schema 字符串（可能为对象，需 toString 保留） */
    private String inputSchema;
}
