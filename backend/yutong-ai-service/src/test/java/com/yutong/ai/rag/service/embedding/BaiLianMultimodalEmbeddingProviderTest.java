package com.yutong.ai.rag.service.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.ai.gateway.domain.AiProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BaiLianMultimodalEmbeddingProvider 单元测试。
 * 设计来源: docs/compose/spec/ai-depth-parity.md S2.3
 * 验收: 无 Key 失败关闭；supports 匹配 bailian/qianwen/aliyun；supportsModality 覆盖 text/image/video。
 */
@DisplayName("百炼多模态 Embedding 供应商")
class BaiLianMultimodalEmbeddingProviderTest {

    private BaiLianMultimodalEmbeddingProvider provider;

    @BeforeEach
    void setUp() {
        provider = new BaiLianMultimodalEmbeddingProvider(new ObjectMapper());
    }

    private AiProvider providerOf(String providerType, String endpoint, String apiKeyRef) {
        AiProvider p = new AiProvider();
        p.setProviderCode("test");
        p.setProviderType(providerType);
        p.setEndpoint(endpoint);
        p.setApiKeyRef(apiKeyRef);
        return p;
    }

    // ==================== supports ====================

    @Test
    @DisplayName("providerType bailian / qianwen / aliyun / alibailian / dashscope 均命中")
    void supports_providerTypeMatches() {
        assertTrue(provider.supports(providerOf("bailian", null, null)));
        assertTrue(provider.supports(providerOf("qianwen", null, null)));
        assertTrue(provider.supports(providerOf("aliyun", null, null)));
        assertTrue(provider.supports(providerOf("alibailian", null, null)));
        assertTrue(provider.supports(providerOf("dashscope", null, null)));
        assertTrue(provider.supports(providerOf("BAiLian", null, null)));
    }

    @Test
    @DisplayName("endpoint 含 dashscope / aliyuncs 时命中")
    void supports_endpointMatches() {
        assertTrue(provider.supports(providerOf("custom_api", "https://dashscope.aliyuncs.com/api/v1", null)));
        assertTrue(provider.supports(providerOf(null, "https://bailian.console.aliyun.com", null)));
    }

    @Test
    @DisplayName("null / 无关 provider 不命中")
    void supports_falseCases() {
        assertFalse(provider.supports(null));
        assertFalse(provider.supports(providerOf("zhipu", "https://open.bigmodel.cn", null)));
        assertFalse(provider.supports(providerOf("siliconflow", "https://api.siliconflow.cn/v1", null)));
    }

    @Test
    @DisplayName("getProviderType 固定为 bailian")
    void getProviderType() {
        assertEquals("bailian", provider.getProviderType());
    }

    // ==================== supportsModality ====================

    @Test
    @DisplayName("支持 text / image / video，不支持 audio 与空值")
    void supportsModality() {
        assertTrue(provider.supportsModality("text"));
        assertTrue(provider.supportsModality("image"));
        assertTrue(provider.supportsModality("video"));
        assertTrue(provider.supportsModality("IMAGE"));
        assertFalse(provider.supportsModality("audio"));
        assertFalse(provider.supportsModality(null));
        assertFalse(provider.supportsModality(""));
        assertFalse(provider.supportsModality("  "));
    }

    // ==================== fail-closed 无 Key ====================

    @Test
    @DisplayName("无 Key 时 embed 抛异常 fail-closed")
    void embed_withoutKey_throws() {
        AiProvider p = providerOf("bailian", "https://dashscope.aliyuncs.com/api/v1", null);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> provider.embed("hello", "multimodal-embedding-v1", p));
        assertTrue(ex.getMessage().toLowerCase().contains("apikey")
                || ex.getMessage().toLowerCase().contains("api key")
                || ex.getMessage().toLowerCase().contains("missing"));
    }

    @Test
    @DisplayName("无 Key 时 embedImage 抛异常 fail-closed")
    void embedImage_withoutKey_throws() {
        AiProvider p = providerOf("bailian", null, "");
        assertThrows(IllegalStateException.class,
                () -> provider.embedImage("https://example.com/a.png", null, p));
    }

    @Test
    @DisplayName("无 Key 时 embedVideo 抛异常 fail-closed")
    void embedVideo_withoutKey_throws() {
        AiProvider p = providerOf("qianwen", null, null);
        assertThrows(IllegalStateException.class,
                () -> provider.embedVideo("https://example.com/a.mp4", "multimodal-embedding-v1", p));
    }

    @Test
    @DisplayName("空 payload 抛 IllegalArgumentException")
    void emptyPayload_throws() {
        AiProvider p = providerOf("bailian", null, "sk-test");
        assertThrows(IllegalArgumentException.class, () -> provider.embed("", null, p));
        assertThrows(IllegalArgumentException.class, () -> provider.embedImage("  ", null, p));
        assertThrows(IllegalArgumentException.class, () -> provider.embedVideo(null, null, p));
    }

}
