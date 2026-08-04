package com.yutong.ai.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.ai.chat.dto.AiChatVO;
import com.yutong.ai.chat.service.llm.LlmProviderAdapter;
import com.yutong.ai.chat.service.llm.LlmProviderSelector;
import com.yutong.ai.chat.service.llm.LlmRequest;
import com.yutong.ai.chat.service.llm.LlmResponse;
import com.yutong.ai.gateway.domain.AiProvider;
import com.yutong.ai.gateway.mapper.AiConversationMapper;
import com.yutong.ai.gateway.mapper.AiMessageMapper;
import com.yutong.ai.gateway.service.AiAuditService;
import com.yutong.ai.gateway.service.AiToolRegistry;
import com.yutong.ai.governance.service.AiCostGovernanceService;
import com.yutong.ai.rag.service.RagRetrievalService;
import com.yutong.api.facade.LicenseService;
import com.yutong.auth.AuthAdapter;
import com.yutong.auth.DataScopeResolver;
import com.yutong.common.auth.DataScope;
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
    private AiCostGovernanceService aiCostGovernanceService;
    private DataScopeResolver dataScopeResolver;

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
        aiCostGovernanceService = mock(AiCostGovernanceService.class);
        dataScopeResolver = mock(DataScopeResolver.class);
        // 默认返回 ALL scope（admin），避免 pageConversations 相关用例受影响
        when(dataScopeResolver.resolve(anyString())).thenReturn(
                DataScope.all("test-user", "default", "ai:conversation"));

        service = new AiChatApplicationService(
                conversationMapper, messageMapper, toolRegistry, auditService,
                ragRetrievalService, platformMetrics, authAdapter, objectMapper,
                licenseService, providerSelector, aiCostGovernanceService, dataScopeResolver);
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
    @DisplayName("所有真实 LLM 供应商失败时降级为 mock")
    void fallsBackToMockWhenAllProvidersFail() {
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

        assertFalse(response.isError());
        assertEquals("mock-local", response.providerCode());
        assertEquals("mock-chat", response.modelCode());
        assertTrue(response.content().contains("收到您的问题"));

        verify(firstAdapter, times(1)).chat(any(LlmRequest.class));
        verify(secondAdapter, times(1)).chat(any(LlmRequest.class));
    }

    private LlmResponse invokeGenerateReply(String conversationId, String userMessage, String scenario,
                                            String providerCode, String modelCode,
                                            List<RagRetrievalService.RetrievalResult> retrievalResults,
                                            List<AiChatVO.Citation> citations) {
        return (LlmResponse) ReflectionTestUtils.invokeMethod(
                service, "generateReply",
                conversationId, userMessage, scenario, providerCode, modelCode, retrievalResults, citations);
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
}
