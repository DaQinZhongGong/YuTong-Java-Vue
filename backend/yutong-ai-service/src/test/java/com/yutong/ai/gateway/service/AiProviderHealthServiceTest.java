package com.yutong.ai.gateway.service;

import com.yutong.ai.gateway.domain.AiProvider;
import com.yutong.ai.gateway.dto.ProviderHealthResult;
import com.yutong.ai.gateway.mapper.AiProviderMapper;
import com.yutong.ai.chat.service.llm.LlmProviderSelector;
import com.yutong.common.auth.CurrentUserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * AI 供应商健康检查服务（并发）单元测试。
 * 设计来源: P6-02 免费 LLM 供应商集成 - 健康检查能力
 * <p>
 * 通过注入受控 HealthProbe / 线程池 / 超时参数，对并发正确性、整批硬上限、
 * 单家超时上限、mock 跳过与缓存命中进行确定性验证，避免依赖真实网络。
 */
@DisplayName("AI 供应商健康检查服务（并发）")
class AiProviderHealthServiceTest {

    private AiProviderMapper providerMapper;
    private LlmProviderSelector providerSelector;
    private AiProviderHealthService.HealthProbe probe;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        CurrentUserContext.set("user-1", "tenant-1", "admin");
        providerMapper = Mockito.mock(AiProviderMapper.class);
        providerSelector = Mockito.mock(LlmProviderSelector.class);
        when(providerSelector.resolveApiKey(anyString())).thenReturn("sk-test");
        when(providerSelector.extractDefaultModel(anyString())).thenReturn("gpt-4o-mini");
        probe = Mockito.mock(AiProviderHealthService.HealthProbe.class);
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
        if (executor != null) {
            executor.shutdownNow();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** 构造服务：使用 4 线程守护池、注入已初始化的 probe。 */
    private AiProviderHealthService service(long maxSingleTimeoutMs, long batchHardTimeoutMs) {
        executor = daemonPool(4);
        return new AiProviderHealthService(providerMapper, providerSelector,
                probe, executor, maxSingleTimeoutMs, batchHardTimeoutMs);
    }

    private static ExecutorService daemonPool(int n) {
        return Executors.newFixedThreadPool(n, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> mockResponse(int status) {
        HttpResponse<String> resp = Mockito.mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(status);
        when(resp.body()).thenReturn("{}");
        return resp;
    }

    private static AiProvider provider(String code, String name, int priority, Integer timeoutMs) {
        AiProvider p = new AiProvider();
        p.setProviderCode(code);
        p.setProviderName(name);
        p.setEndpoint("https://api.example.com/v1");
        p.setApiKeyRef("sk");
        p.setModelListJson("[{\"code\":\"gpt-4o-mini\"}]");
        p.setEnabled(true);
        p.setPriority(priority);
        p.setTimeoutMs(timeoutMs);
        return p;
    }

    @Test
    @DisplayName("mock-local 供应商直接返回 ok 且不发起网络请求")
    void mockLocalSkippedWithoutProbe() throws Exception {
        AiProviderHealthService service = service(10_000L, 60_000L);
        AiProvider mock = provider("mock-local", "Mock", 1, null);
        mock.setEndpoint(null);
        mock.setModelListJson(null);

        ProviderHealthResult result = service.checkProvider(mock);

        assertTrue(result.reachable());
        assertNull(result.errorMessage());
        verify(probe, never()).send(any(HttpRequest.class));
    }

    @Test
    @DisplayName("空供应商列表返回空结果，不发起探测")
    void emptyProviderListReturnsEmpty() throws Exception {
        when(providerMapper.selectList(any())).thenReturn(List.of());
        AiProviderHealthService service = service(10_000L, 60_000L);

        List<ProviderHealthResult> results = service.checkAllEnabledProviders();

        assertTrue(results.isEmpty());
        verify(probe, never()).send(any(HttpRequest.class));
    }

    @Test
    @DisplayName("并发检查 20 家供应商全部可达且顺序与优先级一致，且明显快于串行")
    void concurrencyChecksAllProvidersInParallel() throws Exception {
        int n = 20;
        List<AiProvider> providers = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            providers.add(provider("prov-" + i, "Provider " + i, i, 3000));
        }
        when(providerMapper.selectList(any())).thenReturn(providers);
        final AtomicInteger calls = new AtomicInteger(0);
        when(probe.send(any(HttpRequest.class))).thenAnswer(inv -> {
            calls.incrementAndGet();
            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return mockResponse(200);
        });

        AiProviderHealthService service = service(10_000L, 60_000L);
        long start = System.currentTimeMillis();
        List<ProviderHealthResult> results = service.checkAllEnabledProviders();
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(n, results.size());
        for (ProviderHealthResult r : results) {
            assertTrue(r.reachable(), "应为可达: " + r.providerCode());
            assertTrue(r.defaultModelOk());
        }
        // 顺序与优先级一致
        for (int i = 0; i < n; i++) {
            assertEquals("prov-" + i, results.get(i).providerCode());
        }
        // 串行 20*30ms=600ms；4 线程应显著更快
        assertTrue(elapsed < 500, "并发应明显快于串行, elapsed=" + elapsed);
        assertEquals(n, calls.get());
    }

    @Test
    @DisplayName("整批硬上限内未完成的供应商标记为 unreachable（超时）且不阻塞返回")
    void batchHardTimeoutMarksUnreachable() throws Exception {
        int n = 10;
        List<AiProvider> providers = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            providers.add(provider("prov-" + i, "Provider " + i, i, 3000));
        }
        when(providerMapper.selectList(any())).thenReturn(providers);
        // 每次探测耗时 200ms，2 线程在 300ms 硬上限内只能完成约 2 家
        when(probe.send(any(HttpRequest.class))).thenAnswer(inv -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return mockResponse(200);
        });

        // 注入 2 线程池 + 300ms 整批硬上限
        executor = daemonPool(2);
        AiProviderHealthService service = new AiProviderHealthService(
                providerMapper, providerSelector, probe, executor, 10_000L, 300L);

        long start = System.currentTimeMillis();
        List<ProviderHealthResult> results = service.checkAllEnabledProviders();
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(n, results.size());
        long unreachable = results.stream().filter(r -> !r.reachable()).count();
        assertTrue(unreachable >= 6, "应至少有 6 家因超时标记为不可达, actual=" + unreachable);
        assertTrue(results.stream().filter(r -> !r.reachable())
                .allMatch(r -> r.errorMessage() != null && r.errorMessage().contains("超时")),
                "超时结果应携带超时错误信息");
        // 不应等待到串行 10*200=2000ms
        assertTrue(elapsed < 600, "整批应在硬上限附近返回, elapsed=" + elapsed);
    }

    @Test
    @DisplayName("单供应商超时受 10s 上限约束")
    void singleTimeoutCappedAtMax() {
        AiProviderHealthService service = service(10_000L, 60_000L);

        AiProvider huge = provider("p-huge", "Huge", 1, 60_000);
        assertEquals(10_000L, service.effectiveTimeoutMs(huge));

        AiProvider small = provider("p-small", "Small", 1, 5_000);
        assertEquals(5_000L, service.effectiveTimeoutMs(small));

        AiProvider none = provider("p-none", "None", 1, null);
        // 默认 15s 也被压到 10s 上限
        assertEquals(10_000L, service.effectiveTimeoutMs(none));
    }

    @Test
    @DisplayName("缓存命中时 getLatestHealth 不重复发起探测")
    void cacheHitAvoidsReProbe() throws Exception {
        List<AiProvider> providers = List.of(
                provider("p-a", "A", 1, 3000),
                provider("p-b", "B", 2, 3000),
                provider("p-c", "C", 3, 3000));
        when(providerMapper.selectList(any())).thenReturn(providers);
        HttpResponse<String> okResp = mockResponse(200);
        when(probe.send(any(HttpRequest.class))).thenReturn(okResp);

        AiProviderHealthService service = service(10_000L, 60_000L);

        List<ProviderHealthResult> first = service.checkAllEnabledProviders();
        assertEquals(3, first.size());
        assertTrue(first.stream().allMatch(ProviderHealthResult::reachable));

        // 立即再次读取，应复用缓存，不新增探测调用
        List<ProviderHealthResult> second = service.getLatestHealth();
        assertEquals(3, second.size());
        assertTrue(second.stream().allMatch(ProviderHealthResult::reachable));

        verify(probe, times(3)).send(any(HttpRequest.class));
    }

    @Test
    @DisplayName("首次检查写入缓存后，未过期时 getLatestHealth 仍复用缓存")
    void staleCacheTriggersRecheck() throws Exception {
        List<AiProvider> providers = List.of(provider("p-a", "A", 1, 3000));
        when(providerMapper.selectList(any())).thenReturn(providers);
        final AtomicInteger calls = new AtomicInteger(0);
        HttpResponse<String> okResp2 = mockResponse(200);
        when(probe.send(any(HttpRequest.class))).thenAnswer(inv -> {
            calls.incrementAndGet();
            return okResp2;
        });

        AiProviderHealthService service = service(10_000L, 60_000L);
        service.checkAllEnabledProviders();
        // 首次检查后写入缓存；再次调用时若缓存未过期仍走缓存
        service.getLatestHealth();

        assertEquals(1, calls.get(), "缓存未过期时不应重复探测");
    }
}
