package com.yutong.ai.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.ai.gateway.domain.AiProvider;
import com.yutong.ai.gateway.service.AiProviderRegistry;
import com.yutong.ai.rag.domain.AiKnowledgeBase;
import com.yutong.ai.rag.service.embedding.MultimodalEmbeddingProvider;
import com.yutong.common.auth.CurrentUserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EmbeddingService 多模态路由单元测试。
 * 设计来源: docs/compose/spec/ai-depth-parity.md S2.3
 * 验收: image/video 走 MultimodalEmbeddingProvider；无 Provider fail-closed；文本路径兼容；维度由 kb 驱动。
 */
@DisplayName("EmbeddingService 多模态路由")
class EmbeddingServiceMultimodalTest {

    private AiProviderRegistry registry;
    private MultimodalEmbeddingProvider multimodalProvider;
    private EmbeddingService service;
    private AiProvider bailianProvider;
    private AiKnowledgeBase kb;

    @BeforeEach
    void setUp() {
        CurrentUserContext.clear();
        CurrentUserContext.set("u1", "tenant-1", "tester");

        registry = mock(AiProviderRegistry.class);
        multimodalProvider = mock(MultimodalEmbeddingProvider.class);
        when(multimodalProvider.getProviderType()).thenReturn("bailian");
        when(multimodalProvider.supportsModality("image")).thenReturn(true);
        when(multimodalProvider.supportsModality("video")).thenReturn(true);
        when(multimodalProvider.supportsModality("text")).thenReturn(true);
        when(multimodalProvider.supports(any())).thenReturn(true);

        bailianProvider = new AiProvider();
        bailianProvider.setProviderCode("bailian");
        bailianProvider.setProviderType("bailian");
        bailianProvider.setApiKeyRef("sk-test");
        bailianProvider.setEndpoint("https://dashscope.aliyuncs.com/api/v1");

        when(registry.resolveForTenant(eq("tenant-1"), any(), anyString()))
                .thenReturn(List.of(bailianProvider));
        when(registry.resolveForTenant(eq("tenant-1"), any(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(List.of(bailianProvider));

        service = new EmbeddingService(registry, List.of(), List.of(multimodalProvider), new ObjectMapper());

        kb = new AiKnowledgeBase();
        kb.setId("kb-1");
        kb.setEmbeddingModel("multimodal-embedding-v1");
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    private float[] unitVector(int dim) {
        float[] v = new float[dim];
        v[0] = 1.0f;
        return v;
    }

    @Test
    @DisplayName("image 模态路由到 embedImage 并返回向量")
    void embedMultimodal_image_routesToEmbedImage() throws Exception {
        float[] remote = unitVector(1536);
        when(multimodalProvider.embedImage(eq("https://example.com/a.png"), any(), any()))
                .thenReturn(remote);

        float[] vec = service.embedMultimodal("image", "https://example.com/a.png", kb);
        assertNotNull(vec);
        assertEquals(1536, vec.length);
        assertEquals(1.0f, vec[0], 0.0001f);
        verify(multimodalProvider).embedImage(eq("https://example.com/a.png"), any(), any());
        verify(multimodalProvider, never()).embedVideo(anyString(), any(), any());
    }

    @Test
    @DisplayName("video 模态路由到 embedVideo")
    void embedMultimodal_video_routesToEmbedVideo() throws Exception {
        float[] remote = unitVector(1024);
        when(multimodalProvider.embedVideo(eq("https://example.com/v.mp4"), any(), any()))
                .thenReturn(remote);
        kb.setEmbeddingDimension(1024);

        float[] vec = service.embedMultimodal("video", "https://example.com/v.mp4", kb);
        assertEquals(1024, vec.length);
        verify(multimodalProvider).embedVideo(eq("https://example.com/v.mp4"), any(), any());
    }

    @Test
    @DisplayName("远端维度与 kb.embeddingDimension 不一致时 adapt 到目标维度")
    void embedMultimodal_adaptsDimension() throws Exception {
        float[] remote = unitVector(2048);
        when(multimodalProvider.embedImage(anyString(), any(), any())).thenReturn(remote);
        kb.setEmbeddingDimension(1536);

        float[] vec = service.embedMultimodal("image", "data:image/png;base64,xxx", kb);
        assertEquals(1536, vec.length);
    }

    @Test
    @DisplayName("无 MultimodalEmbeddingProvider 时 image fail-closed 抛异常")
    void embedMultimodal_noProvider_failClosed() {
        EmbeddingService bare = new EmbeddingService(registry, List.of(), List.of(), new ObjectMapper());
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> bare.embedMultimodal("image", "https://example.com/a.png", kb));
        assertTrue(ex.getMessage().contains("image") || ex.getMessage().toLowerCase().contains("multimodal"));
    }

    @Test
    @DisplayName("远程全部失败时 image fail-closed")
    void embedMultimodal_remoteFails_failClosed() throws Exception {
        when(multimodalProvider.embedImage(anyString(), any(), any()))
                .thenThrow(new IllegalStateException("apiKey missing"));
        assertThrows(IllegalStateException.class,
                () -> service.embedMultimodal("image", "https://example.com/a.png", kb));
    }

    @Test
    @DisplayName("embedMultimodalOutcome 在远程失败时返回 remoteSuccess=false 而不抛")
    void embedMultimodalOutcome_reportsFailure() throws Exception {
        when(multimodalProvider.embedImage(anyString(), any(), any()))
                .thenThrow(new IllegalStateException("boom"));
        EmbeddingService.MultimodalEmbedOutcome outcome =
                service.embedMultimodalOutcome("image", "https://example.com/a.png", kb);
        assertFalse(outcome.remoteSuccess());
        assertNull(outcome.vector());
        assertNotNull(outcome.message());
    }

    @Test
    @DisplayName("text 模态走文本路径且 remoteSuccess 可观测（无远程时 hash fallback）")
    void embedMultimodal_text_fallsBackToHash() {
        // registry 有 provider 但 multimodal 不参与文本；remoteProviders 为空 → 哈希
        float[] vec = service.embedMultimodal("text", "hello world", kb);
        assertEquals(1536, vec.length);
        EmbeddingService.MultimodalEmbedOutcome outcome =
                service.embedMultimodalOutcome("text", "hello world", kb);
        assertFalse(outcome.remoteSuccess());
        assertEquals(1536, outcome.vector().length);
    }

    @Test
    @DisplayName("kb.embeddingDimension 驱动哈希与多模态目标维度")
    void resolveTargetDimension_fromKb() {
        assertEquals(1536, service.resolveTargetDimension(null));
        assertEquals(1536, service.resolveTargetDimension(kb));

        kb.setEmbeddingDimension(1024);
        assertEquals(1024, service.resolveTargetDimension(kb));

        float[] vec = service.embed("维度测试", kb);
        assertEquals(1024, vec.length);
    }

    @Test
    @DisplayName("preview 返回前 8 维")
    void preview_capsAt8() {
        float[] v = new float[1536];
        for (int i = 0; i < 1536; i++) v[i] = i;
        List<Float> preview = service.preview(v, 8);
        assertEquals(8, preview.size());
        assertEquals(0.0f, preview.get(0), 0.0001f);
        assertEquals(7.0f, preview.get(7), 0.0001f);
    }
}
