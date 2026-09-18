package com.yutong.ai.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.ai.agent.domain.AiAgent;
import com.yutong.ai.agent.mapper.AiAgentRunMapper;
import com.yutong.ai.chat.service.llm.LlmProviderAdapter;
import com.yutong.ai.chat.service.llm.LlmProviderSelector;
import com.yutong.ai.chat.service.llm.LlmResponse;
import com.yutong.ai.search.WebSearchClient;
import com.yutong.ai.gateway.domain.AiProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * SmartSupervisorAgent 单元测试。
 */
class SmartSupervisorAgentTest {

    private AiAgentRunMapper runMapper;
    private LlmProviderSelector providerSelector;
    private JdbcTemplate jdbcTemplate;
    private SmartSupervisorAgent agent;
    private final List<ReActEngine.ReActStep> steps = new ArrayList<>();

    // 预构建的 mock 对象 (避免嵌套 stubbing)
    private LlmProviderAdapter mockAdapter;
    private AiProvider mockProvider;

    @BeforeEach
    void setUp() {
        runMapper = mock(AiAgentRunMapper.class);
        providerSelector = mock(LlmProviderSelector.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        agent = new SmartSupervisorAgent(runMapper, providerSelector, new ObjectMapper(), jdbcTemplate);
        steps.clear();

        mockAdapter = mock(LlmProviderAdapter.class);
        mockProvider = mock(AiProvider.class);
    }

    private AiAgent buildSupervisor() {
        AiAgent a = new AiAgent();
        a.setId("agent-sup-1");
        a.setAgentCode("supervisor");
        a.setAgentType("supervisor");
        a.setTenantId("default");
        a.setStatus("PUBLISHED");
        return a;
    }

    private void stubProviderWithContent(String content) {
        var resp = new LlmResponse("test", "test-model", content, 10, 10, 100, "stop", null);
        when(mockAdapter.chat(any())).thenReturn(resp);
        var runtime = new LlmProviderSelector.ProviderRuntime(mockProvider, mockAdapter, "test-model");
        when(providerSelector.selectEnabledProvider()).thenReturn(Optional.of(runtime));
    }

    @Test
    void routeAndExecute_chitchat_whenNoProvider() {
        when(providerSelector.selectEnabledProvider()).thenReturn(Optional.empty());
        var result = agent.routeAndExecute(buildSupervisor(), "你好", "conv-1", steps::add);
        assertEquals(SmartSupervisorAgent.TYPE_CHITCHAT, result.route());
    }

    @Test
    void routeAndExecute_webSearch_noClient_failsClosed() {
        stubProviderWithContent("{\"route\":\"web_search\",\"reason\":\"需要联网\"}");
        var result = agent.routeAndExecute(buildSupervisor(), "今天天气", "conv-1", steps::add);
        assertEquals(SmartSupervisorAgent.TYPE_WEB_SEARCH, result.route());
        assertFalse(result.success());
        assertTrue(result.error().contains("未配置"));
    }

    @Test
    void routeAndExecute_webSearch_withClient_success() throws Exception {
        stubProviderWithContent("{\"route\":\"web_search\",\"reason\":\"需要联网\"}");
        WebSearchClient mockClient = mock(WebSearchClient.class);
        var searchResp = new WebSearchClient.SearchResponse("q", "r1", 1,
                List.of(new WebSearchClient.SearchResult("标题", "内容", "https://x.com", "媒体", "2026-01-01")));
        when(mockClient.search(anyString(), anyInt())).thenReturn(searchResp);
        agent.setSearchClient(mockClient);

        var result = agent.routeAndExecute(buildSupervisor(), "AI 新闻", "conv-1", steps::add);
        assertEquals(SmartSupervisorAgent.TYPE_WEB_SEARCH, result.route());
        assertTrue(result.success());
        assertTrue(result.answer().contains("标题"));
    }

    @Test
    void routeAndExecute_sqlQuery_nonSelect_rejected() {
        stubProviderWithContent("{\"route\":\"sql_query\",\"reason\":\"查数据\"}");
        // 第二次 LLM 调用返回非 SELECT SQL
        var resp1 = new LlmResponse("test", "test-model", "{\"route\":\"sql_query\",\"reason\":\"查数据\"}", 10, 10, 100, "stop", null);
        var resp2 = new LlmResponse("test", "test-model", "DROP TABLE users", 10, 10, 100, "stop", null);
        when(mockAdapter.chat(any())).thenReturn(resp1).thenReturn(resp2);
        var runtime = new LlmProviderSelector.ProviderRuntime(mockProvider, mockAdapter, "test-model");
        when(providerSelector.selectEnabledProvider()).thenReturn(Optional.of(runtime));

        var result = agent.routeAndExecute(buildSupervisor(), "删掉用户表", "conv-1", steps::add);
        assertEquals(SmartSupervisorAgent.TYPE_SQL_QUERY, result.route());
        assertFalse(result.success());
    }

    @Test
    void routeAndExecute_sqlQuery_select_success() {
        var resp1 = new LlmResponse("test", "test-model", "{\"route\":\"sql_query\",\"reason\":\"查数据\"}", 10, 10, 100, "stop", null);
        var resp2 = new LlmResponse("test", "test-model", "SELECT count(*) as cnt FROM sys_user", 10, 10, 100, "stop", null);
        when(mockAdapter.chat(any())).thenReturn(resp1).thenReturn(resp2);
        var runtime = new LlmProviderSelector.ProviderRuntime(mockProvider, mockAdapter, "test-model");
        when(providerSelector.selectEnabledProvider()).thenReturn(Optional.of(runtime));
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(Map.of("cnt", 42)));

        var result = agent.routeAndExecute(buildSupervisor(), "有多少用户", "conv-1", steps::add);
        assertEquals(SmartSupervisorAgent.TYPE_SQL_QUERY, result.route());
        assertTrue(result.success());
        assertTrue(result.answer().contains("42"));
    }

    @Test
    void decideRoute_invalidJson_fallback() {
        stubProviderWithContent("这不是JSON");
        var decision = agent.decideRoute("你好");
        assertEquals(SmartSupervisorAgent.TYPE_CHITCHAT, decision.route());
    }

    @Test
    void decideRoute_unknownRoute_fallback() {
        stubProviderWithContent("{\"route\":\"hack\",\"reason\":\"x\"}");
        var decision = agent.decideRoute("你好");
        assertEquals(SmartSupervisorAgent.TYPE_CHITCHAT, decision.route());
    }
}
