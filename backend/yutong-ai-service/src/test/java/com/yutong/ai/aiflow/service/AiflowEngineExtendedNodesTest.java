package com.yutong.ai.aiflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AIFlow 节点补强测试 (Code / Templating / Aggregator / HITL)
 * 设计来源: ADR 0004 P2-B 批次 3
 *
 * 覆盖:
 *  - Code 节点: 简单 JS 表达式 + 超时 + 错误处理
 *  - Templating 节点: 插值 + if 条件 + for 循环 + 嵌套
 *  - Aggregator 节点: merge / first / last / join / sum 五种模式
 *  - HITL 节点: 当前明确失败关闭 (LightWorkflowEngine 未接入)
 */
class AiflowEngineExtendedNodesTest {

    private final ObjectMapper om = new ObjectMapper();
    private final AiflowEngine engine = new AiflowEngine(om, null, null, null, null, null, null);

    // ============ Code 节点 ============

    @Test
    void codeNode_evaluatesArithmetic() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("code", "1 + 2 + 3");
        String out = (String) ReflectionTestUtils.invokeMethod(engine, "executeCodeNode", cfg, new LinkedHashMap<>(), new LinkedHashMap<>());
        assertEquals("6", out, "SpEL 算术返回 Integer, 转 String 是 '6'");
    }

    @Test
    void codeNode_evaluatesStringConcat() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("code", "'hello ' + 'world'");
        String out = (String) ReflectionTestUtils.invokeMethod(engine, "executeCodeNode", cfg, new LinkedHashMap<>(), new LinkedHashMap<>());
        assertEquals("hello world", out);
    }

    @Test
    void codeNode_accessesInputBindings() {
        // SpEL #input['query'] 访问 Map 字段 (大括号访问)
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("code", "#input['query'].toUpperCase()");
        Map<String, Object> input = Map.of("query", "hello world");
        String out = (String) ReflectionTestUtils.invokeMethod(engine, "executeCodeNode", cfg, input, new LinkedHashMap<>());
        assertEquals("HELLO WORLD", out);
    }

    @Test
    void codeNode_accessesTopLevelBindings() {
        // SpEL #name + ' / ' + #score
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("code", "#name + ' / ' + #score");
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("name", "alice");
        input.put("score", 100);
        String out = (String) ReflectionTestUtils.invokeMethod(engine, "executeCodeNode", cfg, input, new LinkedHashMap<>());
        assertEquals("alice / 100", out);
    }

    @Test
    void codeNode_collectionMethod() {
        // 测试 SpEL 调用 List 方法
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("code", "#input['items'].size()");
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("items", List.of("a", "b", "c"));
        String out = (String) ReflectionTestUtils.invokeMethod(engine, "executeCodeNode", cfg, input, new LinkedHashMap<>());
        assertEquals("3", out);
    }

    @Test
    void codeNode_missingCode_throws() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("code", "");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ReflectionTestUtils.invokeMethod(engine, "executeCodeNode", cfg, new LinkedHashMap<>(), new LinkedHashMap<>()));
        assertTrue(ex.getMessage().contains("code 表达式"));
    }

    @Test
    void codeNode_runtimeError_wrappedAsBusinessException() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("code", "#undefinedVariable.foo.bar");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ReflectionTestUtils.invokeMethod(engine, "executeCodeNode", cfg, new LinkedHashMap<>(), new LinkedHashMap<>()));
        assertTrue(ex.getMessage().contains("code 节点执行失败"));
    }

    // ============ Templating 节点 ============

    @Test
    void templating_interpolatesSimpleVar() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("template", "hello {{name}}!");
        Map<String, Object> input = Map.of("name", "alice");
        String out = (String) ReflectionTestUtils.invokeMethod(engine, "executeTemplatingNode", cfg, input, new LinkedHashMap<>());
        assertEquals("hello alice!", out);
    }

    @Test
    void templating_interpolatesFromGlobalOutputs() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("template", "answer: {{n1}}");
        Map<String, Object> input = new LinkedHashMap<>();
        Map<String, Object> outs = new LinkedHashMap<>();
        outs.put("n1", "42");
        String out = (String) ReflectionTestUtils.invokeMethod(engine, "executeTemplatingNode", cfg, input, outs);
        assertEquals("answer: 42", out);
    }

    @Test
    void templating_rendersIfCondition_true() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("template", "{%if status == \"ok\"%}PASSED{%endif%}");
        Map<String, Object> input = Map.of("status", "ok");
        String out = (String) ReflectionTestUtils.invokeMethod(engine, "executeTemplatingNode", cfg, input, new LinkedHashMap<>());
        assertEquals("PASSED", out);
    }

    @Test
    void templating_rendersIfCondition_false() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("template", "{%if status == \"ok\"%}PASSED{%else%}FAILED{%endif%}");
        Map<String, Object> input = Map.of("status", "err");
        String out = (String) ReflectionTestUtils.invokeMethod(engine, "executeTemplatingNode", cfg, input, new LinkedHashMap<>());
        assertEquals("FAILED", out);
    }

    @Test
    void templating_rendersForLoop() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("template", "{%for x in items%}[{{x}}]{%endfor%}");
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("items", List.of("a", "b", "c"));
        String out = (String) ReflectionTestUtils.invokeMethod(engine, "executeTemplatingNode", cfg, input, new LinkedHashMap<>());
        assertEquals("[a][b][c]", out);
    }

    @Test
    void templating_nestedForLoop() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("template", "{%for x in items%}{%if x == \"skip\"%}*{%else%}{{x}}{%endif%}{%endfor%}");
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("items", List.of("a", "skip", "b"));
        String out = (String) ReflectionTestUtils.invokeMethod(engine, "executeTemplatingNode", cfg, input, new LinkedHashMap<>());
        assertEquals("a*b", out);
    }

    @Test
    void templating_complexExpression() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("template", "{%if score > 60 && pass == true%}通过 ({{score}}){%endif%}");
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("score", 85);
        input.put("pass", true);
        String out = (String) ReflectionTestUtils.invokeMethod(engine, "executeTemplatingNode", cfg, input, new LinkedHashMap<>());
        assertEquals("通过 (85)", out);
    }

    @Test
    void templating_missingVar_throwsWhenFailOnMissing() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("template", "hello {{missing}}!");
        cfg.put("failOnMissing", "true");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ReflectionTestUtils.invokeMethod(engine, "executeTemplatingNode", cfg, new LinkedHashMap<>(), new LinkedHashMap<>()));
        assertTrue(ex.getMessage().contains("missing"));
    }

    @Test
    void templating_missingVar_silentWhenFailOnMissingFalse() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("template", "hello {{missing}}!");
        cfg.put("failOnMissing", "false");
        String out = (String) ReflectionTestUtils.invokeMethod(engine, "executeTemplatingNode", cfg, new LinkedHashMap<>(), new LinkedHashMap<>());
        assertEquals("hello !", out);
    }

    @Test
    void templating_emptyTemplate_throws() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("template", "");
        assertThrows(BusinessException.class,
                () -> ReflectionTestUtils.invokeMethod(engine, "executeTemplatingNode", cfg, new LinkedHashMap<>(), new LinkedHashMap<>()));
    }

    // ============ Aggregator 节点 ============

    @Test
    void aggregator_mergeMode_mergesMaps() throws Exception {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("sources", "n1,n2");
        cfg.put("mode", "merge");
        Map<String, Object> outputs = new LinkedHashMap<>();
        outputs.put("n1", Map.of("a", 1, "b", 2));
        outputs.put("n2", Map.of("b", 20, "c", 3));
        String out = (String) ReflectionTestUtils.invokeMethod(engine, "executeAggregatorNode", cfg, new LinkedHashMap<>(), outputs);
        JsonNode node = om.readTree(out);
        assertEquals("merge", node.get("mode").asText());
        assertEquals(2, node.get("count").asInt());
        // merged 写到 globalOutputs
        assertTrue(outputs.containsKey("aggregated"));
        Map<?, ?> agg = (Map<?, ?>) outputs.get("aggregated");
        assertEquals(1, agg.get("a"));
        assertEquals(20, agg.get("b"));
        assertEquals(3, agg.get("c"));
    }

    @Test
    void aggregator_firstMode_takesFirst() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("sources", "n1,n2");
        cfg.put("mode", "first");
        Map<String, Object> outputs = new LinkedHashMap<>();
        outputs.put("n1", "first-value");
        outputs.put("n2", "second-value");
        Map<String, Object> globalOutputs = new LinkedHashMap<>();
        globalOutputs.put("n1", "first-value");
        globalOutputs.put("n2", "second-value");
        ReflectionTestUtils.invokeMethod(engine, "executeAggregatorNode", cfg, new LinkedHashMap<>(), globalOutputs);
        assertEquals("first-value", globalOutputs.get("aggregated"));
    }

    @Test
    void aggregator_lastMode_takesLast() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("sources", "n1,n2");
        cfg.put("mode", "last");
        Map<String, Object> globalOutputs = new LinkedHashMap<>();
        globalOutputs.put("n1", "first");
        globalOutputs.put("n2", "last");
        ReflectionTestUtils.invokeMethod(engine, "executeAggregatorNode", cfg, new LinkedHashMap<>(), globalOutputs);
        assertEquals("last", globalOutputs.get("aggregated"));
    }

    @Test
    void aggregator_joinMode_concatenates() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("sources", "n1,n2,n3");
        cfg.put("mode", "join");
        cfg.put("separator", " | ");
        Map<String, Object> globalOutputs = new LinkedHashMap<>();
        globalOutputs.put("n1", "a");
        globalOutputs.put("n2", "b");
        globalOutputs.put("n3", "c");
        ReflectionTestUtils.invokeMethod(engine, "executeAggregatorNode", cfg, new LinkedHashMap<>(), globalOutputs);
        assertEquals("a | b | c", globalOutputs.get("aggregated"));
    }

    @Test
    void aggregator_sumMode_sumsNumbers() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("sources", "n1,n2,n3");
        cfg.put("mode", "sum");
        Map<String, Object> globalOutputs = new LinkedHashMap<>();
        globalOutputs.put("n1", 10);
        globalOutputs.put("n2", 20.5);
        globalOutputs.put("n3", "15");
        ReflectionTestUtils.invokeMethod(engine, "executeAggregatorNode", cfg, new LinkedHashMap<>(), globalOutputs);
        assertEquals(45.5, ((Number) globalOutputs.get("aggregated")).doubleValue(), 0.001);
    }

    @Test
    void aggregator_customOutputKey() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("sources", "n1");
        cfg.put("mode", "first");
        cfg.put("outputKey", "myCustomKey");
        Map<String, Object> globalOutputs = new LinkedHashMap<>();
        globalOutputs.put("n1", "v1");
        ReflectionTestUtils.invokeMethod(engine, "executeAggregatorNode", cfg, new LinkedHashMap<>(), globalOutputs);
        // outputKey 自定义时, 只写到自定义 key, 不再写 "aggregated" 默认
        assertEquals("v1", globalOutputs.get("myCustomKey"));
        assertEquals(null, globalOutputs.get("aggregated"));
    }

    @Test
    void aggregator_missingSources_throws() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        // sources 不填
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ReflectionTestUtils.invokeMethod(engine, "executeAggregatorNode", cfg, new LinkedHashMap<>(), new LinkedHashMap<>()));
        assertTrue(ex.getMessage().contains("sources"));
    }

    @Test
    void aggregator_emptyResults_safeReturn() throws Exception {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("sources", "n1");
        cfg.put("mode", "first");
        Map<String, Object> globalOutputs = new LinkedHashMap<>();
        // n1 不在 globalOutputs
        String out = (String) ReflectionTestUtils.invokeMethod(engine, "executeAggregatorNode", cfg, new LinkedHashMap<>(), globalOutputs);
        JsonNode node = om.readTree(out);
        assertEquals(0, node.get("count").asInt());
    }

    // ============ HITL 节点 ============

    @Test
    void hitl_missingAssignee_throws() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ReflectionTestUtils.invokeMethod(engine, "executeHitlNode", cfg, new LinkedHashMap<>(), new LinkedHashMap<>()));
        assertTrue(ex.getMessage().contains("assignee"));
    }

    @Test
    void hitl_currentlyFailsClosed_withGuidance() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("assignee", "admin");
        cfg.put("formTemplate", "approve?");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ReflectionTestUtils.invokeMethod(engine, "executeHitlNode", cfg, new LinkedHashMap<>(), new LinkedHashMap<>()));
        // 当前阶段明确失败关闭, 不允许模拟通过
        assertTrue(ex.getMessage().contains("LightWorkflowEngine")
                || ex.getMessage().contains("bpmn")
                || ex.getMessage().contains("workflow"));
    }
}
