package com.yutong.ai.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 解析 ReAct LLM JSON 决策。容忍 markdown 围栏；非法 JSON 视为最终回答。
 */
public final class ReActDecisionParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record Decision(String thought, String action, String answer, boolean finalAnswer) {}

    private ReActDecisionParser() {
    }

    public static Decision parse(String raw, int step, int maxSteps) {
        if (raw == null || raw.isBlank()) {
            return new Decision("empty", null, "", true);
        }
        String json = raw.trim();
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start >= 0 && end > start) {
            json = json.substring(start, end + 1);
        }
        try {
            JsonNode n = MAPPER.readTree(json);
            String thought = n.path("thought").asText("思考中");
            String action = n.path("action").asText(null);
            if (action != null && action.isBlank()) {
                action = null;
            }
            boolean fin = n.path("final").asBoolean(false) || step >= maxSteps;
            String answer = n.path("answer").asText("");
            if (fin) {
                action = null;
            }
            return new Decision(thought, action, answer, fin);
        } catch (Exception e) {
            String t = raw.length() > 400 ? raw.substring(0, 400) : raw;
            String a = raw.length() > 800 ? raw.substring(0, 800) : raw;
            return new Decision(t, null, a, true);
        }
    }
}
