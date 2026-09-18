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
 * AiflowEngine Loop 节点测试
 * 设计来源: ADR 0004 P2-B 批次 1-C 循环节点
 *
 * 覆盖:
 *  - 正常遍历 + body 模板插值
 *  - maxIter 截断 (数组 100 项但 maxIter=3, 只跑 3 轮)
 *  - maxIter 边界 (0/负数/-1 走默认值 10, 200 走上限 100)
 *  - items 路径解析失败抛错
 *  - 非数组/非集合类型抛错
 *  - 输出 JSON 结构 (iterations / items / results / joined)
 */
class AiflowEngineLoopTest {

    private final ObjectMapper om = new ObjectMapper();
    private final AiflowEngine engine = new AiflowEngine(om, null, null, null, null, null, null);

    @Test
    void loop_shouldIterateAndInterpolateBody() throws Exception {
        // 入参: { "list": [{ "name": "a"}, { "name": "b"}, { "name": "c"}] }
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("list", List.of(
                Map.of("name", "a", "score", 10),
                Map.of("name", "b", "score", 20),
                Map.of("name", "c", "score", 30)
        ));
        // 节点配置: items=list, body="index={{loop_index}} name={{loop_item_name}} score={{loop_item_score}}"
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("items", "list");
        cfg.put("body", "index={{loop_index}} name={{loop_item_name}} score={{loop_item_score}}");
        cfg.put("maxIter", "10");
        cfg.put("join", " | ");

        String out = (String) ReflectionTestUtils.invokeMethod(engine, "executeLoopNode", cfg, input, new LinkedHashMap<>());
        JsonNode node = om.readTree(out);

        assertEquals(3, node.get("iterations").asInt(), "应跑满 3 轮");
        assertEquals(3, node.get("items").size(), "items 数组大小 = 3");
        assertEquals(3, node.get("results").size(), "results 数组大小 = 3");
        assertEquals(
                "index=0 name=a score=10|index=1 name=b score=20|index=2 name=c score=30",
                node.get("joined").asText(),
                "join 字符串会被 firstCfg trim 掉首尾空格(与 node.config 中其他字符串 token 一致), 实际生效为 |"
        );
        assertTrue(node.get("results").get(0).asText().contains("name=a"));
    }

    @Test
    void loop_shouldClampByMaxIter() throws Exception {
        // 数组 5 项, maxIter=2 -> 只跑 2 轮
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("list", List.of("x", "y", "z", "w", "v"));
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("items", "list");
        cfg.put("body", "[{{loop_index}}:{{loop_item}}]");
        cfg.put("maxIter", "2");
        cfg.put("join", "|");

        String out = (String) ReflectionTestUtils.invokeMethod(engine, "executeLoopNode", cfg, input, new LinkedHashMap<>());
        JsonNode node = om.readTree(out);
        assertEquals(2, node.get("iterations").asInt(), "maxIter=2 截断为 2 轮");
        assertEquals(2, node.get("results").size());
        assertEquals("[0:x]|[1:y]", node.get("joined").asText(), "join= 已 trim 为 |");
    }

    @Test
    void loop_shouldClampMaxIterBounds() throws Exception {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("list", List.of(1, 2, 3));

        // maxIter=0 -> 走默认 10
        Map<String, Object> cfg0 = new LinkedHashMap<>();
        cfg0.put("items", "list");
        cfg0.put("maxIter", "0");
        String out0 = (String) ReflectionTestUtils.invokeMethod(engine, "executeLoopNode", cfg0, input, new LinkedHashMap<>());
        assertEquals(3, om.readTree(out0).get("iterations").asInt(), "maxIter=0 走默认 10, 数组 3 项跑满 3 轮");

        // maxIter=-5 -> 走默认 10
        Map<String, Object> cfgNeg = new LinkedHashMap<>();
        cfgNeg.put("items", "list");
        cfgNeg.put("maxIter", "-5");
        String outNeg = (String) ReflectionTestUtils.invokeMethod(engine, "executeLoopNode", cfgNeg, input, new LinkedHashMap<>());
        assertEquals(3, om.readTree(outNeg).get("iterations").asInt());

        // maxIter=200 -> 上限 100
        Map<String, Object> cfgHuge = new LinkedHashMap<>();
        cfgHuge.put("items", "list");
        cfgHuge.put("maxIter", "200");
        String outHuge = (String) ReflectionTestUtils.invokeMethod(engine, "executeLoopNode", cfgHuge, input, new LinkedHashMap<>());
        assertEquals(3, om.readTree(outHuge).get("iterations").asInt(), "maxIter=200 上限 100, 数组 3 项跑满 3 轮");
    }

    @Test
    void loop_shouldRejectMissingItemsPath() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("body", "anything");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ReflectionTestUtils.invokeMethod(engine, "executeLoopNode", cfg, new LinkedHashMap<>(), new LinkedHashMap<>()));
        assertTrue(ex.getMessage().contains("items"));
    }

    @Test
    void loop_shouldRejectNonArrayItems() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("scalar", "just-a-string");
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("items", "scalar");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ReflectionTestUtils.invokeMethod(engine, "executeLoopNode", cfg, input, new LinkedHashMap<>()));
        assertTrue(ex.getMessage().contains("不是数组"));
    }

    @Test
    void loop_shouldResolveItemsFromGlobalOutputs() throws Exception {
        // 模拟上游节点已经产出 list 输出, 通过 path 引用 n1.items
        Map<String, Object> input = new LinkedHashMap<>();
        Map<String, Object> globalOutputs = new LinkedHashMap<>();
        globalOutputs.put("n1", Map.of("items", List.of("alpha", "beta", "gamma")));

        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("items", "items");  // resolveField 在 n1 Map 内查 items
        cfg.put("body", "step={{loop_index}} val={{loop_item}}");

        String out = (String) ReflectionTestUtils.invokeMethod(engine, "executeLoopNode", cfg, input, globalOutputs);
        JsonNode node = om.readTree(out);
        assertEquals(3, node.get("iterations").asInt());
        assertNotNull(node.get("joined"));
    }

    @Test
    void loop_withoutBodyTemplate_shouldSerializeItem() throws Exception {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("list", List.of(1, 2, 3));
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("items", "list");
        // body 不配置 -> 默认 JSON 序列化 item
        String out = (String) ReflectionTestUtils.invokeMethod(engine, "executeLoopNode", cfg, input, new LinkedHashMap<>());
        JsonNode node = om.readTree(out);
        assertEquals(3, node.get("iterations").asInt());
        assertEquals("1", node.get("results").get(0).asText());
        assertEquals("3", node.get("results").get(2).asText());
    }
}
