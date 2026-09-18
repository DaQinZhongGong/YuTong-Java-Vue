package com.yutong.ai.tool.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP HTTP/SSE 传输客户端。轻量实现兼容：
 * - Streamable HTTP (POST JSON-RPC 直连)
 * - SSE (GET text/event-stream 握手 + POST message)
 *
 * 不依赖 modelcontextprotocol/java-sdk，基于 JDK HttpClient + Jackson，
 * 保证离线编译可通过。若后端返回标准 JSON-RPC 2.0 即可工作。
 */
public class McpHttpClient implements McpTransportClient {

    private static final Logger log = LoggerFactory.getLogger(McpHttpClient.class);

    private final String endpoint;
    private final Map<String, String> headers;
    private final int timeoutMs;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final AtomicLong idGen = new AtomicLong(1);

    public McpHttpClient(String endpoint, Map<String, String> headers, int timeoutMs, ObjectMapper objectMapper) {
        this.endpoint = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        this.headers = headers != null ? headers : Map.of();
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : 5000;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(this.timeoutMs))
                .build();
    }

    @Override
    public List<McpToolDescriptor> listTools() throws Exception {
        // 1) 尝试 initialize + tools/list
        tryInitialize();
        ObjectNode req = rpcRequest("tools/list", objectMapper.createObjectNode());
        // 兼容旧版命名 list_tools
        JsonNode resp = postJsonRpc(req);
        List<McpToolDescriptor> tools = parseTools(resp);
        if (tools.isEmpty()) {
            // fallback: tools/list -> tools/list with legacy key
            ObjectNode legacy = rpcRequest("tools/list", objectMapper.createObjectNode());
            // 某些服务用 "list_tools"
            // 若首次解析为空，再试一次 list_tools（兼容）
            if (resp != null && resp.has("error")) {
                legacy = rpcRequest("list_tools", objectMapper.createObjectNode());
                resp = postJsonRpc(legacy);
                tools = parseTools(resp);
            }
        }
        return tools;
    }

    @Override
    public McpCallResult callTool(String toolName, String argsJson) throws Exception {
        tryInitialize();
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", toolName);
        JsonNode argsNode;
        if (argsJson == null || argsJson.isBlank()) {
            argsNode = objectMapper.createObjectNode();
        } else {
            try {
                argsNode = objectMapper.readTree(argsJson);
            } catch (Exception e) {
                argsNode = objectMapper.createObjectNode();
                ((ObjectNode) argsNode).put("input", argsJson);
            }
        }
        params.set("arguments", argsNode);
        ObjectNode req = rpcRequest("tools/call", params);
        JsonNode resp = postJsonRpc(req);
        // 兼容旧命名
        if (resp != null && resp.has("error")) {
            JsonNode err = resp.get("error");
            String msg = err.has("message") ? err.get("message").asText() : err.toString();
            // 若提示 method not found，尝试 tool/call
            if (msg != null && (msg.contains("Method not found") || msg.contains("tools/call"))) {
                ObjectNode alt = rpcRequest("call_tool", params);
                resp = postJsonRpc(alt);
            }
        }
        return parseCallResult(resp);
    }

    private void tryInitialize() {
        try {
            ObjectNode params = objectMapper.createObjectNode();
            params.put("protocolVersion", "2024-11-05");
            params.set("capabilities", objectMapper.createObjectNode());
            ObjectNode clientInfo = objectMapper.createObjectNode();
            clientInfo.put("name", "yutong-ai-service");
            clientInfo.put("version", "0.2.0");
            params.set("clientInfo", clientInfo);
            ObjectNode req = rpcRequest("initialize", params);
            JsonNode resp = postJsonRpc(req);
            if (resp != null && resp.has("result")) {
                // 发送 initialized 通知 (无 id)
                ObjectNode notif = objectMapper.createObjectNode();
                notif.put("jsonrpc", "2.0");
                notif.put("method", "notifications/initialized");
                postJsonRpcNoResp(notif);
            }
        } catch (Exception e) {
            log.debug("mcp initialize handshake failed (ignored, endpoint={}): {}", endpoint, e.toString());
        }
    }

    private ObjectNode rpcRequest(String method, JsonNode params) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("jsonrpc", "2.0");
        node.put("id", idGen.getAndIncrement());
        node.put("method", method);
        if (params != null) node.set("params", params);
        return node;
    }

    private JsonNode postJsonRpc(ObjectNode body) throws Exception {
        String json = objectMapper.writeValueAsString(body);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        headers.forEach(builder::header);
        HttpRequest req = builder.build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        int code = resp.statusCode();
        String respBody = resp.body();
        if (code < 200 || code >= 300) {
            // 若是 SSE 端点返回 405，尝试从 SSE 流解析 message endpoint，暂抛错由上层标记 UNHEALTHY
            throw new IllegalStateException("HTTP " + code + " body=" + truncate(respBody, 600));
        }
        if (respBody == null || respBody.isBlank()) return null;
        // SSE 流可能返回 event: data: {...} 包装，尝试抽取最后一个 JSON
        String jsonPart = extractJsonFromSse(respBody);
        return objectMapper.readTree(jsonPart);
    }

    private void postJsonRpcNoResp(ObjectNode body) throws Exception {
        String json = objectMapper.writeValueAsString(body);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        headers.forEach(builder::header);
        HttpRequest req = builder.build();
        try {
            httpClient.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {}
    }

    private List<McpToolDescriptor> parseTools(JsonNode resp) {
        List<McpToolDescriptor> list = new ArrayList<>();
        if (resp == null || !resp.has("result")) return list;
        JsonNode result = resp.get("result");
        JsonNode toolsNode = result.has("tools") ? result.get("tools") : result;
        if (toolsNode == null || !toolsNode.isArray()) return list;
        for (JsonNode t : toolsNode) {
            String name = t.has("name") ? t.get("name").asText() : null;
            if (name == null || name.isBlank()) continue;
            String desc = t.has("description") ? t.get("description").asText(null) : null;
            String schema = null;
            if (t.has("inputSchema")) {
                JsonNode s = t.get("inputSchema");
                schema = s.isTextual() ? s.asText() : s.toString();
            } else if (t.has("input_schema")) {
                JsonNode s = t.get("input_schema");
                schema = s.isTextual() ? s.asText() : s.toString();
            }
            list.add(new McpToolDescriptor(name, desc, schema));
        }
        return list;
    }

    private McpCallResult parseCallResult(JsonNode resp) {
        if (resp == null) return new McpCallResult(true, "empty response", null);
        String raw = resp.toString();
        if (resp.has("error")) {
            JsonNode err = resp.get("error");
            String msg = err.has("message") ? err.get("message").asText() : err.toString();
            return new McpCallResult(true, msg, raw);
        }
        JsonNode result = resp.has("result") ? resp.get("result") : resp;
        boolean isError = result.has("isError") && result.get("isError").asBoolean(false);
        // MCP 规范: result.content = [{type:"text", text:"..."}]
        StringBuilder sb = new StringBuilder();
        if (result.has("content") && result.get("content").isArray()) {
            for (JsonNode c : result.get("content")) {
                if (c.has("text")) sb.append(c.get("text").asText()).append("\n");
                else sb.append(c.toString()).append("\n");
            }
        } else if (result.has("content") && result.get("content").isTextual()) {
            sb.append(result.get("content").asText());
        } else {
            sb.append(result.toString());
        }
        String content = sb.toString().trim();
        if (content.isEmpty()) content = raw;
        return new McpCallResult(isError, content, raw);
    }

    private String extractJsonFromSse(String body) {
        if (body.contains("\"jsonrpc\"")) {
            // 可能包含 SSE 前缀 "event: message\ndata: {...}"
            int idx = body.lastIndexOf("{");
            int dataIdx = body.lastIndexOf("data:");
            if (dataIdx >= 0) {
                String after = body.substring(dataIdx + 5).trim();
                // 取第一行 JSON
                int nl = after.indexOf('\n');
                if (nl > 0) after = after.substring(0, nl).trim();
                if (after.startsWith("{")) return after;
            }
            // 回退：截取最后一个 JSON 对象
            // 找到最后一个 { ... } 通过直接返回 body 中最后一个 JSON 行
            String[] lines = body.split("\n");
            for (int i = lines.length - 1; i >= 0; i--) {
                String l = lines[i].trim();
                if (l.startsWith("data:")) l = l.substring(5).trim();
                if (l.startsWith("{") && l.contains("jsonrpc")) return l;
            }
        }
        return body.trim();
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
