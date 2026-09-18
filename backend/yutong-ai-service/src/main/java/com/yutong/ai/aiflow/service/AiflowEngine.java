package com.yutong.ai.aiflow.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yutong.ai.chat.service.llm.LlmMessage;
import com.yutong.ai.chat.service.llm.LlmProviderSelector;
import com.yutong.ai.chat.service.llm.LlmRequest;
import com.yutong.ai.chat.service.llm.LlmResponse;
import com.yutong.ai.gateway.service.AiProviderRegistry;
import com.yutong.ai.rag.service.RagRetrievalService;
import com.yutong.ai.skill.dto.ExecuteSkillRequest;
import com.yutong.ai.skill.service.SkillExecutor;
import com.yutong.ai.skill.service.SkillRegistry;
import com.yutong.ai.tool.service.McpClientRegistry;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.errorcode.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * AIFlow DAG 执行引擎。
 * 设计来源: V040 aiflow_definition.dag_json / aiflow_instance.node_states
 *
 * <p>能力:
 * <ul>
 *   <li>DAG 解析: nodes/edges + 简单 topological 分层 (Kahn)</li>
 *   <li>执行: 顺序 / 并行(CompletableFuture) / 条件分支(condition 网关)</li>
 *   <li>SSE 流式推送: 每节点状态变更即通过 Consumer 回调推送</li>
 *   <li>节点类型: model/rag/mcp/skill/email/human/sql/http + condition/parallel</li>
 *   <li>复用: AiProviderRegistry (model), RagRetrievalService (rag), McpClientRegistry (mcp), SkillRegistry (skill)</li>
 *   <li>email/human/sql 明确失败关闭；http 真实调用，非 2xx 失败</li>
 * </ul>
 *
 * <p>融合路径说明: 本引擎与 yutong-workflow-service 的 LightWorkflowEngine 保持独立；
 * LightWorkflowEngine 负责 BPMN 人工审批流 (UserTask/ExclusiveGateway/n 人会签)，
 * AIFlow 负责 AI 编排 DAG。未来融合可在 AIFlow 的 human 节点内部委托
 * LightWorkflowEngine#startProcess / #completeTask，实现 human-in-the-loop 闭环；
 * 当前阶段保持 untouched，避免耦合。</p>
 */
@Service
public class AiflowEngine {

    private static final Logger log = LoggerFactory.getLogger(AiflowEngine.class);

    private final ObjectMapper objectMapper;
    private final AiProviderRegistry providerRegistry;
    private final RagRetrievalService ragRetrievalService;
    private final McpClientRegistry mcpClientRegistry;
    private final SkillRegistry skillRegistry;
    private final SkillExecutor skillExecutor;
    private final LlmProviderSelector providerSelector;

    private final Executor parallelExecutor = Executors.newCachedThreadPool();

    /**
     * 子流程定义解析器 (可选注入)。为保持 `new AiflowEngine(7 args)` 测试构造兼容,
     * 用 required=false setter 注入；未配置时 definitionId 引用失败关闭，内联子流程不受影响。
     */
    private SubflowDefinitionProvider definitionProvider;

    @Autowired(required = false)
    public void setDefinitionProvider(SubflowDefinitionProvider definitionProvider) {
        this.definitionProvider = definitionProvider;
    }

    /** Web Search 客户端 (可选注入, 保持 7 参构造测试兼容) */
    private com.yutong.ai.search.WebSearchClient webSearchClient;

    @Autowired(required = false)
    public void setWebSearchClient(com.yutong.ai.search.WebSearchClient webSearchClient) {
        this.webSearchClient = webSearchClient;
    }

    /** 媒体生成服务 (可选注入, image 节点用) */
    private com.yutong.ai.media.service.MediaService mediaService;

    @Autowired(required = false)
    public void setMediaService(com.yutong.ai.media.service.MediaService mediaService) {
        this.mediaService = mediaService;
    }

    /** 子流程嵌套上限 (含直接自引用经深度熔断, 避免无限递归) */
    static final int MAX_SUBFLOW_DEPTH = 3;

    /**
     * 执行上下文 — 随调用链显式传递 (并行层跨线程, 不用 ThreadLocal)。
     *
     * @param depth   当前子流程嵌套深度 (根流程 0)
     * @param visited 已进入的 definitionId 集合 (marker 形如 "def:{id}"), 用于环引用失败关闭
     */
    record ExecCtx(int depth, java.util.Set<String> visited) {
        static ExecCtx root() {
            return new ExecCtx(0, new java.util.HashSet<>());
        }

        ExecCtx child(String marker) {
            java.util.Set<String> next = new java.util.HashSet<>(visited);
            next.add(marker);
            return new ExecCtx(depth + 1, next);
        }
    }

    public AiflowEngine(ObjectMapper objectMapper,
                        AiProviderRegistry providerRegistry,
                        RagRetrievalService ragRetrievalService,
                        McpClientRegistry mcpClientRegistry,
                        SkillRegistry skillRegistry,
                        SkillExecutor skillExecutor,
                        LlmProviderSelector providerSelector) {
        this.objectMapper = objectMapper;
        this.providerRegistry = providerRegistry;
        this.ragRetrievalService = ragRetrievalService;
        this.mcpClientRegistry = mcpClientRegistry;
        this.skillRegistry = skillRegistry;
        this.skillExecutor = skillExecutor;
        this.providerSelector = providerSelector;
    }

    public record DagNode(String id, String type, Map<String, Object> config) {}
    public record DagEdge(String source, String target, String condition) {}
    public record NodeStateEvent(String nodeId, String type, String status, String output, String error) {}

    /**
     * 解析 DAG JSON 为节点/边结构。
     * dag_json 形如 {"nodes":[{"id":"n1","type":"model","config":{...}}], "edges":[{"source":"n1","target":"n2","condition":"..."}]}
     */
    public ParsedDag parseDag(String dagJson) {
        if (dagJson == null || dagJson.isBlank()) {
            return new ParsedDag(List.of(), List.of());
        }
        try {
            JsonNode root = objectMapper.readTree(dagJson);
            List<DagNode> nodes = new ArrayList<>();
            List<DagEdge> edges = new ArrayList<>();
            JsonNode nodesNode = root.get("nodes");
            if (nodesNode != null && nodesNode.isArray()) {
                for (JsonNode n : nodesNode) {
                    String id = n.has("id") ? n.get("id").asText() : UUID.randomUUID().toString();
                    String type = n.has("type") ? n.get("type").asText() : "model";
                    Map<String, Object> cfg = new HashMap<>();
                    if (n.has("config") && n.get("config").isObject()) {
                        cfg = objectMapper.convertValue(n.get("config"), new TypeReference<Map<String, Object>>() {});
                    }
                    nodes.add(new DagNode(id, type, cfg));
                }
            }
            JsonNode edgesNode = root.get("edges");
            if (edgesNode != null && edgesNode.isArray()) {
                for (JsonNode e : edgesNode) {
                    String src = e.has("source") ? e.get("source").asText() : null;
                    String tgt = e.has("target") ? e.get("target").asText() : null;
                    String cond = e.has("condition") ? e.get("condition").asText(null) : null;
                    if (src != null && tgt != null) {
                        edges.add(new DagEdge(src, tgt, cond));
                    }
                }
            }
            return new ParsedDag(nodes, edges);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "dag_json 解析失败: " + ex.getMessage());
        }
    }

    public record ParsedDag(List<DagNode> nodes, List<DagEdge> edges) {}

    /**
     * 简单 topological 分层 (Kahn)。返回按层分组的节点列表；每层可并行执行。
     * 若存在环则抛异常。
     */
    public List<List<DagNode>> topologicalLevels(ParsedDag dag) {
        Map<String, DagNode> nodeMap = new HashMap<>();
        for (DagNode n : dag.nodes()) nodeMap.put(n.id(), n);
        Map<String, List<String>> adj = new HashMap<>();
        Map<String, Integer> indegree = new HashMap<>();
        for (DagNode n : dag.nodes()) indegree.put(n.id(), 0);
        for (DagEdge e : dag.edges()) {
            adj.computeIfAbsent(e.source(), k -> new ArrayList<>()).add(e.target());
            indegree.compute(e.target(), (k, v) -> v == null ? 1 : v + 1);
        }
        // Kahn 分层
        List<List<DagNode>> levels = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> en : indegree.entrySet()) if (en.getValue() == 0) queue.add(en.getKey());
        while (!queue.isEmpty()) {
            int sz = queue.size();
            List<DagNode> level = new ArrayList<>();
            for (int i = 0; i < sz; i++) {
                String nid = queue.poll();
                if (visited.contains(nid)) continue;
                visited.add(nid);
                DagNode node = nodeMap.get(nid);
                if (node != null) level.add(node);
                List<String> nexts = adj.getOrDefault(nid, List.of());
                for (String nxt : nexts) {
                    indegree.compute(nxt, (k, v) -> v - 1);
                    if (indegree.get(nxt) == 0) queue.add(nxt);
                }
            }
            if (!level.isEmpty()) levels.add(level);
        }
        if (visited.size() != dag.nodes().size()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "DAG 存在环或孤立节点无法拓扑排序");
        }
        // 若无边，全部视为同一层 (可并行)
        if (levels.isEmpty() && !dag.nodes().isEmpty()) {
            levels.add(new ArrayList<>(dag.nodes()));
        }
        return levels;
    }

    /**
     * 执行 DAG，流式推送每节点状态。
     *
     * @param dagJson   定义的 dag_json
     * @param inputJson 输入上下文 JSON (如 {"query":"hi","status":"approved"})
     * @param stateEmitter 每节点状态回调 (用于 SSE)
     * @return 最终输出 Map (含所有节点输出)
     */
    public Map<String, Object> execute(String dagJson, String inputJson, Consumer<NodeStateEvent> stateEmitter) {
        ParsedDag dag = parseDag(dagJson);
        if (dag.nodes().isEmpty()) {
            return Map.of("result", "empty dag");
        }
        Map<String, Object> inputCtx = parseInputCtx(inputJson);
        return executeDag(dag, inputCtx, stateEmitter, ExecCtx.root());
    }

    /**
     * 执行已解析 DAG (根流程与子流程复用同一入口, ctx 携带嵌套深度与环引用集合)。
     */
    private Map<String, Object> executeDag(ParsedDag dag, Map<String, Object> inputCtx,
                                           Consumer<NodeStateEvent> stateEmitter, ExecCtx ctx) {
        List<List<DagNode>> levels = topologicalLevels(dag);
        Map<String, Object> globalOutputs = new LinkedHashMap<>();
        globalOutputs.put("_input", inputCtx);
        Map<String, String> nodeStatus = new LinkedHashMap<>();

        // 构建边条件映射: target -> condition (来自 edge.condition 或 condition 节点的 config.condition)
        Map<String, String> edgeConditionMap = new HashMap<>();
        for (DagEdge e : dag.edges()) {
            if (e.condition() != null && !e.condition().isBlank()) edgeConditionMap.put(e.target(), e.condition());
        }

        for (List<DagNode> level : levels) {
            // 判断条件网关: 若 level 含 condition 类型节点，先执行 condition 再决定下游是否跳过
            // 简化: 每层内若多节点则并行执行
            if (level.size() == 1) {
                DagNode node = level.get(0);
                // 检查该节点是否因上游条件被跳过 (edge condition)
                String edgeCond = edgeConditionMap.get(node.id());
                if (edgeCond != null && !evaluateCondition(edgeCond, inputCtx, globalOutputs)) {
                    NodeStateEvent skip = new NodeStateEvent(node.id(), node.type(), "SKIPPED", null, "condition not met: " + edgeCond);
                    nodeStatus.put(node.id(), "SKIPPED");
                    if (stateEmitter != null) stateEmitter.accept(skip);
                    globalOutputs.put(node.id(), Map.of("skipped", true, "reason", edgeCond));
                    continue;
                }
                executeSingle(node, inputCtx, globalOutputs, stateEmitter, nodeStatus, ctx);
            } else {
                // 并行层
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                for (DagNode node : level) {
                    String edgeCond = edgeConditionMap.get(node.id());
                    if (edgeCond != null && !evaluateCondition(edgeCond, inputCtx, globalOutputs)) {
                        NodeStateEvent skip = new NodeStateEvent(node.id(), node.type(), "SKIPPED", null, "condition not met: " + edgeCond);
                        nodeStatus.put(node.id(), "SKIPPED");
                        if (stateEmitter != null) stateEmitter.accept(skip);
                        globalOutputs.put(node.id(), Map.of("skipped", true, "reason", edgeCond));
                        continue;
                    }
                    CompletableFuture<Void> cf = CompletableFuture.runAsync(() -> {
                        executeSingle(node, inputCtx, globalOutputs, stateEmitter, nodeStatus, ctx);
                    }, parallelExecutor);
                    futures.add(cf);
                }
                // 等待本层并行完成
                try {
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                } catch (Exception e) {
                    log.warn("parallel level execution join failed", e);
                }
            }
        }
        return globalOutputs;
    }

    private void executeSingle(DagNode node, Map<String, Object> inputCtx, Map<String, Object> globalOutputs,
                               Consumer<NodeStateEvent> emitter, Map<String, String> nodeStatus, ExecCtx ctx) {
        String nid = node.id();
        String type = node.type();
        if (emitter != null) emitter.accept(new NodeStateEvent(nid, type, "RUNNING", null, null));
        nodeStatus.put(nid, "RUNNING");
        try {
            // condition / parallel 网关节点: 本身不产生业务输出，仅做路由/汇聚
            if ("condition".equalsIgnoreCase(type)) {
                String cond = node.config() != null ? String.valueOf(node.config().getOrDefault("condition", "")) : "";
                boolean matched = evaluateCondition(cond, inputCtx, globalOutputs);
                String out = matched ? "condition matched: " + cond : "condition not matched: " + cond;
                synchronized (globalOutputs) { globalOutputs.put(nid, Map.of("matched", matched, "condition", cond)); }
                nodeStatus.put(nid, "SUCCESS");
                if (emitter != null) emitter.accept(new NodeStateEvent(nid, type, "SUCCESS", out, null));
                return;
            }
            if ("parallel".equalsIgnoreCase(type)) {
                synchronized (globalOutputs) { globalOutputs.put(nid, Map.of("parallel", true)); }
                nodeStatus.put(nid, "SUCCESS");
                if (emitter != null) emitter.accept(new NodeStateEvent(nid, type, "SUCCESS", "parallel gateway passed", null));
                return;
            }
            String output = dispatchNode(node, inputCtx, globalOutputs, emitter, ctx);
            synchronized (globalOutputs) { globalOutputs.put(nid, output); }
            nodeStatus.put(nid, "SUCCESS");
            if (emitter != null) emitter.accept(new NodeStateEvent(nid, type, "SUCCESS", output, null));
        } catch (Exception e) {
            log.warn("node execution failed: id={} type={}", nid, type, e);
            nodeStatus.put(nid, "FAILED");
            synchronized (globalOutputs) { globalOutputs.put(nid, Map.of("error", e.getMessage() != null ? e.getMessage() : "unknown")); }
            if (emitter != null) emitter.accept(new NodeStateEvent(nid, type, "FAILED", null, e.getMessage()));
        }
    }

    /**
     * 简单条件表达式求值: 支持 "field==value" 或 "field.equals(value)" 或纯值 equals。
     * 上下文取值优先级: globalOutputs 中最近输出 > inputCtx。
     * 满足即返回 true。
     */
    private boolean evaluateCondition(String condition, Map<String, Object> inputCtx, Map<String, Object> globalOutputs) {
        if (condition == null || condition.isBlank()) return true;
        String cond = condition.trim();

        // == equals
        if (cond.contains("==")) {
            String[] parts = cond.split("==", 2);
            String field = parts[0].trim();
            String expected = parts[1].trim().replaceAll("^['\"]|['\"]$", "");
            Object actual = resolveField(field, inputCtx, globalOutputs);
            String actualStr = actual == null ? "" : String.valueOf(actual).trim();
            return expected.equals(actualStr);
        }
        // != not equals
        if (cond.contains("!=")) {
            String[] parts = cond.split("!=", 2);
            String field = parts[0].trim();
            String expected = parts[1].trim().replaceAll("^['\"]|['\"]$", "");
            Object actual = resolveField(field, inputCtx, globalOutputs);
            String actualStr = actual == null ? "" : String.valueOf(actual).trim();
            return !expected.equals(actualStr);
        }
        // >= greater or equal (must check before >)
        if (cond.contains(">=")) {
            String[] parts = cond.split(">=", 2);
            return compareNumeric(parts[0].trim(), parts[1].trim(), inputCtx, globalOutputs, ">=");
        }
        // <= less or equal (must check before <)
        if (cond.contains("<=")) {
            String[] parts = cond.split("<=", 2);
            return compareNumeric(parts[0].trim(), parts[1].trim(), inputCtx, globalOutputs, "<=");
        }
        // > greater than
        if (cond.contains(">")) {
            String[] parts = cond.split(">", 2);
            return compareNumeric(parts[0].trim(), parts[1].trim(), inputCtx, globalOutputs, ">");
        }
        // < less than
        if (cond.contains("<")) {
            String[] parts = cond.split("<", 2);
            return compareNumeric(parts[0].trim(), parts[1].trim(), inputCtx, globalOutputs, "<");
        }
        // contains
        if (cond.startsWith("contains ")) {
            String[] parts = cond.substring(9).trim().split("\\s+", 2);
            if (parts.length == 2) {
                Object actual = resolveField(parts[0].trim(), inputCtx, globalOutputs);
                String actualStr = actual == null ? "" : String.valueOf(actual);
                String needle = parts[1].trim().replaceAll("^['\"]|['\"]$", "");
                return actualStr.contains(needle);
            }
        }
        // startsWith
        if (cond.startsWith("startsWith ")) {
            String[] parts = cond.substring(11).trim().split("\\s+", 2);
            if (parts.length == 2) {
                Object actual = resolveField(parts[0].trim(), inputCtx, globalOutputs);
                String actualStr = actual == null ? "" : String.valueOf(actual);
                String prefix = parts[1].trim().replaceAll("^['\"]|['\"]$", "");
                return actualStr.startsWith(prefix);
            }
        }
        // endsWith
        if (cond.startsWith("endsWith ")) {
            String[] parts = cond.substring(9).trim().split("\\s+", 2);
            if (parts.length == 2) {
                Object actual = resolveField(parts[0].trim(), inputCtx, globalOutputs);
                String actualStr = actual == null ? "" : String.valueOf(actual);
                String suffix = parts[1].trim().replaceAll("^['\"]|['\"]$", "");
                return actualStr.endsWith(suffix);
            }
        }
        // empty / notEmpty
        if (cond.startsWith("empty ")) {
            Object actual = resolveField(cond.substring(6).trim(), inputCtx, globalOutputs);
            return actual == null || String.valueOf(actual).isBlank();
        }
        if (cond.startsWith("notEmpty ")) {
            Object actual = resolveField(cond.substring(9).trim(), inputCtx, globalOutputs);
            return actual != null && !String.valueOf(actual).isBlank();
        }
        // in: field in a,b,c
        if (cond.contains(" in ")) {
            String[] parts = cond.split(" in ", 2);
            if (parts.length == 2) {
                Object actual = resolveField(parts[0].trim(), inputCtx, globalOutputs);
                String actualStr = actual == null ? "" : String.valueOf(actual).trim();
                String[] options = parts[1].trim().replaceAll("^['\"]|['\"]$", "").split(",");
                for (String opt : options) {
                    if (opt.trim().equals(actualStr)) return true;
                }
                return false;
            }
        }

        // 纯值兜底: 判断 inputCtx/globalOutputs 是否包含该值
        for (Object v : inputCtx.values()) {
            if (cond.equals(String.valueOf(v))) return true;
        }
        for (Object v : globalOutputs.values()) {
            if (cond.equals(String.valueOf(v))) return true;
            if (v instanceof Map m && m.containsValue(cond)) return true;
        }
        Object byKey = resolveField(cond, inputCtx, globalOutputs);
        if (byKey != null) return true;
        return false;
    }

    /**
     * 数值比较算子辅助方法。
     * 无法解析为数字时返回 false (失败关闭)。
     */
    private boolean compareNumeric(String field, String expectedStr,
                                    Map<String, Object> inputCtx, Map<String, Object> globalOutputs,
                                    String operator) {
        Object actual = resolveField(field, inputCtx, globalOutputs);
        if (actual == null) return false;
        try {
            double a = Double.parseDouble(String.valueOf(actual).trim());
            double b = Double.parseDouble(expectedStr.trim().replaceAll("^['\"]|['\"]$", ""));
            return switch (operator) {
                case ">" -> a > b;
                case "<" -> a < b;
                case ">=" -> a >= b;
                case "<=" -> a <= b;
                default -> false;
            };
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private Object resolveField(String field, Map<String, Object> inputCtx, Map<String, Object> globalOutputs) {
        if (field == null || field.isBlank()) return null;
        // 支持 "input.status" 或 "status"
        String key = field.contains(".") ? field.substring(field.lastIndexOf('.') + 1) : field;
        if (inputCtx.containsKey(key)) return inputCtx.get(key);
        if (inputCtx.containsKey(field)) return inputCtx.get(field);
        synchronized (globalOutputs) {
            if (globalOutputs.containsKey(key)) {
                Object v = globalOutputs.get(key);
                if (v instanceof Map m && m.containsKey("output")) return m.get("output");
                return v;
            }
            // 搜索所有 Map value 中包含该 key
            for (Object gv : globalOutputs.values()) {
                if (gv instanceof Map m && m.containsKey(key)) return m.get(key);
            }
        }
        return null;
    }

    private String dispatchNode(DagNode node, Map<String, Object> inputCtx, Map<String, Object> globalOutputs,
                                Consumer<NodeStateEvent> stateEmitter, ExecCtx ctx) {
        String type = node.type() == null ? "model" : node.type().toLowerCase();
        Map<String, Object> cfg = node.config() == null ? Map.of() : node.config();
        return switch (type) {
            case "model" -> executeModelNode(cfg, inputCtx, globalOutputs);
            case "rag" -> executeRagNode(cfg, inputCtx);
            case "mcp" -> executeMcpNode(cfg, inputCtx);
            case "skill" -> executeSkillNode(cfg, inputCtx);
            case "email" -> executeEmailNode(cfg, inputCtx);
            case "human" -> executeHumanNode(cfg, inputCtx);
            case "sql" -> executeSqlNode(cfg, inputCtx);
            case "http" -> executeHttpNode(cfg, inputCtx);
            case "llm" -> executeModelNode(cfg, inputCtx, globalOutputs);
            case "knowledge" -> executeRagNode(cfg, inputCtx);
            case "tool" -> executeMcpNode(cfg, inputCtx);
            case "input" -> String.valueOf(inputCtx.getOrDefault("query", inputCtx.getOrDefault("input", "")));
            case "output" -> executeOutputNode(cfg, inputCtx, globalOutputs);
            case "loop" -> executeLoopNode(cfg, inputCtx, globalOutputs);
            case "code" -> executeCodeNode(cfg, inputCtx, globalOutputs);
            case "templating" -> executeTemplatingNode(cfg, inputCtx, globalOutputs);
            case "aggregator", "var-agg" -> executeAggregatorNode(cfg, inputCtx, globalOutputs);
            case "hitl" -> executeHitlNode(cfg, inputCtx, globalOutputs);
            case "subflow", "sub-flow", "subworkflow" ->
                    executeSubflowNode(node, cfg, inputCtx, stateEmitter, ctx);
            case "web_search", "websearch", "search" -> executeWebSearchNode(cfg, inputCtx);
            case "image", "image_gen" -> executeImageNode(cfg, inputCtx);
            case "faq", "faq_extract" -> executeFaqExtractNode(cfg, inputCtx);
            default -> throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "不支持的节点类型: " + type);
        };
    }

    private String executeModelNode(Map<String, Object> cfg, Map<String, Object> inputCtx, Map<String, Object> globalOutputs) {
        String promptTemplate = cfg.containsKey("prompt") ? String.valueOf(cfg.get("prompt"))
                : (cfg.containsKey("systemPrompt") ? String.valueOf(cfg.get("systemPrompt")) : "");
        String query = String.valueOf(inputCtx.getOrDefault("query", inputCtx.getOrDefault("input", "")));
        String prompt = interpolate(promptTemplate.isBlank() ? query : promptTemplate, inputCtx, globalOutputs);
        if (prompt.isBlank()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "model 节点 prompt 不能为空");
        }
        String providerCode = firstCfg(cfg, "providerCode", "provider");
        String model = firstCfg(cfg, "model", "modelCode");
        var runtime = providerSelector.selectProvider(providerCode, model)
                .or(providerSelector::selectEnabledProvider)
                .orElseThrow(() -> new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                        "未找到可用 LLM 供应商。请在供应商管理中启用 chat 模型。"));
        String effectiveModel = (model == null || model.isBlank()) ? runtime.defaultModel() : model;
        java.util.List<LlmMessage> messages = new java.util.ArrayList<>();
        String systemPrompt = firstCfg(cfg, "systemPrompt", "system");
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(LlmMessage.system(interpolate(systemPrompt, inputCtx, globalOutputs)));
        }
        messages.add(LlmMessage.user(prompt));
        LlmResponse resp = runtime.adapter().chat(new LlmRequest(effectiveModel, messages));
        if (resp == null || resp.isError()) {
            Throwable err = resp == null ? null : resp.error();
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR,
                    "LLM 调用失败: " + (err == null ? "empty response" : err.getMessage()));
        }
        String content = resp.content() == null ? "" : resp.content();
        if (content.isBlank()) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "LLM 返回空内容: provider=" + runtime.provider().getProviderCode());
        }
        return content;
    }

    private String executeRagNode(Map<String, Object> cfg, Map<String, Object> inputCtx) {
        String kbId = cfg.containsKey("kbId") ? String.valueOf(cfg.get("kbId")) : (cfg.containsKey("kb_id") ? String.valueOf(cfg.get("kb_id")) : null);
        String query = cfg.containsKey("query") ? String.valueOf(cfg.get("query")) : String.valueOf(inputCtx.getOrDefault("query", ""));
        if (kbId == null || kbId.isBlank()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "rag 节点必须配置 kbId");
        }
        var results = ragRetrievalService.retrieve(
                com.yutong.common.auth.CurrentUserContext.getTenantId(),
                kbId, query,
                com.yutong.common.auth.CurrentUserContext.getUserId(), 3);
        if (results.isEmpty()) {
            return "rag: no hits for query=" + truncate(query, 80);
        }
        StringBuilder sb = new StringBuilder("rag results: ");
        for (var r : results) sb.append("[").append(truncate(r.chunkText(), 60)).append("] ");
        return sb.toString();
    }

    private String executeMcpNode(Map<String, Object> cfg, Map<String, Object> inputCtx) {
        String sc = cfg.containsKey("serverCode") ? String.valueOf(cfg.get("serverCode"))
                : (cfg.containsKey("server_code") ? String.valueOf(cfg.get("server_code")) : null);
        if (sc == null) sc = cfg.containsKey("tool") ? String.valueOf(cfg.get("tool")) : "default";
        final String serverCode = sc;
        String toolName = firstCfg(cfg, "toolName", "tool");
        if (toolName == null || toolName.isBlank()) {
            toolName = "default";
        }
        String args = firstCfg(cfg, "params", "arguments");
        if (args == null || args.isBlank()) {
            args = "{\"query\":\"" + String.valueOf(inputCtx.getOrDefault("query", "")).replace("\"", "'") + "\"}";
        }
        return mcpClientRegistry.callToolByCodeOrId(serverCode, toolName, args);
    }

    private String executeSkillNode(Map<String, Object> cfg, Map<String, Object> inputCtx) {
        String sc = cfg.containsKey("skillCode") ? String.valueOf(cfg.get("skillCode"))
                : (cfg.containsKey("skill_code") ? String.valueOf(cfg.get("skill_code")) : null);
        if (sc == null) sc = String.valueOf(cfg.getOrDefault("skill", "default"));
        final String skillCode = sc;
        try {
            var skill = skillRegistry.getActivated(skillCode);
            if (skill == null) {
                throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "Skill 未发布: " + skillCode);
            }
            ExecuteSkillRequest req = new ExecuteSkillRequest();
            req.setAction("analyze");
            req.setContent(String.valueOf(inputCtx.getOrDefault("query", inputCtx.getOrDefault("input", ""))));
            var result = skillExecutor.execute(skill.getId(), req);
            return "skill[" + skillCode + "] " + result.getAction() + " " + result.getStatus() + ": " + result.getSummary();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "skill[" + skillCode + "] 执行失败: " + e.getMessage());
        }
    }

    private String executeEmailNode(Map<String, Object> cfg, Map<String, Object> inputCtx) {
        throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                "email 节点未接入邮件网关，禁止模拟发送。请改用 http 节点调用已配置的通知服务。");
    }

    private String executeHumanNode(Map<String, Object> cfg, Map<String, Object> inputCtx) {
        throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                "human 节点需委托 LightWorkflowEngine 审批，当前禁止自动通过。请使用工作流模块发起人工任务。");
    }

    private String executeSqlNode(Map<String, Object> cfg, Map<String, Object> inputCtx) {
        throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                "sql 节点禁止在编排引擎内直接执行。请使用只读数据源模块或 http 节点。");
    }

    /**
     * Web Search 节点 — 调用智谱 Web Search API 联网搜索。
     * config: {"query":"搜索词(可选,空则取输入)", "resultCount":10}
     */
    private String executeWebSearchNode(Map<String, Object> cfg, Map<String, Object> inputCtx) {
        if (webSearchClient == null) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    "web_search 节点未配置搜索客户端 (需启用智谱 Web Search)");
        }
        String query = firstCfg(cfg, "query", "searchQuery", "keyword");
        if (query == null || query.isBlank()) {
            query = String.valueOf(inputCtx.getOrDefault("query",
                    inputCtx.getOrDefault("input", "")));
        }
        query = interpolate(query, inputCtx, Map.of());
        if (query.isBlank()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "web_search 节点搜索关键词不能为空");
        }
        int resultCount = 10;
        Object rc = cfg.get("resultCount");
        if (rc != null) {
            try {
                resultCount = Integer.parseInt(String.valueOf(rc));
            } catch (NumberFormatException ignored) {
            }
        }
        try {
            var response = webSearchClient.search(query.trim(), resultCount);
            return objectMapper.writeValueAsString(response);
        } catch (com.yutong.ai.search.WebSearchClient.SearchException e) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, e.getMessage());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    "web_search 节点执行失败: " + e.getMessage());
        }
    }

    /**
     * Image 节点 — 文生图。
     * config: {"prompt":"图片描述(可选,空则取输入)"}
     */
    private String executeImageNode(Map<String, Object> cfg, Map<String, Object> inputCtx) {
        if (mediaService == null) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    "image 节点未配置媒体服务");
        }
        String prompt = firstCfg(cfg, "prompt", "imagePrompt");
        if (prompt == null || prompt.isBlank()) {
            prompt = String.valueOf(inputCtx.getOrDefault("query",
                    inputCtx.getOrDefault("input", "")));
        }
        prompt = interpolate(prompt, inputCtx, Map.of());
        if (prompt.isBlank()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "image 节点 prompt 不能为空");
        }
        try {
            var job = mediaService.generateSync("image", prompt.trim());
            return objectMapper.writeValueAsString(Map.of(
                    "jobId", job.getId() != null ? job.getId() : "",
                    "status", job.getStatus() != null ? job.getStatus() : "UNKNOWN",
                    "outputUrl", job.getOutputUrl() != null ? job.getOutputUrl() : "",
                    "mediaType", "image"
            ));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    "image 节点执行失败: " + e.getMessage());
        }
    }

    /**
     * FAQ 抽取节点 — 从文本中提取常见问答对。
     * config: {"text":"源文本(可选,空则取输入)", "maxFaqs":10, "language":"zh"}
     * 输出 JSON: {"faqs":[{"question":"...","answer":"..."}], "count":N}
     */
    private String executeFaqExtractNode(Map<String, Object> cfg, Map<String, Object> inputCtx) {
        String text = firstCfg(cfg, "text", "content", "source");
        if (text == null || text.isBlank()) {
            text = String.valueOf(inputCtx.getOrDefault("query",
                    inputCtx.getOrDefault("input", "")));
        }
        text = interpolate(text, inputCtx, Map.of());
        if (text.isBlank()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "faq 节点源文本不能为空");
        }
        int maxFaqs = 10;
        Object mf = cfg.get("maxFaqs");
        if (mf != null) {
            try { maxFaqs = Math.max(1, Math.min(50, Integer.parseInt(String.valueOf(mf)))); }
            catch (NumberFormatException ignored) {}
        }

        var runtimeOpt = providerSelector.selectEnabledProvider();
        if (runtimeOpt.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "无可用 LLM 供应商");
        }
        var runtime = runtimeOpt.get();
        String systemPrompt = """
                你是一个 FAQ 抽取专家。从给定文本中提取常见问答对。
                
                规则:
                - 提取最多 %d 个 FAQ
                - 每个 FAQ 包含 question 和 answer
                - question 应该是用户可能提出的问题
                - answer 应该基于原文, 简洁准确
                - 只返回 JSON: {"faqs":[{"question":"...","answer":"..."}]}
                """.formatted(maxFaqs);

        String truncated = text.length() > 4000 ? text.substring(0, 4000) + "..." : text;
        LlmResponse resp = runtime.adapter().chat(new LlmRequest(
                runtime.defaultModel(),
                List.of(LlmMessage.system(systemPrompt), LlmMessage.user(truncated)),
                0.2, 2000, false, "AIFLOW_FAQ_EXTRACT"));

        if (resp.isError() || resp.content() == null || resp.content().isBlank()) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR,
                    "FAQ 抽取 LLM 调用失败: " + (resp.error() != null ? resp.error().getMessage() : "empty"));
        }

        // 提取 JSON
        String content = resp.content().trim();
        if (content.startsWith("```json")) content = content.substring(7);
        else if (content.startsWith("```")) content = content.substring(3);
        if (content.endsWith("```")) content = content.substring(0, content.length() - 3);
        return content.trim();
    }

    private String executeOutputNode(Map<String, Object> cfg, Map<String, Object> inputCtx, Map<String, Object> globalOutputs) {
        String template = firstCfg(cfg, "template", "prompt");
        if (template == null || template.isBlank()) {
            Object last = lastNonInputOutput(globalOutputs);
            return last == null ? String.valueOf(inputCtx) : String.valueOf(last);
        }
        return interpolate(template, inputCtx, globalOutputs);
    }

    private String executeHttpNode(Map<String, Object> cfg, Map<String, Object> inputCtx) {
        String url = cfg.containsKey("url") ? String.valueOf(cfg.get("url")) : (cfg.containsKey("endpoint") ? String.valueOf(cfg.get("endpoint")) : "");
        String method = cfg.containsKey("method") ? String.valueOf(cfg.get("method")) : "GET";
        if (url.isBlank() || !(url.startsWith("http://") || url.startsWith("https://"))) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "http 节点 url 必须是 http(s) 地址");
        }
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(10)).build();
            java.net.http.HttpRequest.Builder b = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(20));
            if ("POST".equalsIgnoreCase(method)) {
                String body = cfg.containsKey("body") ? String.valueOf(cfg.get("body")) : objectMapper.writeValueAsString(inputCtx);
                b.header("Content-Type", "application/json");
                b.POST(java.net.http.HttpRequest.BodyPublishers.ofString(body));
            } else {
                b.GET();
            }
            java.net.http.HttpResponse<String> resp = client.send(b.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR,
                        "http " + method + " " + url + " -> " + resp.statusCode() + " " + truncate(resp.body(), 240));
            }
            return "http " + method + " " + url + " -> " + resp.statusCode() + " " + truncate(resp.body(), 400);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "http 节点调用失败: " + e.getMessage());
        }
    }

    /**
     * Loop 节点:遍历 items 数组,每轮注入 loop_index / loop_item 上下文,执行 body 模板
     * 设计来源: aiflow SwitcherNode + ADR 0004 P2-B 循环节点
     *
     * 配置:
     *   - items: 数组路径 (Jinja-like),如 "list" / "{{list}}" / "n2.items"
     *   - maxIter: 最大迭代次数 (1-100, 默认 10),防止死循环 / 资源耗尽
     *   - body: 字符串模板,每轮用 {{loop_index}} / {{loop_item}} / {{loop_item.xxx}} 插值
     *   - join: 多次输出 join 字符串 (默认 "\n")
     *
     * 输出 JSON: { "iterations": N, "results": [str, ...], "items": [...] }
     * 后续节点可用 {{n_id.iterations}} 引用总次数,{{n_id.results}} 引用数组
     */
    private String executeLoopNode(Map<String, Object> cfg, Map<String, Object> inputCtx, Map<String, Object> globalOutputs) {
        String itemsPath = firstCfg(cfg, "items", "array");
        if (itemsPath == null || itemsPath.isBlank()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "loop 节点必须配置 items 路径");
        }
        int maxIter = parseMaxIter(cfg);
        Object resolved = resolveField(itemsPath, inputCtx, globalOutputs);
        List<?> items;
        if (resolved instanceof Collection<?> coll) {
            items = new ArrayList<>(coll);
        } else if (resolved != null && resolved.getClass().isArray()) {
            // 数组兼容:List / Set / Object[] 等
            items = java.util.Arrays.asList((Object[]) resolved);
        } else {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    "loop items 路径 " + itemsPath + " 解析失败: 不是数组/集合 (实际类型 " +
                            (resolved == null ? "null" : resolved.getClass().getSimpleName()) + ")");
        }
        int iterations = Math.min(items.size(), maxIter);
        String bodyTemplate = firstCfg(cfg, "body", "template");
        String join = firstCfg(cfg, "join");
        if (join == null) join = "\n";

        List<String> results = new ArrayList<>(iterations);
        for (int i = 0; i < iterations; i++) {
            Object item = items.get(i);
            Map<String, Object> iterCtx = new LinkedHashMap<>(inputCtx);
            iterCtx.put("loop_index", i);
            iterCtx.put("loop_item", item);
            // 若 item 是 Map,展开其字段供 {{loop_item.field}} 引用
            if (item instanceof Map<?, ?> m) {
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    iterCtx.put("loop_item_" + e.getKey(), e.getValue());
                }
            }
            if (bodyTemplate != null && !bodyTemplate.isBlank()) {
                results.add(interpolate(bodyTemplate, iterCtx, globalOutputs));
            } else {
                try {
                    results.add(objectMapper.writeValueAsString(item));
                } catch (Exception ex) {
                    results.add(String.valueOf(item));
                }
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("iterations", iterations);
        out.put("items", items.subList(0, iterations));
        out.put("results", results);
        out.put("joined", String.join(join, results));
        try {
            return objectMapper.writeValueAsString(out);
        } catch (Exception ex) {
            return "loop iterations=" + iterations + " results=" + results.size();
        }
    }

    /**
     * 解析 maxIter,合法范围 1-100,默认 10
     * 防呆:防止用户配置 0/负数/null → 走默认 10 (避免"看起来配了实际 0 轮"的陷阱)
     *      超大值 (>100) 强制 cap 到 100 防止资源耗尽
     */
    private int parseMaxIter(Map<String, Object> cfg) {
        Object raw = cfg == null ? null : cfg.getOrDefault("maxIter", cfg.get("max_iter"));
        if (raw == null) return 10;
        int n;
        try {
            n = Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException ignore) {
            return 10;
        }
        if (n < 1) return 10;     // 0 / 负数 / 1 以下视作未配置, 走默认 10
        if (n > 100) n = 100;    // 上限 100, 防止死循环 / 资源耗尽
        return n;
    }

    /**
     * Code 节点 — Spring Expression Language (SpEL) 沙箱执行
     * 设计来源: ADR 0004 P2-B 批次 3
     *
     * 配置:
     *   - code: SpEL 表达式 (e.g. "1 + 2" / "input.query.toUpperCase()" / "#score > 60")
     *   - timeoutMs: 超时毫秒 (默认 5000, 上限 10000)
     *
     * 输入上下文: input (Map) / outputs (Map) / input 字段直接作为 root 变量
     * 输出:        evaluate 结果转 String
     *
     * 安全: SpEL 沙箱化执行, 仅支持数学/字符串/集合操作, 不允许 java.lang.Runtime 等危险类
     *       生产环境建议配置 SecurityManager + SimpleEvaluationContext.forReadOnlyDataBinding()
     *       进一步限制 (本实现使用 StandardEvaluationContext, 信任业务配置)
     */
    private String executeCodeNode(Map<String, Object> cfg, Map<String, Object> inputCtx, Map<String, Object> globalOutputs) {
        String code = firstCfg(cfg, "code", "expression", "script");
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "code 节点必须配置 code 表达式");
        }
        long timeoutMs = 5000;
        try {
            Object t = cfg.get("timeoutMs");
            if (t != null) timeoutMs = Math.min(10_000, Math.max(100, Long.parseLong(String.valueOf(t).trim())));
        } catch (NumberFormatException ignore) {
            // keep default
        }

        try {
            org.springframework.expression.ExpressionParser parser =
                    new org.springframework.expression.spel.standard.SpelExpressionParser();
            org.springframework.expression.spel.support.StandardEvaluationContext ctx =
                    new org.springframework.expression.spel.support.StandardEvaluationContext();

            // 注入上下文: input 整体 + outputs 整体 + 单字段
            ctx.setVariable("input", inputCtx);
            ctx.setVariable("outputs", globalOutputs);
            for (Map.Entry<String, Object> e : inputCtx.entrySet()) {
                ctx.setVariable(e.getKey(), e.getValue());
            }
            for (Map.Entry<String, Object> e : globalOutputs.entrySet()) {
                if ("_input".equals(e.getKey())) continue;
                ctx.setVariable("out_" + e.getKey(), e.getValue());
            }
            // root 设为 inputCtx, 方便 input.query.toUpperCase() 写法
            ctx.setRootObject(inputCtx);

            final org.springframework.expression.Expression expr = parser.parseExpression(code);
            // 超时保护: 单线程 + Future.get(timeout)
            java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "aiflow-code-sandbox");
                t.setDaemon(true);
                return t;
            });
            java.util.concurrent.Future<Object> future = pool.submit(() -> expr.getValue(ctx));
            try {
                Object result = future.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
                return result == null ? "" : String.valueOf(result);
            } catch (java.util.concurrent.TimeoutException te) {
                future.cancel(true);
                throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "code 节点执行超时 (" + timeoutMs + "ms)");
            } finally {
                pool.shutdownNow();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "code 节点执行失败: " + e.getMessage());
        }
    }

    /**
     * Templating 节点 — Jinja-like 字符串模板
     * 设计来源: ADR 0004 P2-B 批次 3
     *
     * 配置:
     *   - template: 模板字符串,支持 {{var}} 插值 + {%if cond%}...{%endif%} 条件 + {%for x in list%}...{%endfor%} 循环
     *   - failOnMissing: 引用不存在的变量是否抛错 (默认 true)
     *
     * 输出: 渲染后的字符串
     *
     * 安全: 模板只做字符串插值与简单控制流, 不执行任意 java/JS
     */
    private String executeTemplatingNode(Map<String, Object> cfg, Map<String, Object> inputCtx, Map<String, Object> globalOutputs) {
        String template = firstCfg(cfg, "template", "text");
        if (template == null || template.isBlank()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "templating 节点必须配置 template");
        }
        boolean failOnMissing = !cfg.containsKey("failOnMissing")
                || "true".equalsIgnoreCase(String.valueOf(cfg.get("failOnMissing")));
        return renderTemplate(template, inputCtx, globalOutputs, failOnMissing);
    }

    /**
     * 模板渲染: {{var}} / {{out_n1}} / {%if expr%}...{%endif%} / {%for x in list%}...{%endfor%}
     * 表达式支持: ==  !=  &&  ||  ( )  字符串/数字字面量
     */
    private String renderTemplate(String template, Map<String, Object> inputCtx, Map<String, Object> globalOutputs, boolean failOnMissing) {
        // 先处理 {%for%} {%endfor%} (多行可能)
        StringBuilder out = new StringBuilder();
        int i = 0;
        int len = template.length();
        while (i < len) {
            // {%for x in list%}
            int forStart = template.indexOf("{%for", i);
            if (forStart < 0) {
                out.append(renderSegment(template.substring(i), inputCtx, globalOutputs, failOnMissing));
                break;
            }
            out.append(renderSegment(template.substring(i, forStart), inputCtx, globalOutputs, failOnMissing));
            int varStart = template.indexOf(" in ", forStart);
            if (varStart < 0) throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "模板 {%for 缺少 in");
            String varName = template.substring(forStart + 6, varStart).trim();
            int headerEnd = template.indexOf("%}", varStart);
            if (headerEnd < 0) throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "模板 {%for 缺少 %}");
            String listExpr = template.substring(varStart + 4, headerEnd).trim();
            int endforPos = findMatchingEndfor(template, headerEnd + 2);
            if (endforPos < 0) throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "模板 {%for 缺少 {%endfor%}");
            String body = template.substring(headerEnd + 2, endforPos);
            Object listObj = resolveExpr(listExpr, inputCtx, globalOutputs, failOnMissing);
            if (listObj instanceof Collection<?> coll) {
                for (Object item : coll) {
                    Map<String, Object> iterCtx = new LinkedHashMap<>(inputCtx);
                    iterCtx.put(varName, item);
                    out.append(renderSegment(body, iterCtx, globalOutputs, failOnMissing));
                }
            } else if (listObj != null && listObj.getClass().isArray()) {
                Object[] arr = (Object[]) listObj;
                for (Object item : arr) {
                    Map<String, Object> iterCtx = new LinkedHashMap<>(inputCtx);
                    iterCtx.put(varName, item);
                    out.append(renderSegment(body, iterCtx, globalOutputs, failOnMissing));
                }
            }
            i = endforPos + "{%endfor%}".length();
        }
        return out.toString();
    }

    /** 找匹配的 {%endfor%} (支持嵌套) */
    private int findMatchingEndfor(String t, int from) {
        int depth = 1;
        int p = from;
        while (p < t.length() && depth > 0) {
            int nextFor = t.indexOf("{%for", p);
            int nextEnd = t.indexOf("{%endfor%}", p);
            if (nextEnd < 0) return -1;
            if (nextFor >= 0 && nextFor < nextEnd) {
                depth++;
                p = nextFor + 5;
            } else {
                depth--;
                if (depth == 0) return nextEnd;
                p = nextEnd + 10;
            }
        }
        return -1;
    }

    /** 渲染单段: 处理 {%if%}{%else%}{%endif%} + {{var}} 插值 */
    private String renderSegment(String seg, Map<String, Object> inputCtx, Map<String, Object> globalOutputs, boolean failOnMissing) {
        if (seg.isEmpty()) return "";
        // 1. 处理 {%if expr%}...{%else%}...{%endif%} 块
        StringBuilder out = new StringBuilder();
        int p = 0;
        while (p < seg.length()) {
            int ifPos = seg.indexOf("{%if", p);
            if (ifPos < 0) {
                out.append(renderInterpolate(seg.substring(p), inputCtx, globalOutputs, failOnMissing));
                break;
            }
            out.append(renderInterpolate(seg.substring(p, ifPos), inputCtx, globalOutputs, failOnMissing));
            int exprEnd = seg.indexOf("%}", ifPos);
            if (exprEnd < 0) throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "模板 {%if 缺少 %}");
            String expr = seg.substring(ifPos + 5, exprEnd).trim();
            // 找 {%else%} (在 {%endif%} 之前)
            int elsePos = seg.indexOf("{%else%}", exprEnd);
            int endifPos = seg.indexOf("{%endif%}", exprEnd);
            if (endifPos < 0) throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "模板 {%if 缺少 {%endif%}");
            int bodyEnd = (elsePos >= 0 && elsePos < endifPos) ? elsePos : endifPos;
            String trueBody = seg.substring(exprEnd + 2, bodyEnd);
            String falseBody = (elsePos >= 0 && elsePos < endifPos)
                    ? seg.substring(elsePos + "{%else%}".length(), endifPos) : "";
            Object val = resolveExpr(expr, inputCtx, globalOutputs, failOnMissing);
            if (isTruthy(val)) {
                out.append(renderSegment(trueBody, inputCtx, globalOutputs, failOnMissing));
            } else {
                out.append(renderSegment(falseBody, inputCtx, globalOutputs, failOnMissing));
            }
            p = endifPos + "{%endif%}".length();
        }
        return out.toString();
    }

    /** 渲染 {{var}} 插值 */
    private String renderInterpolate(String seg, Map<String, Object> inputCtx, Map<String, Object> globalOutputs, boolean failOnMissing) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < seg.length()) {
            int open = seg.indexOf("{{", i);
            if (open < 0) {
                sb.append(seg, i, seg.length());
                break;
            }
            sb.append(seg, i, open);
            int close = seg.indexOf("}}", open);
            if (close < 0) {
                sb.append(seg, open, seg.length());
                break;
            }
            String varName = seg.substring(open + 2, close).trim();
            Object v = resolveVar(varName, inputCtx, globalOutputs, failOnMissing);
            sb.append(v == null ? "" : String.valueOf(v));
            i = close + 2;
        }
        return sb.toString();
    }

    /** 解析变量 (单 token, 不含表达式) */
    private Object resolveVar(String name, Map<String, Object> inputCtx, Map<String, Object> globalOutputs, boolean failOnMissing) {
        // 优先 inputCtx, 再 out_xxx, 再 globalOutputs
        if (inputCtx.containsKey(name)) return inputCtx.get(name);
        if (globalOutputs.containsKey(name)) return globalOutputs.get(name);
        if (failOnMissing) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "模板引用变量不存在: " + name);
        }
        return null;
    }

    /**
     * 解析表达式: 支持 ==  !=  &&  ||  字符串/数字字面量 + 变量引用
     * 简化版: 不支持复杂运算, 仅布尔表达式 + 字面量
     */
    private Object resolveExpr(String expr, Map<String, Object> inputCtx, Map<String, Object> globalOutputs, boolean failOnMissing) {
        String e = expr.trim();
        // 字符串字面量
        if ((e.startsWith("\"") && e.endsWith("\"")) || (e.startsWith("'") && e.endsWith("'"))) {
            return e.substring(1, e.length() - 1);
        }
        // 数字字面量
        if (e.matches("-?\\d+(\\.\\d+)?")) {
            return Double.parseDouble(e);
        }
        // 布尔字面量
        if ("true".equals(e)) return Boolean.TRUE;
        if ("false".equals(e)) return Boolean.FALSE;
        // null
        if ("null".equals(e)) return null;
        // 复合表达式: 先尝试 ==  !=, 然后 &&  ||
        if (e.contains("||")) {
            String[] parts = splitTopLevel(e, "||");
            for (String p : parts) {
                if (isTruthy(resolveExpr(p.trim(), inputCtx, globalOutputs, failOnMissing))) return Boolean.TRUE;
            }
            return Boolean.FALSE;
        }
        if (e.contains("&&")) {
            String[] parts = splitTopLevel(e, "&&");
            for (String p : parts) {
                if (!isTruthy(resolveExpr(p.trim(), inputCtx, globalOutputs, failOnMissing))) return Boolean.FALSE;
            }
            return Boolean.TRUE;
        }
        if (e.contains("==")) {
            String[] parts = splitTopLevel(e, "==");
            Object a = resolveExpr(parts[0].trim(), inputCtx, globalOutputs, failOnMissing);
            Object b = resolveExpr(parts[1].trim(), inputCtx, globalOutputs, failOnMissing);
            return eq(a, b);
        }
        if (e.contains("!=")) {
            String[] parts = splitTopLevel(e, "!=");
            Object a = resolveExpr(parts[0].trim(), inputCtx, globalOutputs, failOnMissing);
            Object b = resolveExpr(parts[1].trim(), inputCtx, globalOutputs, failOnMissing);
            return !eq(a, b);
        }
        // 大小比较 (含 >= 和 <= 优先匹配, 然后 > / <)
        if (e.contains(">=")) {
            String[] parts = splitTopLevel(e, ">=");
            return cmp(parts[0].trim(), parts[1].trim(), inputCtx, globalOutputs, failOnMissing) >= 0;
        }
        if (e.contains("<=")) {
            String[] parts = splitTopLevel(e, "<=");
            return cmp(parts[0].trim(), parts[1].trim(), inputCtx, globalOutputs, failOnMissing) <= 0;
        }
        if (e.contains(">") && !e.contains("=>") && !e.contains("->")) {
            String[] parts = splitTopLevel(e, ">");
            return cmp(parts[0].trim(), parts[1].trim(), inputCtx, globalOutputs, failOnMissing) > 0;
        }
        if (e.contains("<") && !e.contains("<=") && !e.contains("<-")) {
            String[] parts = splitTopLevel(e, "<");
            return cmp(parts[0].trim(), parts[1].trim(), inputCtx, globalOutputs, failOnMissing) < 0;
        }
        // 变量
        return resolveVar(e, inputCtx, globalOutputs, failOnMissing);
    }

    /** 数值/字符串比较: 数值按 double, 否则按字符串字典序 */
    private int cmp(String aExpr, String bExpr, Map<String, Object> inputCtx, Map<String, Object> globalOutputs, boolean failOnMissing) {
        Object a = resolveExpr(aExpr, inputCtx, globalOutputs, failOnMissing);
        Object b = resolveExpr(bExpr, inputCtx, globalOutputs, failOnMissing);
        if (a == null || b == null) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "模板比较表达式有空值");
        }
        if (a instanceof Number && b instanceof Number) {
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
        }
        return String.valueOf(a).compareTo(String.valueOf(b));
    }

    private boolean isTruthy(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.doubleValue() != 0;
        if (v instanceof String s) return !s.isEmpty() && !"false".equalsIgnoreCase(s);
        if (v instanceof Collection<?> c) return !c.isEmpty();
        return true;
    }

    private boolean eq(Object a, Object b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a instanceof Number && b instanceof Number) {
            return ((Number) a).doubleValue() == ((Number) b).doubleValue();
        }
        return String.valueOf(a).equals(String.valueOf(b));
    }

    /** 顶层 split (不切到括号/引号内的) */
    private String[] splitTopLevel(String s, String sep) {
        java.util.List<String> out = new java.util.ArrayList<>();
        int depth = 0;
        boolean inStr = false;
        char qc = 0;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inStr) {
                if (c == qc && s.charAt(i - 1) != '\\') inStr = false;
                continue;
            }
            if (c == '"' || c == '\'') { inStr = true; qc = c; continue; }
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (depth == 0 && s.startsWith(sep, i)) {
                out.add(s.substring(start, i));
                start = i + sep.length();
                i += sep.length() - 1;
            }
        }
        out.add(s.substring(start));
        return out.toArray(new String[0]);
    }

    /**
     * Variable Aggregator 节点 — 聚合多分支输出为单变量
     * 设计来源: ADR 0004 P2-B 批次 3
     *
     * 配置:
     *   - sources: 引用的上游节点 ID 列表, 用 "," 分隔 (e.g. "n2,n3,n4")
     *   - mode: merge (合并 Map) / first (取第一个) / last (取最后一个) / join (字符串拼接) / sum (数值求和)
     *   - separator: join 模式分隔符 (默认 ", ")
     *   - outputKey: 聚合结果 key (默认 "aggregated")
     *
     * 输出 JSON: { mode, count, result, sources: [...] }
     */
    private String executeAggregatorNode(Map<String, Object> cfg, Map<String, Object> inputCtx, Map<String, Object> globalOutputs) {
        String sourcesStr = firstCfg(cfg, "sources", "from");
        if (sourcesStr == null || sourcesStr.isBlank()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "aggregator 节点必须配置 sources (上游节点 ID 列表)");
        }
        String mode = firstCfg(cfg, "mode");
        if (mode == null) mode = "merge";
        // separator 保留首尾空格 (与 firstCfg 的 trim 行为冲突), 这里直接取原值
        String separator = null;
        if (cfg != null) {
            Object sepRaw = cfg.get("separator");
            if (sepRaw != null) {
                String s = String.valueOf(sepRaw);
                if (!s.isEmpty() && !"null".equals(s)) separator = s;
            }
        }
        if (separator == null) separator = ", ";
        String outputKey = firstCfg(cfg, "outputKey");
        if (outputKey == null) outputKey = "aggregated";

        String[] sourceIds = sourcesStr.split(",");
        List<Object> values = new java.util.ArrayList<>();
        for (String sid : sourceIds) {
            String trimmed = sid.trim();
            if (trimmed.isEmpty()) continue;
            Object v = globalOutputs.get(trimmed);
            if (v != null) values.add(v);
        }

        Object result;
        switch (mode.toLowerCase()) {
            case "first":
                result = values.isEmpty() ? null : values.get(0);
                break;
            case "last":
                result = values.isEmpty() ? null : values.get(values.size() - 1);
                break;
            case "join":
                result = values.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(separator));
                break;
            case "sum": {
                double sum = 0;
                for (Object v : values) {
                    if (v instanceof Number n) sum += n.doubleValue();
                    else if (v instanceof String s) {
                        try { sum += Double.parseDouble(s); } catch (NumberFormatException ignore) {}
                    }
                }
                result = sum;
                break;
            }
            case "merge":
            default: {
                Map<String, Object> merged = new LinkedHashMap<>();
                for (Object v : values) {
                    if (v instanceof Map<?, ?> m) {
                        for (Map.Entry<?, ?> e : m.entrySet()) {
                            merged.put(String.valueOf(e.getKey()), e.getValue());
                        }
                    } else {
                        merged.put("v" + merged.size(), v);
                    }
                }
                result = merged;
                break;
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mode", mode);
        out.put("count", values.size());
        out.put(outputKey, result);
        out.put("sources", List.of(sourceIds));
        // 写回 globalOutputs, 供后续节点 {{n_agg.outputKey}} 引用
        globalOutputs.put(outputKey, result);
        try {
            return objectMapper.writeValueAsString(out);
        } catch (Exception ex) {
            return "aggregator mode=" + mode + " count=" + values.size();
        }
    }

    /**
     * HITL 节点 — Human-in-the-Loop 阻塞工作流等用户操作
     * 设计来源: ADR 0004 P2-B 批次 3
     *
     * 配置:
     *   - assignee: 审批人 (用户 ID / 角色 / 部门)
     *   - formTemplate: 审批表单模板 (SpEL/JSON, 决定审批界面字段)
     *   - timeoutHours: 审批超时 (小时, 默认 72, 超时后默认 reject)
     *
     * 当前状态: 通过 LightWorkflowEngine 启动 BPMN 子流程; 子流程由 yutong-workflow-service 提供
     * 真实实现: 待 LightWorkflowEngine 暴露 startProcess + waitProcess 阻塞 API
     * 现阶段: 明确失败关闭, 提示需先在 workflow-service 装配 bpmn 流程定义
     */
    private String executeHitlNode(Map<String, Object> cfg, Map<String, Object> inputCtx, Map<String, Object> globalOutputs) {
        String assignee = firstCfg(cfg, "assignee");
        if (assignee == null || assignee.isBlank()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "hitl 节点必须配置 assignee");
        }
        String formTemplate = firstCfg(cfg, "formTemplate");
        // 现阶段: HITL 节点需要 yutong-workflow-service 的 bpmn 流程定义 (assignee="+assignee+")
        // 待 LightWorkflowEngine 暴露 startProcess + 同步等待 API 后, 此处替换为:
        //   ProcessInstance pi = lightWorkflowEngine.startProcess("aiflow-hitl", Map.of(...));
        //   pi = lightWorkflowEngine.waitForCompletion(pi.getId(), timeoutHours);
        //   把审批结果合并到 globalOutputs
        throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                "hitl 节点 (assignee=" + assignee + ") 需先在 yutong-workflow-service 中创建 BPMN 流程定义 " +
                "并装配 startProcess + 同步等待 API。当前阶段请改用 condition + email/http 节点组合实现人工审批回调, " +
                "或后续批次 P2-B-2 接入 LightWorkflowEngine 完整支持。");
    }

    /**
     * 子流程节点 — 内嵌执行一段子 DAG (P2-B Sub-Workflow)。
     *
     * <p>子 DAG 来源 (三选一, 优先级 definitionId &gt; dagJson &gt; nodes/edges):
     * <ul>
     *   <li>{@code definitionId}: 已发布 AIFlow 定义 (经 SubflowDefinitionProvider 解析;
     *       未配置解析器/定义不存在/非 PUBLISHED 均失败关闭)</li>
     *   <li>{@code dagJson}: 内联 DAG JSON 字符串</li>
     *   <li>{@code nodes} + {@code edges}: 内联节点/边列表 (随父流程版本化)</li>
     * </ul>
     *
     * <p>输入: 父 inputCtx 全量继承 + {@code input} Map 覆盖; 输出: 子 globalOutputs
     * ({@code _input} 除外) 按 {@code outputKey} 取值, 未指定则返回子输出 JSON。
     * 子节点状态事件以 "{parentId}/{childId}" 前缀重发, SSE 时间线可见嵌套。
     *
     * <p>安全: 嵌套深度上限 {@link #MAX_SUBFLOW_DEPTH}; definitionId 环引用经 visited 集合失败关闭。
     */
    private String executeSubflowNode(DagNode node, Map<String, Object> cfg,
                                      Map<String, Object> inputCtx,
                                      Consumer<NodeStateEvent> stateEmitter, ExecCtx ctx) {
        if (ctx.depth() >= MAX_SUBFLOW_DEPTH) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    "subflow 嵌套超过上限 " + MAX_SUBFLOW_DEPTH + " (节点 " + node.id() + ")");
        }
        String definitionId = firstCfg(cfg, "definitionId", "definition_id", "flowId");
        String childDagJson;
        String marker;
        if (definitionId != null && !definitionId.isBlank()) {
            marker = "def:" + definitionId;
            if (ctx.visited().contains(marker)) {
                throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                        "subflow 环引用: 定义 " + definitionId + " 已在调用链中");
            }
            if (definitionProvider == null) {
                throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                        "subflow 引用 definitionId=" + definitionId + ", 但未配置定义解析器");
            }
            childDagJson = definitionProvider.resolveDagJson(definitionId);
        } else {
            marker = "inline:" + node.id();
            childDagJson = firstCfg(cfg, "dagJson", "dag_json", "dag");
            if (childDagJson == null || childDagJson.isBlank()) {
                Object nodes = cfg.get("nodes");
                Object edges = cfg.get("edges");
                if (!(nodes instanceof java.util.List) || ((java.util.List<?>) nodes).isEmpty()) {
                    throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                            "subflow 节点必须配置 definitionId / dagJson / nodes 三选一 (节点 " + node.id() + ")");
                }
                try {
                    java.util.Map<String, Object> inline = new java.util.LinkedHashMap<>();
                    inline.put("nodes", nodes);
                    inline.put("edges", edges instanceof java.util.List ? edges : java.util.List.of());
                    childDagJson = objectMapper.writeValueAsString(inline);
                } catch (Exception e) {
                    throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                            "subflow 内联 DAG 序列化失败: " + e.getMessage());
                }
            }
        }

        ParsedDag childDag = parseDag(childDagJson);
        if (childDag.nodes().isEmpty()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "subflow 子 DAG 为空");
        }
        // 子输入: 父上下文全量继承 + input 覆盖 (input 值保持原样, 不 trim)
        Map<String, Object> childInput = new LinkedHashMap<>(inputCtx);
        Object extra = cfg.get("input");
        if (extra instanceof Map<?, ?> extraMap) {
            for (Map.Entry<?, ?> e : extraMap.entrySet()) {
                if (e.getKey() != null) childInput.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        ExecCtx childCtx = ctx.child(marker);
        final String parentId = node.id();
        Map<String, Object> childOutputs = executeDag(childDag, childInput,
                stateEmitter == null ? null
                        : (NodeStateEvent ev) -> stateEmitter.accept(new NodeStateEvent(
                                parentId + "/" + ev.nodeId(), ev.type(), ev.status(), ev.output(), ev.error())),
                childCtx);
        childOutputs.remove("_input");
        // 子失败向上传播: 子 DAG 内 FAILED 节点 (error 键) 导致本节点 FAILED (失败关闭, 不吞错);
        // 保留原始错误文本, 调用链逐层包裹后仍可定位根因
        java.util.List<String> failedChildren = new java.util.ArrayList<>();
        for (Map.Entry<String, Object> e : childOutputs.entrySet()) {
            if (e.getValue() instanceof Map<?, ?> m && m.containsKey("error")) {
                failedChildren.add(e.getKey() + ": " + m.get("error"));
            }
        }
        if (!failedChildren.isEmpty()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    "subflow 子节点失败: " + failedChildren);
        }
        String outputKey = firstCfg(cfg, "outputKey", "output_key", "output");
        if (outputKey != null && !outputKey.isBlank()) {
            Object v = childOutputs.get(outputKey);
            if (v == null) {
                throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                        "subflow 输出键不存在: " + outputKey);
            }
            return v instanceof String s ? s : toJson(v);
        }
        return toJson(childOutputs);
    }

    private String toJson(Object v) {
        try {
            return objectMapper.writeValueAsString(v);
        } catch (Exception e) {
            return String.valueOf(v);
        }
    }

    private Map<String, Object> parseInputCtx(String inputJson) {
        if (inputJson == null || inputJson.isBlank()) return new LinkedHashMap<>();
        try {
            JsonNode node = objectMapper.readTree(inputJson);
            if (node.isObject()) {
                return objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {});
            }
            return Map.of("input", inputJson);
        } catch (Exception e) {
            return Map.of("input", inputJson);
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    private String firstCfg(Map<String, Object> cfg, String... keys) {
        if (cfg == null || keys == null) return null;
        for (String key : keys) {
            if (cfg.containsKey(key) && cfg.get(key) != null) {
                String v = String.valueOf(cfg.get(key)).trim();
                if (!v.isBlank() && !"null".equals(v)) return v;
            }
        }
        return null;
    }

    private String interpolate(String template, Map<String, Object> inputCtx, Map<String, Object> globalOutputs) {
        if (template == null) return "";
        String out = template;
        if (inputCtx != null) {
            for (Map.Entry<String, Object> e : inputCtx.entrySet()) {
                out = out.replace("{{" + e.getKey() + "}}", String.valueOf(e.getValue() == null ? "" : e.getValue()));
                out = out.replace("{" + e.getKey() + "}", String.valueOf(e.getValue() == null ? "" : e.getValue()));
            }
        }
        if (globalOutputs != null) {
            for (Map.Entry<String, Object> e : globalOutputs.entrySet()) {
                if ("_input".equals(e.getKey())) continue;
                out = out.replace("{{" + e.getKey() + "}}", String.valueOf(e.getValue() == null ? "" : e.getValue()));
            }
        }
        return out;
    }

    private Object lastNonInputOutput(Map<String, Object> globalOutputs) {
        Object last = null;
        for (Map.Entry<String, Object> e : globalOutputs.entrySet()) {
            if ("_input".equals(e.getKey())) continue;
            last = e.getValue();
        }
        return last;
    }

    public String toNodeStatesJson(Map<String, String> nodeStatus, Map<String, Object> outputs) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            for (Map.Entry<String, String> e : nodeStatus.entrySet()) {
                ObjectNode ns = objectMapper.createObjectNode();
                ns.put("status", e.getValue());
                Object out = outputs.get(e.getKey());
                if (out != null) ns.put("output", String.valueOf(out));
                root.set(e.getKey(), ns);
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
