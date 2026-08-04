package com.yutong.ai.chat.service.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.ai.gateway.domain.AiProvider;
import com.yutong.ai.gateway.mapper.AiProviderMapper;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.auth.DataScopeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * LLM 供应商选择器单元测试。
 * 设计来源: P6-02 免费 LLM 供应商集成
 */
class LlmProviderSelectorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AiProviderMapper providerMapper;
    private LlmProviderSelector selector;

    @BeforeEach
    void setUp() {
        providerMapper = Mockito.mock(AiProviderMapper.class);
        selector = new LlmProviderSelector(providerMapper, objectMapper);
        CurrentUserContext.set("user-1", "default", "admin", "dept-1", "dept-1", DataScopeType.ALL);
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    @DisplayName("无可用供应商时返回空")
    void selectReturnsEmptyWhenNoProviders() {
        when(providerMapper.selectList(any())).thenReturn(List.of());
        Optional<LlmProviderSelector.ProviderRuntime> result = selector.selectEnabledProvider();
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("跳过 mock 供应商并返回优先级最高的真实供应商")
    void selectSkipsMockAndReturnsHighestPriority() {
        AiProvider mockProvider = createProvider("mock-local", "Mock", true, 1,
                "[{\"code\":\"mock-chat\"}]");
        AiProvider siliconflow = createProvider("siliconflow", "SiliconFlow", true, 10,
                "[{\"code\":\"qwen\"},{\"code\":\"deepseek\"}]");
        when(providerMapper.selectList(any())).thenReturn(List.of(mockProvider, siliconflow));

        Optional<LlmProviderSelector.ProviderRuntime> result = selector.selectEnabledProvider();

        assertTrue(result.isPresent());
        assertEquals("siliconflow", result.get().provider().getProviderCode());
        assertEquals("qwen", result.get().defaultModel());
        assertNotNull(result.get().adapter());
    }

    @Test
    @DisplayName("已禁用供应商被跳过")
    void selectSkipsDisabledProviders() {
        AiProvider disabled = createProvider("openrouter", "OpenRouter", false, 5,
                "[{\"code\":\"gpt-4o-mini\"}]");
        when(providerMapper.selectList(any())).thenReturn(List.of(disabled));

        Optional<LlmProviderSelector.ProviderRuntime> result = selector.selectEnabledProvider();
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("apiKeyRef JSON 解析正确")
    void resolveApiKeyFromJson() {
        assertEquals("sk-123", selector.resolveApiKey("{\"apiKey\":\"sk-123\"}"));
        assertEquals("sk-456", selector.resolveApiKey("{\"api_key\":\"sk-456\"}"));
        assertEquals("tk-789", selector.resolveApiKey("{\"token\":\"tk-789\"}"));
    }

    @Test
    @DisplayName("apiKeyRef 明文直接返回")
    void resolveApiKeyPlaintext() {
        assertEquals("sk-plain", selector.resolveApiKey("sk-plain"));
    }

    @Test
    @DisplayName("modelListJson 解析默认模型")
    void extractDefaultModelFromJson() {
        assertEquals("glm-4-flash", selector.extractDefaultModel("[{\"code\":\"glm-4-flash\"}]"));
        assertNull(selector.extractDefaultModel("[]"));
        assertNull(selector.extractDefaultModel(null));
    }

    @Test
    @DisplayName("listAvailableModels 返回所有启用供应商的模型")
    void listAvailableModelsReturnsOptions() {
        AiProvider zhipu = createProvider("zhipu", "智谱", true, 5,
                "[{\"code\":\"glm-4-flash\",\"name\":\"GLM-4-Flash\"}]");
        AiProvider groq = createProvider("groq", "Groq", true, 10,
                "[{\"code\":\"llama-3.1-8b\",\"name\":\"Llama 3.1\"}]");
        when(providerMapper.selectList(any())).thenReturn(List.of(zhipu, groq));

        List<LlmProviderSelector.ModelOption> options = selector.listAvailableModels();

        assertEquals(2, options.size());
        assertTrue(options.stream().anyMatch(o -> "zhipu".equals(o.providerCode()) && "glm-4-flash".equals(o.modelCode())));
        assertTrue(options.stream().anyMatch(o -> "groq".equals(o.providerCode()) && "llama-3.1-8b".equals(o.modelCode())));
    }

    @Test
    @DisplayName("selectEnabledProviders 返回所有启用非 mock 供应商并按优先级排序")
    void selectEnabledProvidersReturnsAllNonMock() {
        AiProvider mockProvider = createProvider("mock-local", "Mock", true, 1,
                "[{\"code\":\"mock-chat\"}]");
        AiProvider zhipu = createProvider("zhipu", "智谱", true, 5,
                "[{\"code\":\"glm-4-flash\"}]");
        AiProvider groq = createProvider("groq", "Groq", true, 10,
                "[{\"code\":\"llama-3.1-8b\"}]");
        when(providerMapper.selectList(any())).thenReturn(List.of(mockProvider, zhipu, groq));

        List<LlmProviderSelector.ProviderRuntime> result = selector.selectEnabledProviders();

        assertEquals(2, result.size());
        assertEquals("zhipu", result.get(0).provider().getProviderCode());
        assertEquals("groq", result.get(1).provider().getProviderCode());
    }

    private AiProvider createProvider(String code, String name, boolean enabled, int priority, String modelListJson) {
        AiProvider provider = new AiProvider();
        provider.setProviderCode(code);
        provider.setProviderName(name);
        provider.setEndpoint("https://api.example.com/v1");
        provider.setEnabled(enabled);
        provider.setPriority(priority);
        provider.setModelListJson(modelListJson);
        provider.setProtocol("OPENAI_COMPATIBLE");
        return provider;
    }
}
