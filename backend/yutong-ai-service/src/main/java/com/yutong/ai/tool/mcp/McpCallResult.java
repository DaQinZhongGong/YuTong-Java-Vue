package com.yutong.ai.tool.mcp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP tool/call 调用结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class McpCallResult {
    /** 是否错误 */
    private boolean isError;
    /** 文本化内容 (拼接 content[].text) */
    private String content;
    /** 原始 JSON */
    private String rawJson;
}
