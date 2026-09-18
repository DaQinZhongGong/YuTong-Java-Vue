package com.yutong.ai.aiflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AIFlow Sub-Workflow 节点测试 (内联子 DAG, 无 LLM, 无 DB)。
 * 设计来源: ADR 0004 P2-B (Sub-Workflow)。
 *
 * 覆盖:
 * - 内联子流程执行 + 父输入继承 + 输出取回
 * - input 覆盖 + outputKey 取值
 * - 三选一缺失失败关闭
 * - 无解析器时 definitionId 失败关闭
 * - definitionId 环引用失败关闭 (stub 解析器)
 * - 嵌套深度熔断
 * - 子事件 ID 前缀
 */
class AiflowEngineSubflowTest {

    private final ObjectMapper om = new ObjectMapper();
    private final AiflowEngine engine = new AiflowEngine(om, null, null, null, null, null, null);

    private Map<String, Object> node(String id, String type, Map<String, Object> config) {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("id", id);
        n.put("type", type);
        n.put("config", config);
        return n;
    }

    private Map<String, Object> edge(String source, String target) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("source", source);
        e.put("target", target);
        return e;
    }

    private String dag(Object... parts) throws Exception {
        // parts 交替: nodes(List), edges(List)
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("nodes", parts[0]);
        d.put("edges", parts.length > 1 ? parts[1] : List.of());
        return om.writeValueAsString(d);
    }

    private Map<String, Object> templatingCfg(String template) {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("template", template);
        return cfg;
    }

    @Test
    @DisplayName("内联子流程: 继承父输入并返回子输出")
    void inlineSubflowInheritsInput() throws Exception {
        Map<String, Object> child = new LinkedHashMap<>();
        child.put("nodes", List.of(node("t1", "templating", templatingCfg("hi {{name}}!"))));
        child.put("edges", List.of());
        Map<String, Object> subCfg = new LinkedHashMap<>();
        subCfg.put("nodes", child.get("nodes"));
        subCfg.put("edges", child.get("edges"));

        String dagJson = dag(
                List.of(
                        node("n0", "input", Map.of()),
                        node("n1", "subflow", subCfg)),
                List.of(edge("n0", "n1")));

        Map<String, Object> outputs =
                engine.execute(dagJson, "{\"name\":\"bob\"}", null);

        assertTrue(outputs.get("n1").toString().contains("hi bob!"));
    }

    @Test
    @DisplayName("input 覆盖 + outputKey 取值")
    void inputOverrideAndOutputKey() throws Exception {
        Map<String, Object> subCfg = new LinkedHashMap<>();
        subCfg.put("nodes", List.of(
                node("t1", "templating", templatingCfg("a={{x}}")),
                node("t2", "templating", templatingCfg("b={{x}}"))));
        subCfg.put("edges", List.of());
        subCfg.put("input", Map.of("x", "42"));
        subCfg.put("outputKey", "t2");

        String dagJson = dag(List.of(node("n1", "subflow", subCfg)), List.of());

        Map<String, Object> outputs =
                engine.execute(dagJson, "{\"x\":\"0\"}", null);

        assertEquals("b=42", outputs.get("n1"));
    }

    @Test
    @DisplayName("三选一缺失失败关闭 (节点 FAILED, 不抛到外层)")
    void missingSourceFailsNode() throws Exception {
        String dagJson = dag(List.of(node("n1", "subflow", Map.of())), List.of());

        Map<String, Object> outputs =
                engine.execute(dagJson, "{}", null);

        // executeSingle 捕获异常落 FAILED, 不向外抛
        Object v = outputs.get("n1");
        assertTrue(v instanceof Map && ((Map<?, ?>) v).containsKey("error"));
    }

    @Test
    @DisplayName("无解析器时 definitionId 失败关闭")
    void definitionIdWithoutProviderFails() throws Exception {
        Map<String, Object> subCfg = new LinkedHashMap<>();
        subCfg.put("definitionId", "def-1");
        String dagJson = dag(List.of(node("n1", "subflow", subCfg)), List.of());

        Map<String, Object> outputs =
                engine.execute(dagJson, "{}", null);

        Object v = outputs.get("n1");
        assertTrue(v instanceof Map && String.valueOf(((Map<?, ?>) v).get("error")).contains("definitionId"));
    }

    @Test
    @DisplayName("definitionId 环引用失败关闭 (不无限递归)")
    void definitionCycleFails() throws Exception {
        // provider 对 def-A 返回含 subflow(def-A) 的 DAG
        Map<String, Object> subCfg = new LinkedHashMap<>();
        subCfg.put("definitionId", "def-A");
        String innerDag;
        {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("nodes", List.of(node("s1", "subflow", subCfg)));
            d.put("edges", List.of());
            innerDag = om.writeValueAsString(d);
        }
        final String childDag = innerDag;
        engine.setDefinitionProvider(definitionId -> {
            if (!"def-A".equals(definitionId)) throw new IllegalStateException("unexpected " + definitionId);
            return childDag;
        });

        String dagJson = dag(List.of(node("n1", "subflow", subCfg)), List.of());
        Map<String, Object> outputs =
                engine.execute(dagJson, "{}", null);

        // 内层 s1 环引用 FAILED → 外层 n1 传播为 FAILED, 原始"环引用"文本保留 (不吞错, 不无限递归)
        Object v = outputs.get("n1");
        assertTrue(v instanceof Map && String.valueOf(((Map<?, ?>) v).get("error")).contains("环引用"));
    }

    @Test
    @DisplayName("嵌套深度超过上限熔断 (4 层 subflow, 上限 3)")
    void depthLimitTrips() throws Exception {
        // 构造 4 层内联嵌套: s3 > s2 > s1 > s0 > leaf; s0 在 depth=3 触发熔断
        Map<String, Object> leaf = new LinkedHashMap<>();
        leaf.put("nodes", List.of(node("c", "code", Map.of("code", "'deep'"))));
        leaf.put("edges", List.of());
        Map<String, Object> level = leaf;
        for (int i = 0; i < 4; i++) {
            Map<String, Object> subCfg = new LinkedHashMap<>();
            subCfg.put("nodes", level.get("nodes"));
            subCfg.put("edges", level.get("edges"));
            Map<String, Object> next = new LinkedHashMap<>();
            next.put("nodes", List.of(node("s" + i, "subflow", subCfg)));
            next.put("edges", List.of());
            level = next;
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rootNodes = (List<Map<String, Object>>) level.get("nodes");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rootEdges = (List<Map<String, Object>>) level.get("edges");
        String dagJson = dag(rootNodes, rootEdges);

        Map<String, Object> outputs =
                engine.execute(dagJson, "{}", null);

        Object v = outputs.get("s3");
        assertTrue(v instanceof Map && String.valueOf(((Map<?, ?>) v).get("error")).contains("上限"));
    }

    @Test
    @DisplayName("子事件 ID 带父前缀")
    void childEventsPrefixed() throws Exception {
        Map<String, Object> subCfg = new LinkedHashMap<>();
        subCfg.put("nodes", List.of(node("t1", "templating", templatingCfg("hi!"))));
        subCfg.put("edges", List.of());
        String dagJson = dag(List.of(node("n1", "subflow", subCfg)), List.of());

        List<String> eventIds = new ArrayList<>();
        engine.execute(dagJson, "{}", ev -> eventIds.add(ev.nodeId()));

        assertTrue(eventIds.contains("n1"));
        assertTrue(eventIds.stream().anyMatch(id -> id.equals("n1/t1")));
    }

    @Test
    @DisplayName("别名 sub-flow / subworkflow 同样路由")
    void aliasesRoute() throws Exception {
        for (String alias : List.of("sub-flow", "subworkflow")) {
            Map<String, Object> subCfg = new LinkedHashMap<>();
            subCfg.put("nodes", List.of(node("t1", "templating", templatingCfg("ok"))));
            subCfg.put("edges", List.of());
            String dagJson = dag(List.of(node("n1", alias, subCfg)), List.of());

            Map<String, Object> outputs = engine.execute(dagJson, "{}", null);
            assertTrue(outputs.get("n1").toString().contains("ok"), alias);
        }
    }

    @Test
    @DisplayName("outputKey 不存在失败关闭")
    void missingOutputKeyFails() throws Exception {
        Map<String, Object> subCfg = new LinkedHashMap<>();
        subCfg.put("nodes", List.of(node("t1", "templating", templatingCfg("ok"))));
        subCfg.put("edges", List.of());
        subCfg.put("outputKey", "nope");
        String dagJson = dag(List.of(node("n1", "subflow", subCfg)), List.of());

        Map<String, Object> outputs = engine.execute(dagJson, "{}", null);
        Object v = outputs.get("n1");
        assertTrue(v instanceof Map && String.valueOf(((Map<?, ?>) v).get("error")).contains("输出键"));
    }
}
