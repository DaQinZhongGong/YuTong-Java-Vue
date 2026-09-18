package com.yutong.ai.agent.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReActDecisionParseTest {

    @Test
    void parse_shouldReadFencedJson() {
        ReActDecisionParser.Decision d = ReActDecisionParser.parse(
                "```json\n{\"thought\":\"需要查库\",\"action\":\"mcp:filesystem\",\"final\":false,\"answer\":\"\"}\n```",
                1, 8);
        assertEquals("需要查库", d.thought());
        assertEquals("mcp:filesystem", d.action());
        assertFalse(d.finalAnswer());
    }

    @Test
    void parse_garbageBecomesFinalAnswer() {
        ReActDecisionParser.Decision d = ReActDecisionParser.parse("not json", 1, 8);
        assertNull(d.action());
        assertTrue(d.finalAnswer());
    }
}
