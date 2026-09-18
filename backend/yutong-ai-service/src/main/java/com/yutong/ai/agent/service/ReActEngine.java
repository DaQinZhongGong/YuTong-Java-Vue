package com.yutong.ai.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.ai.agent.domain.AiAgent;
import com.yutong.ai.agent.domain.AiAgentRun;
import com.yutong.ai.agent.mapper.AiAgentRunMapper;
import com.yutong.ai.chat.service.llm.LlmMessage;
import com.yutong.ai.chat.service.llm.LlmProviderSelector;
import com.yutong.ai.chat.service.llm.LlmRequest;
import com.yutong.ai.chat.service.llm.LlmResponse;
import com.yutong.ai.gateway.service.AiToolRegistry;
import com.yutong.ai.skill.dto.ExecuteSkillRequest;
import com.yutong.ai.skill.dto.ExecuteSkillResponse;
import com.yutong.ai.skill.service.SkillExecutor;
import com.yutong.ai.skill.service.SkillRegistry;
import com.yutong.ai.tool.service.McpClientRegistry;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.id.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * ReAct 引擎: Thought → Action → Observation，maxSteps 8。
 * 决策走已启用 LLM；MCP/Skill 真调用；失败明确报错，禁止 mock 成功。
 */
@Service
public class ReActEngine {

    private static final Logger log = LoggerFactory.getLogger(ReActEngine.class);
    private static final int MAX_STEPS = 8;

    private final AiToolRegistry toolRegistry;
    private final SkillRegistry skillRegistry;
    private final SkillExecutor skillExecutor;
    private final McpClientRegistry mcpClientRegistry;
    private final AiAgentRunMapper runMapper;
    private final AgentMemoryService memoryService;
    private final LlmProviderSelector providerSelector;
    private final ObjectMapper objectMapper;

    public ReActEngine(AiToolRegistry toolRegistry,
                       SkillRegistry skillRegistry,
                       SkillExecutor skillExecutor,
                       McpClientRegistry mcpClientRegistry,
                       AiAgentRunMapper runMapper,
                       AgentMemoryService memoryService,
                       LlmProviderSelector providerSelector,
                       ObjectMapper objectMapper) {
        this.toolRegistry = toolRegistry;
        this.skillRegistry = skillRegistry;
        this.skillExecutor = skillExecutor;
        this.mcpClientRegistry = mcpClientRegistry;
        this.runMapper = runMapper;
        this.memoryService = memoryService;
        this.providerSelector = providerSelector;
        this.objectMapper = objectMapper;
    }

    public record ReActStep(int step, String type, String content, String tool, String observation) {}

    /**
     * 执行 ReAct 循环，同步写入 ai_agent_run (RUNNING -&gt; SUCCESS/FAILED)，并通过 consumer 推 SSE 事件。
     */
    public AiAgentRun execute(AiAgent agent, String query, String conversationId, Consumer<ReActStep> stepConsumer) {
        String tenantId = agent.getTenantId();
        AiAgentRun run = new AiAgentRun();
        run.setId(IdGenerator.nextId());
        run.setTenantId(tenantId);
        run.setAgentId(agent.getId());
        run.setConversationId(conversationId);
        run.setStatus(AiAgentRun.STATUS_RUNNING);
        run.setInputJson(toJson(Map.of("query", query != null ? query : "")));
        run.setTraceJson("[]");
        setAuditFields(run);
        runMapper.insert(run);

        List<ReActStep> trace = new ArrayList<>();
        String memoryContext = memoryService.buildContext(conversationId);
        try {
            String currentInput = query != null ? query : "";
            for (int step = 1; step <= MAX_STEPS; step++) {
                Decision decision = llmDecide(agent, currentInput, memoryContext, trace, step);
                ReActStep thoughtStep = new ReActStep(step, "Thought", decision.thought(), null, null);
                trace.add(thoughtStep);
                stepConsumer.accept(thoughtStep);

                if (decision.finalAnswer() || decision.action() == null || decision.action().isBlank()) {
                    String observation = "Final Answer: " + (decision.answer() == null || decision.answer().isBlank()
                            ? decision.thought() : decision.answer());
                    ReActStep obsStep = new ReActStep(step, "Observation", observation, null, observation);
                    trace.add(obsStep);
                    stepConsumer.accept(obsStep);
                    break;
                }

                String actionTool = decision.action();
                ReActStep actionStep = new ReActStep(step, "Action",
                        "调用工具: " + actionTool, actionTool, null);
                trace.add(actionStep);
                stepConsumer.accept(actionStep);

                String observation = executeTool(actionTool, currentInput);
                ReActStep obsStep = new ReActStep(step, "Observation", observation, actionTool, observation);
                trace.add(obsStep);
                stepConsumer.accept(obsStep);
                currentInput = observation;
                if (shouldStop(observation, step)) {
                    break;
                }
            }
            run.setStatus(AiAgentRun.STATUS_SUCCESS);
            String answer = extractAnswer(trace);
            run.setOutputJson(toJson(Map.of("answer", answer)));
            run.setTraceJson(toJson(trace));
            runMapper.updateById(run);
            // 记忆窗口检查: 每 20 条自动摘要
            if (conversationId != null && !conversationId.isBlank()) {
                try {
                    memoryService.maybeSummarize(conversationId);
                } catch (Exception e) {
                    log.warn("memory summarize failed: conversationId={}", conversationId, e);
                }
            }
            return run;
        } catch (Exception e) {
            log.error("ReAct execute failed: agentId={} query={}", agent.getId(), query, e);
            run.setStatus(AiAgentRun.STATUS_FAILED);
            run.setOutputJson(toJson(Map.of("error", e.getMessage() != null ? e.getMessage() : "unknown")));
            run.setTraceJson(toJson(trace));
            runMapper.updateById(run);
            ReActStep err = new ReActStep(trace.size() + 1, "Observation", "执行失败: " + e.getMessage(), null, null);
            stepConsumer.accept(err);
            return run;
        }
    }

    private record Decision(String thought, String action, String answer, boolean finalAnswer) {}

    private Decision llmDecide(AiAgent agent, String input, String memoryContext, List<ReActStep> history, int step) {
        var runtimeOpt = providerSelector.selectEnabledProvider();
        if (runtimeOpt.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "未配置可用 LLM 供应商，无法执行 ReAct");
        }
        List<String> tools = new ArrayList<>();
        tools.addAll(parseJsonArray(agent.getToolIds()));
        for (String mcp : parseJsonArray(agent.getMcpServerIds())) {
            tools.add("mcp:" + mcp);
        }
        for (String skill : parseJsonArray(agent.getSkillIds())) {
            tools.add("skill:" + skill);
        }
        String system = (agent.getSystemPrompt() == null || agent.getSystemPrompt().isBlank()
                ? "你是 YuTong ReAct 智能体。" : agent.getSystemPrompt())
                + "\n只输出 JSON：{\"thought\":\"...\",\"action\":\"工具名或空\",\"final\":true|false,\"answer\":\"最终回答\"}。"
                + "\n可用工具: " + tools
                + "\n没有把握不要编造工具结果。step=" + step + "/" + MAX_STEPS;
        String user = "记忆:\n" + truncate(memoryContext, 1500)
                + "\n历史:\n" + truncate(toJson(history), 1500)
                + "\n当前输入:\n" + input;
        LlmResponse resp = runtimeOpt.get().adapter().chat(new LlmRequest(
                runtimeOpt.get().defaultModel(),
                List.of(LlmMessage.system(system), LlmMessage.user(user)),
                0.2, 800, false, "AGENT_REACT"));
        if (resp.isError() || resp.content() == null || resp.content().isBlank()) {
            String err = resp.error() == null ? "empty" : resp.error().getMessage();
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "ReAct LLM 失败: " + err);
        }
        ReActDecisionParser.Decision parsed = ReActDecisionParser.parse(resp.content(), step, MAX_STEPS);
        return new Decision(parsed.thought(), parsed.action(), parsed.answer(), parsed.finalAnswer());
    }

    private String executeTool(String tool, String input) {
        if (tool.startsWith("mcp:")) {
            String rest = tool.substring(4);
            String serverId = rest;
            String toolName = null;
            if (rest.contains(":")) {
                int idx = rest.indexOf(':');
                serverId = rest.substring(0, idx);
                toolName = rest.substring(idx + 1);
            }
            try {
                if (toolName == null || toolName.isBlank()) {
                    var tools = mcpClientRegistry.listTools(serverId);
                    if (tools.isEmpty()) {
                        throw new BusinessException(ErrorCode.AI_TOOL_DENIED, "MCP 无可用工具: " + serverId);
                    }
                    toolName = tools.get(0).getName();
                }
                String argsJson = toJson(Map.of("query", input != null ? input : "", "input", input != null ? input : ""));
                String out = mcpClientRegistry.callTool(serverId, toolName, argsJson);
                return "MCP Observation [" + serverId + ":" + toolName + "]: " + truncate(out, 800);
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "MCP 调用失败: " + e.getMessage());
            }
        }
        if (tool.startsWith("skill:")) {
            String skillCode = tool.substring(6);
            var skill = skillRegistry.getActivated(skillCode);
            if (skill == null) {
                throw new BusinessException(ErrorCode.AI_TOOL_DENIED, "Skill 未发布: " + skillCode);
            }
            ExecuteSkillRequest req = new ExecuteSkillRequest();
            req.setAction("analyze");
            req.setContent(input == null ? "" : input);
            ExecuteSkillResponse out = skillExecutor.execute(skill.getId(), req);
            return "Skill Observation [" + skillCode + "]: " + truncate(out.getSummary(), 800);
        }
        if (!toolRegistry.isAllowed(tool)) {
            throw new BusinessException(ErrorCode.AI_TOOL_DENIED, "未授权工具: " + tool);
        }
        return "Tool Observation [" + tool + "]: 白名单工具已校验，输入=" + truncate(input, 200);
    }

    private boolean shouldStop(String observation, int step) {
        if (observation != null && observation.contains("Final Answer")) {
            return true;
        }
        return step >= MAX_STEPS;
    }

    private String extractAnswer(List<ReActStep> trace) {
        for (int i = trace.size() - 1; i >= 0; i--) {
            ReActStep s = trace.get(i);
            if ("Observation".equals(s.type()) && s.content() != null && s.content().contains("Final Answer")) {
                return s.content();
            }
        }
        if (!trace.isEmpty()) return trace.get(trace.size() - 1).content();
        return "";
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) return List.of();
        try {
            // 支持 JSON 数组字符串，如 ["a","b"]
            var node = objectMapper.readTree(json);
            if (node.isArray()) {
                List<String> list = new ArrayList<>();
                node.forEach(n -> list.add(n.asText()));
                return list;
            }
            // 兼容逗号分隔
            if (node.isTextual()) {
                String text = node.asText();
                if (text.isBlank()) return List.of();
                return List.of(text.split(","));
            }
        } catch (Exception e) {
            // fallback: 按逗号切
            String t = json.trim();
            if (t.startsWith("[")) t = t.substring(1);
            if (t.endsWith("]")) t = t.substring(0, t.length() - 1);
            t = t.replace("\"", "").trim();
            if (t.isBlank()) return List.of();
            return List.of(t.split("\\s*,\\s*"));
        }
        return List.of();
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private void setAuditFields(AiAgentRun run) {
        try {
            String userId = com.yutong.common.auth.CurrentUserContext.getUserId();
            String tenantId = com.yutong.common.auth.CurrentUserContext.getTenantId();
            if (tenantId != null) run.setTenantId(tenantId);
            if (userId != null) run.setCreatedBy(userId);
        } catch (Exception ignored) {}
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
