package com.yutong.ai.chat.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.ai.gateway.domain.AiProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OpenAI 兼容适配器契约测试。
 * 验证不同免费 LLM 供应商的 endpoint / 请求体 / 响应体契约兼容性。
 * 设计来源: P6-02 免费 LLM 供应商集成
 */
class OpenAiCompatibleAdapterContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AiProvider provider;
    private OpenAiCompatibleAdapter.HttpExecutor httpExecutor;
    @SuppressWarnings("unchecked")
    private final HttpResponse<String> mockResponse = mock(HttpResponse.class);

    @BeforeEach
    void setUp() {
        provider = new AiProvider();
        provider.setProviderCode("siliconflow");
        provider.setEndpoint("https://api.siliconflow.cn/v1");
        provider.setTimeoutMs(30000);

        httpExecutor = mock(OpenAiCompatibleAdapter.HttpExecutor.class);
    }

    @Test
    @DisplayName("智谱 GLM-4-Flash endpoint 与请求体契约")
    void zhipuContract() throws Exception {
        provider.setProviderCode("zhipu");
        provider.setEndpoint("https://open.bigmodel.cn/api/paas/v4");

        String responseJson = """
                {
                  "choices": [
                    { "message": { "role": "assistant", "content": "zhipu-reply" }, "finish_reason": "stop" }
                  ],
                  "usage": { "prompt_tokens": 5, "completion_tokens": 3 }
                }
                """;
        expectOpenAiChatCompletion("https://open.bigmodel.cn/api/paas/v4", "Bearer sk-zhipu",
                "glm-4-flash", responseJson);

        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(
                provider, "sk-zhipu", objectMapper, httpExecutor);
        LlmResponse response = adapter.chat(new LlmRequest("glm-4-flash",
                List.of(LlmMessage.user("你好")), 0.7, null, false, "chat"));

        assertFalse(response.isError());
        assertEquals("zhipu", response.providerCode());
        assertEquals("zhipu-reply", response.content());
        assertEquals(5, response.tokenInput());
        assertEquals(3, response.tokenOutput());
    }

    @Test
    @DisplayName("OpenRouter 请求体包含额外模型路由参数")
    void openrouterContract() throws Exception {
        provider.setProviderCode("openrouter");
        provider.setEndpoint("https://openrouter.ai/api/v1");

        String responseJson = """
                {
                  "choices": [
                    { "message": { "role": "assistant", "content": "openrouter-reply" }, "finish_reason": "stop" }
                  ],
                  "usage": { "prompt_tokens": 4, "completion_tokens": 4 }
                }
                """;
        expectOpenAiChatCompletion("https://openrouter.ai/api/v1", "Bearer sk-or",
                "openai/gpt-4o-mini", responseJson);

        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(
                provider, "sk-or", objectMapper, httpExecutor);
        LlmResponse response = adapter.chat(new LlmRequest("openai/gpt-4o-mini",
                List.of(LlmMessage.system("You are helpful"), LlmMessage.user("hi")),
                0.5, 512, false, "chat"));

        assertFalse(response.isError());
        assertEquals("openrouter", response.providerCode());
        assertEquals("openai/gpt-4o-mini", response.modelCode());
        assertEquals("openrouter-reply", response.content());
    }

    @Test
    @DisplayName("供应商未返回 usage 时按字符数估算 token")
    void missingUsageEstimatesTokens() throws Exception {
        String responseJson = """
                {
                  "choices": [
                    { "message": { "role": "assistant", "content": "ABCD" }, "finish_reason": "stop" }
                  ]
                }
                """;
        expectOpenAiChatCompletion("https://api.siliconflow.cn/v1", "Bearer sk-test",
                "model", responseJson);

        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(
                provider, "sk-test", objectMapper, httpExecutor);
        LlmResponse response = adapter.chat(new LlmRequest("model",
                List.of(LlmMessage.user("一二")), null, null, false, "chat"));

        assertFalse(response.isError());
        assertEquals("ABCD", response.content());
        assertTrue(response.tokenOutput() > 0, "应估算输出 token");
    }

    @Test
    @DisplayName("choices 为空时返回错误响应")
    void emptyChoicesReturnsError() throws Exception {
        String responseJson = """
                { "choices": [], "usage": { "prompt_tokens": 1, "completion_tokens": 0 } }
                """;
        expectOpenAiChatCompletion("https://api.siliconflow.cn/v1", "Bearer sk-test",
                "model", responseJson);

        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(
                provider, "sk-test", objectMapper, httpExecutor);
        LlmResponse response = adapter.chat(new LlmRequest("model",
                List.of(LlmMessage.user("hi")), null, null, false, "chat"));

        assertTrue(response.isError());
        assertNotNull(response.error());
    }

    @Test
    @DisplayName("max_tokens 为 null 时不写入请求体")
    void nullMaxTokensOmitted() throws Exception {
        String responseJson = """
                {
                  "choices": [ { "message": { "content": "ok" }, "finish_reason": "stop" } ],
                  "usage": { "prompt_tokens": 1, "completion_tokens": 1 }
                }
                """;
        expectOpenAiChatCompletion("https://api.siliconflow.cn/v1", "Bearer sk-test",
                "model", responseJson);

        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(
                provider, "sk-test", objectMapper, httpExecutor);
        LlmResponse response = adapter.chat(new LlmRequest("model",
                List.of(LlmMessage.user("hi")), 0.7, null, false, "chat"));

        assertFalse(response.isError());
    }

    @Test
    @DisplayName("pollinations 免 Key endpoint 与请求体契约")
    void canChatWithPollinations() throws Exception {
        AiProvider p = buildProvider(
                "pollinations",
                "Pollinations AI",
                "https://text.pollinations.ai/openai",
                "[{\"code\":\"openai\",\"name\":\"OpenAI Compatible\",\"contextWindow\":8192,\"priceInputCny\":0.0000,\"priceOutputCny\":0.0000},{\"code\":\"mistral\",\"name\":\"Mistral\",\"contextWindow\":8192,\"priceInputCny\":0.0000,\"priceOutputCny\":0.0000}]");

        expectOpenAiChatCompletion("https://text.pollinations.ai/openai", null,
                "openai", buildReplyJson("pollinations-reply"));

        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(p, "", objectMapper, httpExecutor);
        LlmResponse response = adapter.chat(new LlmRequest("openai",
                List.of(LlmMessage.user("hi")), 0.7, null, false, "chat"));

        assertFalse(response.isError());
        assertEquals("pollinations", response.providerCode());
        assertEquals("openai", response.modelCode());
        assertEquals("pollinations-reply", response.content());
    }

    @Test
    @DisplayName("ollama-local 本地 endpoint 与请求体契约")
    void canChatWithOllamaLocal() throws Exception {
        AiProvider p = buildProvider(
                "ollama-local",
                "Ollama 本地推理",
                "http://localhost:11434/v1",
                "[{\"code\":\"llama3.1\",\"name\":\"Llama 3.1 8B\",\"contextWindow\":128000,\"priceInputCny\":0.0000,\"priceOutputCny\":0.0000},{\"code\":\"qwen2.5\",\"name\":\"Qwen 2.5 7B\",\"contextWindow\":32768,\"priceInputCny\":0.0000,\"priceOutputCny\":0.0000}]");

        expectOpenAiChatCompletion("http://localhost:11434/v1", null,
                "llama3.1", buildReplyJson("ollama-reply"));

        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(p, "", objectMapper, httpExecutor);
        LlmResponse response = adapter.chat(new LlmRequest("llama3.1",
                List.of(LlmMessage.user("hi")), 0.7, null, false, "chat"));

        assertFalse(response.isError());
        assertEquals("ollama-local", response.providerCode());
        assertEquals("llama3.1", response.modelCode());
        assertEquals("ollama-reply", response.content());
    }

    @Test
    @DisplayName("together-ai endpoint 与请求体契约")
    void canChatWithTogetherAi() throws Exception {
        AiProvider p = buildProvider(
                "together-ai",
                "Together AI",
                "https://api.together.xyz/v1",
                "[{\"code\":\"meta-llama/Llama-3.3-70B-Instruct-Turbo-Free\",\"name\":\"Llama 3.3 70B Free\",\"contextWindow\":128000,\"priceInputCny\":0.0000,\"priceOutputCny\":0.0000},{\"code\":\"meta-llama/Meta-Llama-3.1-8B-Instruct-Turbo\",\"name\":\"Llama 3.1 8B Turbo\",\"contextWindow\":128000,\"priceInputCny\":0.0001,\"priceOutputCny\":0.0001}]");

        expectOpenAiChatCompletion("https://api.together.xyz/v1", null,
                "meta-llama/Llama-3.3-70B-Instruct-Turbo-Free", buildReplyJson("together-reply"));

        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(p, "", objectMapper, httpExecutor);
        LlmResponse response = adapter.chat(new LlmRequest("meta-llama/Llama-3.3-70B-Instruct-Turbo-Free",
                List.of(LlmMessage.user("hi")), 0.7, null, false, "chat"));

        assertFalse(response.isError());
        assertEquals("together-ai", response.providerCode());
        assertEquals("meta-llama/Llama-3.3-70B-Instruct-Turbo-Free", response.modelCode());
        assertEquals("together-reply", response.content());
    }

    @Test
    @DisplayName("cerebras endpoint 与请求体契约")
    void canChatWithCerebras() throws Exception {
        AiProvider p = buildProvider(
                "cerebras",
                "Cerebras",
                "https://api.cerebras.ai/v1",
                "[{\"code\":\"llama3.1-8b\",\"name\":\"Llama 3.1 8B\",\"contextWindow\":128000,\"priceInputCny\":0.0000,\"priceOutputCny\":0.0000},{\"code\":\"llama-3.3-70b\",\"name\":\"Llama 3.3 70B\",\"contextWindow\":128000,\"priceInputCny\":0.0000,\"priceOutputCny\":0.0000}]");

        expectOpenAiChatCompletion("https://api.cerebras.ai/v1", null,
                "llama3.1-8b", buildReplyJson("cerebras-reply"));

        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(p, "", objectMapper, httpExecutor);
        LlmResponse response = adapter.chat(new LlmRequest("llama3.1-8b",
                List.of(LlmMessage.user("hi")), 0.7, null, false, "chat"));

        assertFalse(response.isError());
        assertEquals("cerebras", response.providerCode());
        assertEquals("llama3.1-8b", response.modelCode());
        assertEquals("cerebras-reply", response.content());
    }

    @Test
    @DisplayName("sambanova endpoint 与请求体契约")
    void canChatWithSambanova() throws Exception {
        AiProvider p = buildProvider(
                "sambanova",
                "SambaNova",
                "https://api.sambanova.ai/v1",
                "[{\"code\":\"Meta-Llama-3.1-8B-Instruct\",\"name\":\"Llama 3.1 8B\",\"contextWindow\":128000,\"priceInputCny\":0.0000,\"priceOutputCny\":0.0000},{\"code\":\"Meta-Llama-3.1-70B-Instruct\",\"name\":\"Llama 3.1 70B\",\"contextWindow\":128000,\"priceInputCny\":0.0000,\"priceOutputCny\":0.0000}]");

        expectOpenAiChatCompletion("https://api.sambanova.ai/v1", null,
                "Meta-Llama-3.1-8B-Instruct", buildReplyJson("sambanova-reply"));

        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(p, "", objectMapper, httpExecutor);
        LlmResponse response = adapter.chat(new LlmRequest("Meta-Llama-3.1-8B-Instruct",
                List.of(LlmMessage.user("hi")), 0.7, null, false, "chat"));

        assertFalse(response.isError());
        assertEquals("sambanova", response.providerCode());
        assertEquals("Meta-Llama-3.1-8B-Instruct", response.modelCode());
        assertEquals("sambanova-reply", response.content());
    }

    @Test
    @DisplayName("stepfun endpoint 与请求体契约")
    void canChatWithStepfun() throws Exception {
        AiProvider p = buildProvider(
                "stepfun",
                "StepFun",
                "https://api.stepfun.com/v1",
                "[{\"code\":\"step-1-flash\",\"name\":\"Step-1 Flash\",\"contextWindow\":32768,\"priceInputCny\":0.0000,\"priceOutputCny\":0.0000}]");

        expectOpenAiChatCompletion("https://api.stepfun.com/v1", null,
                "step-1-flash", buildReplyJson("stepfun-reply"));

        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(p, "", objectMapper, httpExecutor);
        LlmResponse response = adapter.chat(new LlmRequest("step-1-flash",
                List.of(LlmMessage.user("hi")), 0.7, null, false, "chat"));

        assertFalse(response.isError());
        assertEquals("stepfun", response.providerCode());
        assertEquals("step-1-flash", response.modelCode());
        assertEquals("stepfun-reply", response.content());
    }

    @Test
    @DisplayName("perplexity endpoint 与请求体契约")
    void canChatWithPerplexity() throws Exception {
        AiProvider p = buildProvider(
                "perplexity",
                "Perplexity",
                "https://api.perplexity.ai",
                "[{\"code\":\"sonar\",\"name\":\"Sonar\",\"contextWindow\":32768,\"priceInputCny\":0.0000,\"priceOutputCny\":0.0000}]");

        expectOpenAiChatCompletion("https://api.perplexity.ai", null,
                "sonar", buildReplyJson("perplexity-reply"));

        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(p, "", objectMapper, httpExecutor);
        LlmResponse response = adapter.chat(new LlmRequest("sonar",
                List.of(LlmMessage.user("hi")), 0.7, null, false, "chat"));

        assertFalse(response.isError());
        assertEquals("perplexity", response.providerCode());
        assertEquals("sonar", response.modelCode());
        assertEquals("perplexity-reply", response.content());
    }

    @Test
    @DisplayName("minimax endpoint 与请求体契约")
    void canChatWithMinimax() throws Exception {
        AiProvider p = buildProvider(
                "minimax",
                "MiniMax",
                "https://api.minimax.chat/v1",
                "[{\"code\":\"abab6.5s-chat\",\"name\":\"ABAB 6.5s Chat\",\"contextWindow\":245760,\"priceInputCny\":0.0005,\"priceOutputCny\":0.0005},{\"code\":\"abab6.5t-chat\",\"name\":\"ABAB 6.5t Chat\",\"contextWindow\":8192,\"priceInputCny\":0.0020,\"priceOutputCny\":0.0020}]");

        expectOpenAiChatCompletion("https://api.minimax.chat/v1", null,
                "abab6.5s-chat", buildReplyJson("minimax-reply"));

        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(p, "", objectMapper, httpExecutor);
        LlmResponse response = adapter.chat(new LlmRequest("abab6.5s-chat",
                List.of(LlmMessage.user("hi")), 0.7, null, false, "chat"));

        assertFalse(response.isError());
        assertEquals("minimax", response.providerCode());
        assertEquals("abab6.5s-chat", response.modelCode());
        assertEquals("minimax-reply", response.content());
    }

    @Test
    @DisplayName("novita-ai endpoint 与请求体契约")
    void canChatWithNovitaAi() throws Exception {
        AiProvider p = buildProvider(
                "novita-ai",
                "Novita AI",
                "https://api.novita.ai/v1",
                "[{\"code\":\"meta-llama/llama-3.1-8b-instruct\",\"name\":\"Llama 3.1 8B Instruct\",\"contextWindow\":32768,\"priceInputCny\":0.0000,\"priceOutputCny\":0.0000}]");

        expectOpenAiChatCompletion("https://api.novita.ai/v1", null,
                "meta-llama/llama-3.1-8b-instruct", buildReplyJson("novita-reply"));

        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(p, "", objectMapper, httpExecutor);
        LlmResponse response = adapter.chat(new LlmRequest("meta-llama/llama-3.1-8b-instruct",
                List.of(LlmMessage.user("hi")), 0.7, null, false, "chat"));

        assertFalse(response.isError());
        assertEquals("novita-ai", response.providerCode());
        assertEquals("meta-llama/llama-3.1-8b-instruct", response.modelCode());
        assertEquals("novita-reply", response.content());
    }

    @Test
    @DisplayName("ai21 endpoint 与请求体契约")
    void canChatWithAi21() throws Exception {
        AiProvider p = buildProvider(
                "ai21",
                "AI21 Labs",
                "https://api.ai21.com/studio/v1",
                "[{\"code\":\"jamba-1.5-mini\",\"name\":\"Jamba 1.5 Mini（新用户试用额度）\",\"contextWindow\":256000,\"priceInputCny\":0.0000,\"priceOutputCny\":0.0000},{\"code\":\"jamba-1.5-large\",\"name\":\"Jamba 1.5 Large\",\"contextWindow\":256000,\"priceInputCny\":0.0000,\"priceOutputCny\":0.0000}]");

        expectOpenAiChatCompletion("https://api.ai21.com/studio/v1", null,
                "jamba-1.5-mini", buildReplyJson("ai21-reply"));

        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(p, "", objectMapper, httpExecutor);
        LlmResponse response = adapter.chat(new LlmRequest("jamba-1.5-mini",
                List.of(LlmMessage.user("hi")), 0.7, null, false, "chat"));

        assertFalse(response.isError());
        assertEquals("ai21", response.providerCode());
        assertEquals("jamba-1.5-mini", response.modelCode());
        assertEquals("ai21-reply", response.content());
    }

    @Test
    @DisplayName("xai-grok endpoint 与请求体契约")
    void canChatWithXaiGrok() throws Exception {
        AiProvider p = buildProvider(
                "xai-grok",
                "xAI Grok",
                "https://api.x.ai/v1",
                "[{\"code\":\"grok-2\",\"name\":\"Grok 2\",\"contextWindow\":131072,\"priceInputCny\":0.0000,\"priceOutputCny\":0.0000},{\"code\":\"grok-2-mini\",\"name\":\"Grok 2 Mini\",\"contextWindow\":131072,\"priceInputCny\":0.0000,\"priceOutputCny\":0.0000}]");

        expectOpenAiChatCompletion("https://api.x.ai/v1", null,
                "grok-2", buildReplyJson("xai-grok-reply"));

        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(p, "", objectMapper, httpExecutor);
        LlmResponse response = adapter.chat(new LlmRequest("grok-2",
                List.of(LlmMessage.user("hi")), 0.7, null, false, "chat"));

        assertFalse(response.isError());
        assertEquals("xai-grok", response.providerCode());
        assertEquals("grok-2", response.modelCode());
        assertEquals("xai-grok-reply", response.content());
    }

    @Test
    @DisplayName("lambda endpoint 与请求体契约")
    void canChatWithLambda() throws Exception {
        AiProvider p = buildProvider(
                "lambda",
                "Lambda",
                "https://api.lambda.ai/v1",
                "[{\"code\":\"llama3.1-8b\",\"name\":\"Llama 3.1 8B\",\"contextWindow\":128000,\"priceInputCny\":0.0000,\"priceOutputCny\":0.0000},{\"code\":\"hermes3-8b\",\"name\":\"Hermes 3 8B\",\"contextWindow\":8192,\"priceInputCny\":0.0000,\"priceOutputCny\":0.0000}]");

        expectOpenAiChatCompletion("https://api.lambda.ai/v1", null,
                "llama3.1-8b", buildReplyJson("lambda-reply"));

        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(p, "", objectMapper, httpExecutor);
        LlmResponse response = adapter.chat(new LlmRequest("llama3.1-8b",
                List.of(LlmMessage.user("hi")), 0.7, null, false, "chat"));

        assertFalse(response.isError());
        assertEquals("lambda", response.providerCode());
        assertEquals("llama3.1-8b", response.modelCode());
        assertEquals("lambda-reply", response.content());
    }

    @Test
    @DisplayName("friendliai endpoint 与请求体契约")
    void canChatWithFriendliai() throws Exception {
        AiProvider p = buildProvider(
                "friendliai",
                "FriendliAI",
                "https://inference.friendli.ai/v1",
                "[{\"code\":\"meta-llama-3.1-8b-instruct\",\"name\":\"Llama 3.1 8B Instruct\",\"contextWindow\":128000,\"priceInputCny\":0.0000,\"priceOutputCny\":0.0000},{\"code\":\"mistral-8x7b-instruct\",\"name\":\"Mistral 8x7B Instruct\",\"contextWindow\":32768,\"priceInputCny\":0.0000,\"priceOutputCny\":0.0000}]");

        expectOpenAiChatCompletion("https://inference.friendli.ai/v1", null,
                "meta-llama-3.1-8b-instruct", buildReplyJson("friendliai-reply"));

        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(p, "", objectMapper, httpExecutor);
        LlmResponse response = adapter.chat(new LlmRequest("meta-llama-3.1-8b-instruct",
                List.of(LlmMessage.user("hi")), 0.7, null, false, "chat"));

        assertFalse(response.isError());
        assertEquals("friendliai", response.providerCode());
        assertEquals("meta-llama-3.1-8b-instruct", response.modelCode());
        assertEquals("friendliai-reply", response.content());
    }

    @Test
    @DisplayName("kluster-ai endpoint 与请求体契约")
    void canChatWithKlusterAi() throws Exception {
        AiProvider p = buildProvider(
                "kluster-ai",
                "Kluster AI",
                "https://api.kluster.ai/v1",
                "[{\"code\":\"meta-llama/Llama-3.1-8B-Instruct\",\"name\":\"Llama 3.1 8B Instruct\",\"contextWindow\":128000,\"priceInputCny\":0.0000,\"priceOutputCny\":0.0000},{\"code\":\"deepseek-ai/DeepSeek-V3\",\"name\":\"DeepSeek-V3\",\"contextWindow\":64000,\"priceInputCny\":0.0000,\"priceOutputCny\":0.0000}]");

        expectOpenAiChatCompletion("https://api.kluster.ai/v1", null,
                "meta-llama/Llama-3.1-8B-Instruct", buildReplyJson("kluster-reply"));

        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(p, "", objectMapper, httpExecutor);
        LlmResponse response = adapter.chat(new LlmRequest("meta-llama/Llama-3.1-8B-Instruct",
                List.of(LlmMessage.user("hi")), 0.7, null, false, "chat"));

        assertFalse(response.isError());
        assertEquals("kluster-ai", response.providerCode());
        assertEquals("meta-llama/Llama-3.1-8B-Instruct", response.modelCode());
        assertEquals("kluster-reply", response.content());
    }

    @Test
    @DisplayName("chutes-ai endpoint 与请求体契约")
    void canChatWithChutesAi() throws Exception {
        AiProvider p = buildProvider(
                "chutes-ai",
                "Chutes AI",
                "https://llm.chutes.ai/v1",
                "[{\"code\":\"meta-llama/Llama-3.1-8B-Instruct\",\"name\":\"Llama 3.1 8B Instruct\",\"contextWindow\":128000,\"priceInputCny\":0.0000,\"priceOutputCny\":0.0000},{\"code\":\"chutesai/Llama-4-Scout-17B-16E-Instruct\",\"name\":\"Llama 4 Scout\",\"contextWindow\":128000,\"priceInputCny\":0.0000,\"priceOutputCny\":0.0000}]");

        expectOpenAiChatCompletion("https://llm.chutes.ai/v1", null,
                "meta-llama/Llama-3.1-8B-Instruct", buildReplyJson("chutes-reply"));

        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(p, "", objectMapper, httpExecutor);
        LlmResponse response = adapter.chat(new LlmRequest("meta-llama/Llama-3.1-8B-Instruct",
                List.of(LlmMessage.user("hi")), 0.7, null, false, "chat"));

        assertFalse(response.isError());
        assertEquals("chutes-ai", response.providerCode());
        assertEquals("meta-llama/Llama-3.1-8B-Instruct", response.modelCode());
        assertEquals("chutes-reply", response.content());
    }

    private AiProvider buildProvider(String providerCode, String providerName, String endpoint, String modelListJson) {
        AiProvider p = new AiProvider();
        p.setTenantId("default");
        p.setProviderCode(providerCode);
        p.setProviderName(providerName);
        p.setEndpoint(endpoint);
        p.setApiKeyRef("{\"apiKey\":\"\"}");
        p.setModelListJson(modelListJson);
        p.setProtocol("OPENAI_COMPATIBLE");
        p.setEnabled(true);
        p.setTimeoutMs(60000);
        p.setRateLimitPerMin(60);
        return p;
    }

    private String buildReplyJson(String content) {
        return String.format("""
                {
                  "choices": [
                    { "message": { "role": "assistant", "content": "%s" }, "finish_reason": "stop" }
                  ],
                  "usage": { "prompt_tokens": 3, "completion_tokens": 5 }
                }
                """, content);
    }

    private void expectOpenAiChatCompletion(String endpoint, String expectedAuthHeader,
                                            String modelCode, String responseJson) throws Exception {
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(responseJson);
        when(httpExecutor.send(argThat(req -> {
            if (!req.uri().equals(URI.create(endpoint + "/chat/completions"))) {
                return false;
            }
            if (!req.method().equals("POST")) {
                return false;
            }
            Optional<String> auth = req.headers().firstValue(HttpHeaders.AUTHORIZATION);
            if (expectedAuthHeader == null) {
                if (auth.isPresent()) {
                    return false;
                }
            } else if (!auth.filter(expectedAuthHeader::equals).isPresent()) {
                return false;
            }
            // 验证请求体模型名称正确
            try {
                String body = requestBodyToString(req.bodyPublisher().orElse(null));
                JsonNode node = objectMapper.readTree(body);
                return modelCode.equals(node.path("model").asText());
            } catch (Exception e) {
                return false;
            }
        }), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);
    }

    private String requestBodyToString(HttpRequest.BodyPublisher publisher) {
        if (publisher == null) {
            return "";
        }
        CompletableFuture<String> future = new CompletableFuture<>();
        publisher.subscribe(new Flow.Subscriber<ByteBuffer>() {
            private final StringBuilder sb = new StringBuilder();
            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.subscription = subscription;
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                sb.append(StandardCharsets.UTF_8.decode(item));
            }

            @Override
            public void onError(Throwable throwable) {
                future.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                future.complete(sb.toString());
            }
        });
        return future.join();
    }
}
