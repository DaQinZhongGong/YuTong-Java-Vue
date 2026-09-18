package com.yutong.ai.tool.mcp;

import java.util.List;

/**
 * MCP 传输客户端抽象。最小可用：list_tools + tool/call。
 * 实现由 McpHttpClient / McpStdioClient 提供。
 */
public interface McpTransportClient {

    /**
     * 列出远端工具列表。调用方需负责重试/超时。
     */
    List<McpToolDescriptor> listTools() throws Exception;

    /**
     * 调用远端工具。
     * @param toolName MCP 工具名
     * @param argsJson JSON 对象字符串，可为空
     */
    McpCallResult callTool(String toolName, String argsJson) throws Exception;

    /** 释放资源 (HttpClient 无需，Stdio 需销毁进程)。 */
    default void close() {}
}
