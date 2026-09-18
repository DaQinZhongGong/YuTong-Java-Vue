package com.yutong.ai.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.ai.agent.domain.AiAgent;
import com.yutong.ai.agent.service.AgentService;
import com.yutong.ai.chat.dto.AiChatVO;
import com.yutong.ai.chat.service.llm.LlmProviderAdapter;
import com.yutong.ai.chat.service.llm.AiModelPriceResolver;
import com.yutong.ai.chat.service.llm.LlmProviderSelector;
import com.yutong.ai.chat.service.llm.LlmRequest;
import com.yutong.ai.chat.service.llm.LlmResponse;
import com.yutong.ai.gateway.domain.AiProvider;
import com.yutong.ai.gateway.mapper.AiConversationMapper;
import com.yutong.ai.gateway.mapper.AiMessageMapper;import com.yutong.ai.gateway.service.AiAuditService;
import com.yutong.ai.gateway.service.AiProviderRegistry;
import com.yutong.ai.gateway.service.AiToolRegistry;
import com.yutong.ai.governance.service.AiCostGovernanceService;
import com.yutong.ai.rag.service.RagRetrievalService;
import com.yutong.api.facade.LicenseService;
import com.yutong.auth.AuthAdapter;
import com.yutong.auth.DataScopeResolver;
import com.yutong.common.auth.DataScope;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AI 对话应用服务单元测试。
 */
class AiChatApplicationServiceTest {

    private AiChatApplicationService service;

    private AiConversationMapper conversationMapper;
    private AiMessageMapper messageMapper;
    private AiToolRegistry toolRegistry;
    private AiAuditService auditService;
    private RagRetrievalService ragRetrievalService;
    private com.yutong.common.metrics.PlatformMetrics platformMetrics;
    private AuthAdapter authAdapter;
    private ObjectMapper objectMapper;
    private LicenseService licenseService;
    private LlmProviderSelector providerSelector;
    private AiProviderRegistry providerRegistry;
    private AiCostGovernanceService aiCostGovernanceService;
    private AiModelPriceResolver aiModelPriceResolver;
    private DataScopeResolver dataScopeResolver;
    private AgentService agentService;

    @BeforeEach
    void setUp() {
        conversationMapper = mock(AiConversationMapper.class);
        messageMapper = mock(AiMessageMapper.class);
        toolRegistry = mock(AiToolRegistry.class);
        auditService = mock(AiAuditService.class);
        ragRetrievalService = mock(RagRetrievalService.class);
        platformMetrics = mock(com.yutong.common.metrics.PlatformMetrics.class);
        authAdapter = mock(AuthAdapter.class);
        objectMapper = new ObjectMapper();
        licenseService = mock(LicenseService.class);
        providerSelector = mock(LlmProviderSelector.class);
        providerRegistry = mock(AiProviderRegistry.class);
        aiCostGovernanceService = mock(AiCostGovernanceService.class);
        aiModelPriceResolver = mock(AiModelPriceResolver.class);
        // 价格解析默认返回 ZERO (无配置), 避免影响既有 test 行为
        when(aiModelPriceResolver.resolvePrice(any(), any())).thenReturn(AiModelPriceResolver.Price.ZERO);
        dataScopeResolver = mock(DataScopeResolver.class);
        com.yutong.ai.trace.service.AiTraceService aiTraceService = mock(com.yutong.ai.trace.service.AiTraceService.class);
        com.yutong.ai.memory.service.AiMemoryService aiMemoryService = mock(com.yutong.ai.memory.service.AiMemoryService.class);
        agentService = mock(AgentService.class);
        // 默认返回 ALL scope（admin），避免 pageConversations 相关用例受影响
        when(dataScopeResolver.resolve(anyString())).thenReturn(
                DataScope.all("test-user", "default", "ai:conversation"));

        service = new AiChatApplicationService(
                conversationMapper, messageMapper, toolRegistry, auditService,
                ragRetrievalService, platformMetrics, authAdapter, objectMapper,
                licenseService, providerSelector, providerRegistry, aiCostGovernanceService, aiModelPriceResolver, dataScopeResolver, aiTraceService, aiMemoryService, agentService);
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    @Test
    @DisplayName("第一个真实 LLM 供应商失败时尝试第二个供应商")
    void fallsBackToNextProviderWhenFirstFails() {
        AiProvider firstProvider = createProvider("first", "first-model");
        LlmProviderAdapter firstAdapter = mock(LlmProviderAdapter.class);
        LlmProviderSelector.ProviderRuntime firstRuntime =
                new LlmProviderSelector.ProviderRuntime(firstProvider, firstAdapter, "first-model");

        AiProvider secondProvider = createProvider("second", "second-model");
        LlmProviderAdapter secondAdapter = mock(LlmProviderAdapter.class);
        LlmProviderSelector.ProviderRuntime secondRuntime =
                new LlmProviderSelector.ProviderRuntime(secondProvider, secondAdapter, "second-model");

        when(providerSelector.selectProvider(null, null)).thenReturn(java.util.Optional.of(firstRuntime));
        when(providerSelector.selectEnabledProviders()).thenReturn(List.of(firstRuntime, secondRuntime));
        when(firstAdapter.chat(any(LlmRequest.class))).thenReturn(
                LlmResponse.error("first", "first-model", new RuntimeException("first failed")));
        when(secondAdapter.chat(any(LlmRequest.class))).thenReturn(
                new LlmResponse("second", "second-model", "second-reply", 1, 2, 30, "stop", null));

        LlmResponse response = invokeGenerateReply("conv-1", "hello", "CHAT", null, null, List.of(), List.of());

        assertFalse(response.isError());
        assertEquals("second", response.providerCode());
        assertEquals("second-model", response.modelCode());
        assertEquals("second-reply", response.content());

        verify(firstAdapter, times(1)).chat(any(LlmRequest.class));
        verify(secondAdapter, times(1)).chat(any(LlmRequest.class));
    }

    @Test
    @DisplayName("所有真实 LLM 供应商失败时返回 error，不降级 mock")
    void returnsErrorWhenAllProvidersFail() {
        AiProvider firstProvider = createProvider("first", "first-model");
        LlmProviderAdapter firstAdapter = mock(LlmProviderAdapter.class);
        LlmProviderSelector.ProviderRuntime firstRuntime =
                new LlmProviderSelector.ProviderRuntime(firstProvider, firstAdapter, "first-model");

        AiProvider secondProvider = createProvider("second", "second-model");
        LlmProviderAdapter secondAdapter = mock(LlmProviderAdapter.class);
        LlmProviderSelector.ProviderRuntime secondRuntime =
                new LlmProviderSelector.ProviderRuntime(secondProvider, secondAdapter, "second-model");

        when(providerSelector.selectProvider(null, null)).thenReturn(java.util.Optional.of(firstRuntime));
        when(providerSelector.selectEnabledProviders()).thenReturn(List.of(firstRuntime, secondRuntime));
        when(firstAdapter.chat(any(LlmRequest.class))).thenReturn(
                LlmResponse.error("first", "first-model", new RuntimeException("first failed")));
        when(secondAdapter.chat(any(LlmRequest.class))).thenReturn(
                LlmResponse.error("second", "second-model", new RuntimeException("second failed")));

        LlmResponse response = invokeGenerateReply("conv-1", "hello", "CHAT", null, null, List.of(), List.of());

        assertTrue(response.isError());
        assertNotEquals("mock-local", response.providerCode());

        verify(firstAdapter, times(1)).chat(any(LlmRequest.class));
        verify(secondAdapter, times(1)).chat(any(LlmRequest.class));
    }

    private LlmResponse invokeGenerateReply(String conversationId, String userMessage, String scenario,
                                            String providerCode, String modelCode,
                                            List<RagRetrievalService.RetrievalResult> retrievalResults,
                                            List<AiChatVO.Citation> citations) {
        return invokeGenerateReply(conversationId, userMessage, scenario, providerCode, modelCode, null,
                retrievalResults, citations);
    }

    private LlmResponse invokeGenerateReply(String conversationId, String userMessage, String scenario,
                                            String providerCode, String modelCode, String agentId,
                                            List<RagRetrievalService.RetrievalResult> retrievalResults,
                                            List<AiChatVO.Citation> citations) {
        return (LlmResponse) ReflectionTestUtils.invokeMethod(
                service, "generateReply",
                conversationId, userMessage, scenario, providerCode, modelCode, null, null, null, agentId,
                retrievalResults, citations);
    }

    private AiProvider createProvider(String providerCode, String modelCode) {
        AiProvider provider = new AiProvider();
        provider.setProviderCode(providerCode);
        provider.setEndpoint("https://api." + providerCode + ".com/v1");
        provider.setModelListJson("[{\"code\":\"" + modelCode + "\"}]");
        provider.setProtocol("OPENAI_COMPATIBLE");
        provider.setEnabled(true);
        provider.setTimeoutMs(30000);
        return provider;
    }

    // ==================== agentId 路由 (P2-G AgentSelect 闭环) ====================

    private AiAgent createAgent(String id, String status) {
        AiAgent agent = new AiAgent();
        agent.setId(id);
        agent.setAgentCode("CODE_" + id);
        agent.setAgentName("客服助手" + id);
        agent.setSystemPrompt("你是客服助手，回答简洁。");
        agent.setStatus(status);
        return agent;
    }

    @Test
    @DisplayName("agentId 为空时不解析 Agent (默认助手)")
    void agentBlockNullWhenNoAgentId() {
        assertNull(service.resolveAgentPromptBlock(null));
        assertNull(service.resolveAgentPromptBlock("  "));
        verifyNoInteractions(agentService);
    }

    @Test
    @DisplayName("已发布 Agent 返回身份+systemPrompt 块")
    void agentBlockForPublishedAgent() {
        AiAgent agent = createAgent("a1", AiAgent.STATUS_PUBLISHED);
        when(agentService.getAgent("a1")).thenReturn(agent);

        String block = service.resolveAgentPromptBlock("a1");

        assertNotNull(block);
        assertTrue(block.contains("客服助手a1"));
        assertTrue(block.contains("CODE_a1"));
        assertTrue(block.contains("你是客服助手"));
    }

    @Test
    @DisplayName("草稿 Agent 拒绝 (失败关闭)")
    void agentBlockRejectsDraftAgent() {
        AiAgent agent = createAgent("a2", AiAgent.STATUS_DRAFT);
        when(agentService.getAgent("a2")).thenReturn(agent);

        assertThrows(BusinessException.class, () -> service.resolveAgentPromptBlock("a2"));
    }

    @Test
    @DisplayName("不存在的 Agent 抛 404")
    void agentBlockMissingAgentThrows404() {
        when(agentService.getAgent("nope"))
                .thenThrow(new ResourceNotFoundException("Agent 不存在: nope"));

        assertThrows(ResourceNotFoundException.class, () -> service.resolveAgentPromptBlock("nope"));
    }

    @Test
    @DisplayName("generateReply 透传 agentId: system 消息首部为 Agent 块")
    void generateReplyInjectsAgentPrompt() {
        AiAgent agent = createAgent("a3", AiAgent.STATUS_PUBLISHED);
        when(agentService.getAgent("a3")).thenReturn(agent);

        AiProvider provider = createProvider("p1", "m1");
        LlmProviderAdapter adapter = mock(LlmProviderAdapter.class);
        LlmProviderSelector.ProviderRuntime runtime =
                new LlmProviderSelector.ProviderRuntime(provider, adapter, "m1");
        when(providerSelector.selectProvider("p1", "m1")).thenReturn(java.util.Optional.of(runtime));
        org.mockito.ArgumentCaptor<LlmRequest> captor = org.mockito.ArgumentCaptor.forClass(LlmRequest.class);
        when(adapter.chat(captor.capture())).thenReturn(
                new LlmResponse("p1", "m1", "hi", 1, 1, 10, "stop", null));

        LlmResponse response = invokeGenerateReply("conv-1", "hello", "CHAT", "p1", "m1", "a3",
                List.of(), List.of());

        assertFalse(response.isError());
        LlmRequest sent = captor.getValue();
        assertNotNull(sent);
        assertFalse(sent.messages().isEmpty());
        com.yutong.ai.chat.service.llm.LlmMessage first = sent.messages().get(0);
        assertEquals("system", first.role());
        String firstContent = String.valueOf(first.content());
        assertTrue(firstContent.startsWith("当前 Agent: 客服助手a3"));
        assertTrue(firstContent.contains("你是 YuTong 平台的 AI 助手"));
    }

    @Test
    @DisplayName("generateReply 无 agentId 时 system 消息为默认助手 (行为不变)")
    void generateReplyWithoutAgentKeepsDefaultPrompt() {
        AiProvider provider = createProvider("p1", "m1");
        LlmProviderAdapter adapter = mock(LlmProviderAdapter.class);
        LlmProviderSelector.ProviderRuntime runtime =
                new LlmProviderSelector.ProviderRuntime(provider, adapter, "m1");
        when(providerSelector.selectProvider("p1", "m1")).thenReturn(java.util.Optional.of(runtime));
        org.mockito.ArgumentCaptor<LlmRequest> captor = org.mockito.ArgumentCaptor.forClass(LlmRequest.class);
        when(adapter.chat(captor.capture())).thenReturn(
                new LlmResponse("p1", "m1", "hi", 1, 1, 10, "stop", null));

        LlmResponse response = invokeGenerateReply("conv-1", "hello", "CHAT", "p1", "m1",
                List.of(), List.of());

        assertFalse(response.isError());
        com.yutong.ai.chat.service.llm.LlmMessage first = captor.getValue().messages().get(0);
        assertTrue(String.valueOf(first.content()).startsWith("你是 YuTong 平台的 AI 助手"));
    }

    // ==================== P2-C 反馈与置顶 ====================

    private com.yutong.ai.gateway.domain.AiMessage assistantMessage(String id) {
        com.yutong.ai.gateway.domain.AiMessage m = new com.yutong.ai.gateway.domain.AiMessage();
        m.setId(id);
        m.setRole(com.yutong.ai.gateway.domain.AiMessage.ROLE_ASSISTANT);
        return m;
    }

    @Test
    @DisplayName("assistant 消息可点赞/点踩 (大小写归一)")
    void feedbackLikeDislike() {
        when(messageMapper.selectById("m1")).thenReturn(assistantMessage("m1"));

        com.yutong.ai.gateway.domain.AiMessage liked = service.feedbackMessage("m1", "like");
        assertEquals("LIKE", liked.getFeedback());
        when(messageMapper.selectById("m1")).thenReturn(assistantMessage("m1"));
        com.yutong.ai.gateway.domain.AiMessage disliked = service.feedbackMessage("m1", "DISLIKE");
        assertEquals("DISLIKE", disliked.getFeedback());
        verify(messageMapper, times(2)).updateById(any(com.yutong.ai.gateway.domain.AiMessage.class));
    }

    @Test
    @DisplayName("空值清除评价")
    void feedbackClear() {
        com.yutong.ai.gateway.domain.AiMessage m = assistantMessage("m1");
        m.setFeedback("LIKE");
        when(messageMapper.selectById("m1")).thenReturn(m);

        com.yutong.ai.gateway.domain.AiMessage cleared = service.feedbackMessage("m1", null);
        assertNull(cleared.getFeedback());
    }

    @Test
    @DisplayName("非法反馈值拒绝")
    void feedbackInvalidRejected() {
        when(messageMapper.selectById("m1")).thenReturn(assistantMessage("m1"));

        assertThrows(BusinessException.class, () -> service.feedbackMessage("m1", "LOVE"));
    }

    @Test
    @DisplayName("user 消息不可评价")
    void feedbackUserMessageRejected() {
        com.yutong.ai.gateway.domain.AiMessage m = new com.yutong.ai.gateway.domain.AiMessage();
        m.setId("u1");
        m.setRole(com.yutong.ai.gateway.domain.AiMessage.ROLE_USER);
        when(messageMapper.selectById("u1")).thenReturn(m);

        assertThrows(BusinessException.class, () -> service.feedbackMessage("u1", "LIKE"));
    }

    @Test
    @DisplayName("不存在的消息抛 404")
    void feedbackMissingThrows404() {
        when(messageMapper.selectById("nope")).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> service.feedbackMessage("nope", "LIKE"));
    }

    @Test
    @DisplayName("会话置顶/取消置顶")
    void pinUnpinConversation() {
        com.yutong.ai.gateway.domain.AiConversation c = new com.yutong.ai.gateway.domain.AiConversation();
        c.setId("c1");
        when(conversationMapper.selectById("c1")).thenReturn(c);

        assertTrue(service.pinConversation("c1", true).getPinned());
        assertFalse(service.pinConversation("c1", false).getPinned());
        verify(conversationMapper, times(2)).updateById(any(com.yutong.ai.gateway.domain.AiConversation.class));
    }

    @Test
    @DisplayName("不存在的会话置顶抛 404")
    void pinMissingThrows404() {
        when(conversationMapper.selectById("nope")).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> service.pinConversation("nope", true));
    }

    // ==================== P2-C 分支链 (V052) ====================

    @Test
    @DisplayName("空父链返回 null (链首)")
    void parentNullWhenBlank() {
        assertNull(service.resolveParentMessageId("c1", null));
        assertNull(service.resolveParentMessageId("c1", "  "));
        verifyNoInteractions(messageMapper);
    }

    @Test
    @DisplayName("同会话父消息通过")
    void parentSameConversationPasses() {
        com.yutong.ai.gateway.domain.AiMessage parent = new com.yutong.ai.gateway.domain.AiMessage();
        parent.setId("m0");
        parent.setConversationId("c1");
        when(messageMapper.selectById("m0")).thenReturn(parent);

        assertEquals("m0", service.resolveParentMessageId("c1", "m0"));
    }

    @Test
    @DisplayName("不存在的父消息抛 404")
    void parentMissingThrows404() {
        when(messageMapper.selectById("nope")).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> service.resolveParentMessageId("c1", "nope"));
    }

    @Test
    @DisplayName("跨会话父消息抛 400")
    void parentCrossConversationRejected() {
        com.yutong.ai.gateway.domain.AiMessage parent = new com.yutong.ai.gateway.domain.AiMessage();
        parent.setId("m0");
        parent.setConversationId("other");
        when(messageMapper.selectById("m0")).thenReturn(parent);

        assertThrows(BusinessException.class, () -> service.resolveParentMessageId("c1", "m0"));
    }

    @Test
    @DisplayName("saveMessage 落库父链")
    void saveMessagePersistsParent() {
        org.mockito.ArgumentCaptor<com.yutong.ai.gateway.domain.AiMessage> captor =
                org.mockito.ArgumentCaptor.forClass(com.yutong.ai.gateway.domain.AiMessage.class);
        when(messageMapper.insert(captor.capture())).thenReturn(1);

        Object result = ReflectionTestUtils.invokeMethod(
                service, "saveMessage", "c1", "user", "hi", null, null, null, null, "m0");

        assertNotNull(result);
        assertEquals("m0", captor.getValue().getParentMessageId());
        assertEquals("c1", captor.getValue().getConversationId());
    }
}
