package com.yutong.ai.tool.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP Stdio 传输客户端。基于 ProcessBuilder 子进程 + JSON-RPC over stdin/stdout。
 * 每次 listTools/callTool 独立拉起进程（短生命周期），避免长连接状态管理复杂度；
 * 若需复用可在 Registry 层做池化。此实现已足以通过 healthCheck 与 tool/call 审计。
 */
public class McpStdioClient implements McpTransportClient {

    private static final Logger log = LoggerFactory.getLogger(McpStdioClient.class);

    private final String command;
    private final List<String> args;
    private final Map<String, String> env;
    private final String cwd;
    private final int timeoutMs;
    private final ObjectMapper objectMapper;

    public McpStdioClient(String command, List<String> args, Map<String, String> env, String cwd,
                          int timeoutMs, ObjectMapper objectMapper) {
        this.command = command;
        this.args = args != null ? args : List.of();
        this.env = env != null ? env : Map.of();
        this.cwd = cwd;
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : 8000;
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    public static McpStdioClient fromConfigJson(String configJson, ObjectMapper objectMapper, int timeoutMs) {
        try {
            JsonNode root = objectMapper.readTree(configJson);
            String command = root.has("command") ? root.get("command").asText() : null;
            List<String> args = new ArrayList<>();
            if (root.has("args") && root.get("args").isArray()) {
                root.get("args").forEach(n -> args.add(n.asText()));
            } else if (root.has("args") && root.get("args").isTextual()) {
                String s = root.get("args").asText();
                if (!s.isBlank()) args.add(s);
            }
            Map<String, String> env = new java.util.HashMap<>();
            if (root.has("env") && root.get("env").isObject()) {
                root.get("env").fields().forEachRemaining(e -> env.put(e.getKey(), e.getValue().asText()));
            }
            String cwd = root.has("cwd") ? root.get("cwd").asText(null) : null;
            if (command == null || command.isBlank()) {
                throw new IllegalArgumentException("stdio config 缺少 command");
            }
            return new McpStdioClient(command, args, env, cwd, timeoutMs, objectMapper);
        } catch (IOException e) {
            throw new IllegalArgumentException("configJson 解析失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<McpToolDescriptor> listTools() throws Exception {
        return withProcess(proc -> {
            initialize(proc);
            ObjectNode req = rpcRequest(proc.idGen.getAndIncrement(), "tools/list", objectMapper.createObjectNode());
            JsonNode resp = sendAndReceive(proc, req);
            List<McpToolDescriptor> tools = parseTools(resp);
            if (tools.isEmpty() && resp != null && resp.has("error")) {
                // 兼容 list_tools
                ObjectNode legacy = rpcRequest(proc.idGen.getAndIncrement(), "list_tools", objectMapper.createObjectNode());
                resp = sendAndReceive(proc, legacy);
                tools = parseTools(resp);
            }
            return tools;
        });
    }

    @Override
    public McpCallResult callTool(String toolName, String argsJson) throws Exception {
        return withProcess(proc -> {
            initialize(proc);
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
            ObjectNode req = rpcRequest(proc.idGen.getAndIncrement(), "tools/call", params);
            JsonNode resp = sendAndReceive(proc, req);
            if (resp != null && resp.has("error")) {
                JsonNode err = resp.get("error");
                String msg = err.has("message") ? err.get("message").asText() : err.toString();
                if (msg != null && msg.contains("Method not found")) {
                    ObjectNode alt = rpcRequest(proc.idGen.getAndIncrement(), "call_tool", params);
                    resp = sendAndReceive(proc, alt);
                }
            }
            return parseCallResult(resp);
        });
    }

    private void initialize(StdioProc proc) throws Exception {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("protocolVersion", "2024-11-05");
        params.set("capabilities", objectMapper.createObjectNode());
        ObjectNode clientInfo = objectMapper.createObjectNode();
        clientInfo.put("name", "yutong-ai-service");
        clientInfo.put("version", "0.2.0");
        params.set("clientInfo", clientInfo);
        ObjectNode req = rpcRequest(proc.idGen.getAndIncrement(), "initialize", params);
        JsonNode resp = sendAndReceive(proc, req);
        if (resp != null && resp.has("result")) {
            ObjectNode notif = objectMapper.createObjectNode();
            notif.put("jsonrpc", "2.0");
            notif.put("method", "notifications/initialized");
            // notification 无 id，不等待响应
            sendNoResp(proc, notif);
        }
    }

    private ObjectNode rpcRequest(long id, String method, JsonNode params) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("jsonrpc", "2.0");
        node.put("id", id);
        node.put("method", method);
        if (params != null) node.set("params", params);
        return node;
    }

    private JsonNode sendAndReceive(StdioProc proc, ObjectNode req) throws Exception {
        String line = objectMapper.writeValueAsString(req);
        proc.writer.write(line);
        proc.writer.newLine();
        proc.writer.flush();
        // 读取直到匹配 id
        long expectId = req.get("id").asLong();
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            String respLine = proc.queue.poll(200, TimeUnit.MILLISECONDS);
            if (respLine == null) {
                if (!proc.process.isAlive()) {
                    String err = drain(proc.errorReader);
                    throw new IllegalStateException("子进程已退出: " + err);
                }
                continue;
            }
            respLine = respLine.trim();
            if (respLine.isEmpty()) continue;
            JsonNode node;
            try {
                node = objectMapper.readTree(respLine);
            } catch (Exception e) {
                log.debug("stdio non-json line ignored: {}", truncate(respLine, 200));
                continue;
            }
            if (node.has("id") && node.get("id").asLong() == expectId) {
                return node;
            }
            // 忽略通知/其他 id
            log.debug("stdio skip unmatched id line: {}", truncate(respLine, 300));
        }
        throw new TimeoutException("stdio 等待响应超时 id=" + expectId + " req=" + line);
    }

    private void sendNoResp(StdioProc proc, ObjectNode notif) throws IOException {
        String line = objectMapper.writeValueAsString(notif);
        proc.writer.write(line);
        proc.writer.newLine();
        proc.writer.flush();
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

    @FunctionalInterface
    private interface ThrowingFunction<T, R> { R apply(T t) throws Exception; }

    private <T> T withProcess(ThrowingFunction<StdioProc, T> fn) throws Exception {
        StdioProc proc = start();
        try {
            return fn.apply(proc);
        } finally {
            destroy(proc);
        }
    }

    private StdioProc start() throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(command);
        cmd.addAll(args);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        if (cwd != null && !cwd.isBlank()) pb.directory(new File(cwd));
        if (!env.isEmpty()) pb.environment().putAll(env);
        pb.redirectErrorStream(false);
        Process process = pb.start();
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));
        BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        Thread t = new Thread(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    queue.offer(line);
                }
            } catch (IOException ignored) {}
        }, "mcp-stdio-reader-" + command);
        t.setDaemon(true);
        t.start();
        // 等待进程就绪短暂
        Thread.sleep(80);
        if (!process.isAlive()) {
            String err = drain(errorReader);
            throw new IllegalStateException("stdio 进程启动失败: " + String.join(" ", cmd) + " err=" + err);
        }
        StdioProc p = new StdioProc();
        p.process = process;
        p.writer = writer;
        p.reader = reader;
        p.errorReader = errorReader;
        p.queue = queue;
        p.idGen = new AtomicLong(1);
        p.readerThread = t;
        return p;
    }

    private void destroy(StdioProc proc) {
        try { proc.writer.close(); } catch (Exception ignored) {}
        try { proc.reader.close(); } catch (Exception ignored) {}
        try { proc.errorReader.close(); } catch (Exception ignored) {}
        try {
            if (proc.process.isAlive()) {
                proc.process.destroy();
                if (!proc.process.waitFor(800, TimeUnit.MILLISECONDS)) {
                    proc.process.destroyForcibly();
                }
            }
        } catch (Exception ignored) {}
    }

    private String drain(BufferedReader r) {
        try {
            StringBuilder sb = new StringBuilder();
            while (r.ready()) {
                String l = r.readLine();
                if (l == null) break;
                sb.append(l).append("\n");
                if (sb.length() > 1200) break;
            }
            return sb.toString().trim();
        } catch (IOException e) {
            return "";
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    private static class StdioProc {
        Process process;
        BufferedWriter writer;
        BufferedReader reader;
        BufferedReader errorReader;
        BlockingQueue<String> queue;
        AtomicLong idGen;
        Thread readerThread;
    }
}
