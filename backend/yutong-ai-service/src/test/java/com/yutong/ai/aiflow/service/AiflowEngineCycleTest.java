package com.yutong.ai.aiflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiflowEngineCycleTest {

    @Test
    void topologicalLevels_shouldRejectCycle() {
        AiflowEngine engine = new AiflowEngine(new ObjectMapper(), null, null, null, null, null, null);
        AiflowEngine.ParsedDag dag = new AiflowEngine.ParsedDag(
                List.of(
                        new AiflowEngine.DagNode("a", "input", java.util.Map.of()),
                        new AiflowEngine.DagNode("b", "output", java.util.Map.of())
                ),
                List.of(
                        new AiflowEngine.DagEdge("a", "b", null),
                        new AiflowEngine.DagEdge("b", "a", null)
                )
        );
        BusinessException ex = assertThrows(BusinessException.class, () -> engine.topologicalLevels(dag));
        assertTrue(ex.getMessage().contains("环"));
    }

    @Test
    void emailNode_shouldFailClosed() {
        AiflowEngine engine = new AiflowEngine(new ObjectMapper(), null, null, null, null, null, null);
        BusinessException ex = assertThrows(BusinessException.class, () ->
                org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                        engine, "executeEmailNode", java.util.Map.of("to", "a@b.c"), java.util.Map.of()));
        assertTrue(ex.getMessage().contains("禁止模拟发送"));
    }
}
