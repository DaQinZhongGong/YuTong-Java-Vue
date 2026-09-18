package com.yutong.ai.agent.service;

import com.yutong.ai.agent.domain.AiAgent;
import com.yutong.ai.agent.domain.AiAgentRun;
import com.yutong.ai.agent.mapper.AiAgentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Supervisor 智能体: 顺序编排多个子智能体。
 * 当前顺序执行，聚合各子智能体的 trace 与输出，返回统一 ReActStep 流。
 */
@Service
public class SupervisorAgent {

    private static final Logger log = LoggerFactory.getLogger(SupervisorAgent.class);

    private final AiAgentMapper agentMapper;
    private final ReActEngine reActEngine;

    public SupervisorAgent(AiAgentMapper agentMapper, ReActEngine reActEngine) {
        this.agentMapper = agentMapper;
        this.reActEngine = reActEngine;
    }

    /**
     * 顺序编排多个智能体。subAgentIds 按序执行，前一个输出作为后一个输入。
     */
    public List<AiAgentRun> orchestrateSequential(AiAgent supervisor, List<String> subAgentIds,
                                                  String query, String conversationId,
                                                  Consumer<ReActEngine.ReActStep> stepConsumer) {
        List<AiAgentRun> runs = new ArrayList<>();
        String currentInput = query;
        for (int i = 0; i < subAgentIds.size(); i++) {
            String subId = subAgentIds.get(i);
            AiAgent subAgent = agentMapper.selectById(subId);
            if (subAgent == null) {
                log.warn("supervisor orchestrate: subAgent not found id={} index={}", subId, i);
                ReActEngine.ReActStep err = new ReActEngine.ReActStep(
                        runs.size() + 1, "Observation", "子智能体不存在: " + subId, null, null);
                stepConsumer.accept(err);
                continue;
            }
            // 注入 supervisor 上下文到 thought
            ReActEngine.ReActStep delegateThought = new ReActEngine.ReActStep(
                    runs.size() + 1, "Thought",
                    "Supervisor [" + supervisor.getAgentCode() + "] 委派给子智能体 [" + subAgent.getAgentCode() + "] (" + subAgent.getAgentType() + ")",
                    null, null);
            stepConsumer.accept(delegateThought);

            AiAgentRun run = reActEngine.execute(subAgent, currentInput, conversationId, stepConsumer);
            runs.add(run);
            // 下一轮输入 = 上一轮输出的 answer
            if (run.getOutputJson() != null && run.getOutputJson().contains("answer")) {
                // 轻量提取 answer 字段
                String out = run.getOutputJson();
                int idx = out.indexOf("\"answer\"");
                if (idx >= 0) {
                    int colon = out.indexOf(':', idx);
                    int q1 = out.indexOf('"', colon + 1);
                    int q2 = q1 >= 0 ? out.indexOf('"', q1 + 1) : -1;
                    if (q1 >= 0 && q2 > q1) {
                        currentInput = out.substring(q1 + 1, q2);
                    }
                }
            }
            if (AiAgentRun.STATUS_FAILED.equals(run.getStatus())) {
                log.warn("supervisor orchestrate: subAgent failed id={} code={}", subId, subAgent.getAgentCode());
                break;
            }
        }
        return runs;
    }
}
