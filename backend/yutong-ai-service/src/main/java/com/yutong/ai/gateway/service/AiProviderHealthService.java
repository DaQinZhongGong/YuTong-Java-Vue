package com.yutong.ai.gateway.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yutong.ai.chat.service.llm.LlmProviderSelector;
import com.yutong.ai.gateway.domain.AiProvider;
import com.yutong.ai.gateway.dto.ProviderHealthResult;
import com.yutong.ai.gateway.mapper.AiProviderMapper;
import com.yutong.common.auth.CurrentUserContext;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 供应商健康检查服务。
 * 对每个已启用的供应商执行轻量级健康探测，返回可达性、延迟、默认模型是否可用。
 * 设计来源: P6-02 免费 LLM 供应商集成 - 健康检查能力
 * <p>
 * 并发模型:
 * - 批量检查使用固定大小线程池（core 12，daemon 线程）并发探测，避免 95+ 家供应商串行
 *   超时叠加导致最坏 20+ 分钟。
 * - 单供应商保留其 timeout_ms 但上限压到 10s，避免单家拖垮整体。
 * - 整批检查硬上限 60s，超时的供应商标记为 unreachable/timeout，不阻塞返回。
 * - 结果按 providerCode 缓存 5 分钟，过期重新检查。
 * <p>
 * 实现约束:
 * - 使用 Java 原生 java.net.http.HttpClient，不引入新依赖。
 * - 健康检查是只读操作，不修改任何数据。
 * - mock-local 供应商直接返回 ok，不发起网络请求。
 * - 线程池在 @PreDestroy 中关闭，daemon 线程不阻止 JVM 退出。
 */
@Service
public class AiProviderHealthService {

    private static final Logger log = LoggerFactory.getLogger(AiProviderHealthService.class);

    /** mock 供应商编码，跳过网络探测 */
    private static final String MOCK_PROVIDER_CODE = "mock-local";

    /** 默认超时（毫秒），当 provider.timeoutMs 为空时使用 */
    private static final long DEFAULT_TIMEOUT_MS = 15_000L;

    /** 单供应商探测超时上限（毫秒）：压到 10s，避免单家拖垮整体 */
    private static final long MAX_SINGLE_TIMEOUT_MS = 10_000L;

    /** 整批健康检查硬上限（毫秒）：超时未完成者标记为 unreachable */
    private static final long BATCH_HARD_TIMEOUT_MS = 60_000L;

    /** 健康检查线程池核心线程数 */
    private static final int HEALTH_POOL_CORE = 12;

    /** 健康检查缓存有效期（毫秒） */
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L;

    /** OpenAI 兼容 chat completions 路径 */
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    private final AiProviderMapper providerMapper;
    private final LlmProviderSelector providerSelector;

    /** 共享 HttpClient（Java 原生，无新依赖）；仅用于默认探测实现 */
    private final HttpClient httpClient;

    /** 探测执行器，便于在测试中注入可控实现（默认走共享 httpClient） */
    private final HealthProbe healthProbe;

    /** 固定大小守护线程池，并发执行供应商探测 */
    private final ExecutorService healthExecutor;

    /** 单供应商超时上限（毫秒） */
    private final long maxSingleTimeoutMs;

    /** 整批硬上限（毫秒） */
    private final long batchHardTimeoutMs;

    /** 按 providerCode 缓存健康检查结果 */
    private final Map<String, ProviderHealthResult> healthCache = new ConcurrentHashMap<>();

    /** 按 providerCode 缓存检查时间戳（用于判断缓存是否过期） */
    private final Map<String, Long> cacheTimestamps = new ConcurrentHashMap<>();

    @Autowired
    public AiProviderHealthService(AiProviderMapper providerMapper, LlmProviderSelector providerSelector) {
        this.providerMapper = providerMapper;
        this.providerSelector = providerSelector;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.healthProbe = req -> this.httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        this.healthExecutor = defaultExecutor();
        this.maxSingleTimeoutMs = MAX_SINGLE_TIMEOUT_MS;
        this.batchHardTimeoutMs = BATCH_HARD_TIMEOUT_MS;
    }

    /**
     * 测试专用构造：注入可控探测执行器、线程池与超时参数，便于确定性单测。
     */
    AiProviderHealthService(AiProviderMapper providerMapper, LlmProviderSelector providerSelector,
                            HealthProbe healthProbe, ExecutorService healthExecutor,
                            long maxSingleTimeoutMs, long batchHardTimeoutMs) {
        this.providerMapper = providerMapper;
        this.providerSelector = providerSelector;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.healthProbe = healthProbe;
        this.healthExecutor = healthExecutor;
        this.maxSingleTimeoutMs = maxSingleTimeoutMs;
        this.batchHardTimeoutMs = batchHardTimeoutMs;
    }

    private static ExecutorService defaultExecutor() {
        AtomicInteger seq = new AtomicInteger(0);
        return Executors.newFixedThreadPool(HEALTH_POOL_CORE, r -> {
            Thread t = new Thread(r, "ai-health-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 检查当前租户所有已启用的供应商（并发执行）。
     * 结果写入缓存并返回，顺序与按优先级排序后的供应商列表一致。
     */
    public List<ProviderHealthResult> checkAllEnabledProviders() {
        String tenantId = CurrentUserContext.getTenantId();
        List<AiProvider> providers = loadEnabledProviders(tenantId);

        List<ProviderHealthResult> results = runConcurrentChecks(providers);

        long now = System.currentTimeMillis();
        for (int i = 0; i < providers.size(); i++) {
            String code = providers.get(i).getProviderCode();
            healthCache.put(code, results.get(i));
            cacheTimestamps.put(code, now);
        }
        return results;
    }

    /**
     * 返回最近缓存的健康状态列表。
     * 缓存过期（超过 5 分钟）的条目会触发即时并发重新检查，未过期的直接复用缓存。
     */
    public List<ProviderHealthResult> getLatestHealth() {
        String tenantId = CurrentUserContext.getTenantId();
        List<AiProvider> providers = loadEnabledProviders(tenantId);

        long now = System.currentTimeMillis();
        List<ProviderHealthResult> results = new ArrayList<>(providers.size());
        List<Integer> staleIndexes = new ArrayList<>(providers.size());
        List<AiProvider> staleProviders = new ArrayList<>(providers.size());

        for (int i = 0; i < providers.size(); i++) {
            AiProvider provider = providers.get(i);
            String code = provider.getProviderCode();
            Long ts = cacheTimestamps.get(code);
            ProviderHealthResult cached = healthCache.get(code);
            if (cached != null && ts != null && (now - ts) < CACHE_TTL_MS) {
                results.add(cached);
            } else {
                results.add(null);
                staleIndexes.add(i);
                staleProviders.add(provider);
            }
        }

        if (!staleProviders.isEmpty()) {
            List<ProviderHealthResult> fresh = runConcurrentChecks(staleProviders);
            for (int k = 0; k < staleProviders.size(); k++) {
                int idx = staleIndexes.get(k);
                ProviderHealthResult r = fresh.get(k);
                results.set(idx, r);
                healthCache.put(staleProviders.get(k).getProviderCode(), r);
                cacheTimestamps.put(staleProviders.get(k).getProviderCode(), now);
            }
        }
        return results;
    }

    /**
     * 并发执行一组供应商探测，受整批硬上限约束。
     * 未在硬上限内完成的供应商标记为 unreachable（超时）。
     */
    private List<ProviderHealthResult> runConcurrentChecks(List<AiProvider> providers) {
        if (providers.isEmpty()) {
            return List.of();
        }
        List<CompletableFuture<ProviderHealthResult>> futures = new ArrayList<>(providers.size());
        for (AiProvider provider : providers) {
            futures.add(CompletableFuture.supplyAsync(() -> checkProvider(provider), healthExecutor));
        }

        CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        try {
            all.get(batchHardTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            log.warn("health batch hard timeout reached: limitMs={} total={} message=marking remaining as unreachable",
                    batchHardTimeoutMs, providers.size());
        } catch (Exception e) {
            log.warn("health batch interrupted: {}", e.getMessage());
        }

        List<ProviderHealthResult> results = new ArrayList<>(providers.size());
        for (int i = 0; i < providers.size(); i++) {
            CompletableFuture<ProviderHealthResult> f = futures.get(i);
            if (f.isDone()) {
                try {
                    results.add(f.join());
                } catch (Exception e) {
                    results.add(timeoutResult(providers.get(i)));
                }
            } else {
                f.cancel(false);
                results.add(timeoutResult(providers.get(i)));
            }
        }
        return results;
    }

    /**
     * 对单个供应商执行健康检查。
     * - mock-local 直接返回 ok。
     * - 其余供应商构造极短的 OpenAI 兼容 chat 请求（max_tokens=1）探测可达性与默认模型可用性。
     * 单供应商超时受 maxSingleTimeoutMs 上限约束。
     */
    public ProviderHealthResult checkProvider(AiProvider provider) {
        String providerCode = provider.getProviderCode();
        String providerName = provider.getProviderName();

        if (MOCK_PROVIDER_CODE.equals(providerCode)) {
            log.info("health check skip mock provider: providerCode={}", providerCode);
            return ProviderHealthResult.ok(providerCode, providerName, 0, null, true);
        }

        String endpoint = provider.getEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            return ProviderHealthResult.fail(providerCode, providerName, "供应商 endpoint 为空");
        }
        if (containsPathVariable(endpoint)) {
            return ProviderHealthResult.fail(providerCode, providerName,
                    "endpoint 包含未替换的路径变量，请先配置");
        }

        String apiKey = providerSelector.resolveApiKey(provider.getApiKeyRef());
        String defaultModel = providerSelector.extractDefaultModel(provider.getModelListJson());
        if (defaultModel == null || defaultModel.isBlank()) {
            return ProviderHealthResult.fail(providerCode, providerName, "供应商未配置可用模型");
        }

        long timeoutMs = effectiveTimeoutMs(provider);
        String url = buildUrl(endpoint, CHAT_COMPLETIONS_PATH);
        String body = "{\"model\":\"" + defaultModel + "\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"max_tokens\":1}";

        long startTime = System.currentTimeMillis();
        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json");
            // 免 Key 供应商（如 pollinations）apiKeyRef 解析为空，不发送 Authorization 头，走匿名请求路径
            if (apiKey != null && !apiKey.isBlank()) {
                reqBuilder.header("Authorization", "Bearer " + apiKey);
            }
            HttpRequest req = reqBuilder.POST(HttpRequest.BodyPublishers.ofString(body)).build();

            HttpResponse<String> resp = healthProbe.send(req);
            long latencyMs = System.currentTimeMillis() - startTime;
            int status = resp.statusCode();

            if (status == 200) {
                log.info("health check ok: providerCode={} model={} latencyMs={} status=200",
                        providerCode, defaultModel, latencyMs);
                return ProviderHealthResult.ok(providerCode, providerName, latencyMs, defaultModel, true);
            }
            if (status == 401 || status == 403) {
                log.warn("health check auth failed: providerCode={} model={} status={} latencyMs={}",
                        providerCode, defaultModel, status, latencyMs);
                return ProviderHealthResult.ok(providerCode, providerName, latencyMs, defaultModel, false);
            }
            log.warn("health check non-200: providerCode={} model={} status={} latencyMs={}",
                    providerCode, defaultModel, status, latencyMs);
            return ProviderHealthResult.ok(providerCode, providerName, latencyMs, defaultModel, false);
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            String errorSummary = e.getMessage();
            if (errorSummary == null || errorSummary.isBlank()) {
                errorSummary = e.toString();
            }
            log.warn("health check failed: providerCode={} model={} latencyMs={} err={}",
                    providerCode, defaultModel, latencyMs, errorSummary);
            return ProviderHealthResult.fail(providerCode, providerName, errorSummary);
        }
    }

    /**
     * 计算单供应商探测超时（毫秒）：保留 provider 配置，但上限压到 maxSingleTimeoutMs。
     * 包级可见，便于单测验证 10s 上限。
     */
    long effectiveTimeoutMs(AiProvider provider) {
        long configured = provider.getTimeoutMs() == null ? DEFAULT_TIMEOUT_MS : provider.getTimeoutMs();
        return Math.min(configured, maxSingleTimeoutMs);
    }

    private ProviderHealthResult timeoutResult(AiProvider provider) {
        return ProviderHealthResult.fail(provider.getProviderCode(), provider.getProviderName(),
                "健康检查超时（批处理硬上限 " + batchHardTimeoutMs + "ms 内未完成）");
    }

    private List<AiProvider> loadEnabledProviders(String tenantId) {
        return providerMapper.selectList(
                new LambdaQueryWrapper<AiProvider>()
                        .eq(AiProvider::getTenantId, tenantId)
                        .eq(AiProvider::getEnabled, true)
                        .orderByAsc(AiProvider::getPriority));
    }

    private String buildUrl(String endpoint, String path) {
        if (endpoint.endsWith("/") && path.startsWith("/")) {
            return endpoint + path.substring(1);
        }
        if (!endpoint.endsWith("/") && !path.startsWith("/")) {
            return endpoint + "/" + path;
        }
        return endpoint + path;
    }

    private boolean containsPathVariable(String endpoint) {
        return endpoint != null && endpoint.contains("{") && endpoint.contains("}");
    }

    /** 探测执行器抽象，便于单测注入受控实现。 */
    @FunctionalInterface
    interface HealthProbe {
        HttpResponse<String> send(HttpRequest request) throws Exception;
    }

    @PreDestroy
    public void destroy() {
        log.info("shutting down ai provider health executor");
        healthExecutor.shutdownNow();
        try {
            if (!healthExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("ai provider health executor did not terminate within 5s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
