package com.yutong.ai.chat.service.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.ai.gateway.domain.AiProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 真实 LLM 端到端测试。
 * 默认通过 @EnabledIf 检查 pollinations 网络可达性；若可达但实际调用失败，则测试失败。
 * 设计来源: P6-02 免费 LLM 供应商集成
 */
@EnabledIf("networkAvailable")
class RealLlmEndToEndTest {

    private static final String POLLINATIONS_ENDPOINT = "https://text.pollinations.ai/openai/v1";
    private static final String PROVIDER_CODE = "pollinations";
    private static final String MODEL_CODE = "openai";

    static boolean networkAvailable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("text.pollinations.ai", 443),
                    (int) Duration.ofSeconds(5).toMillis());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    @DisplayName("pollinations openai 模型真实端到端调用")
    void pollinationsOpenAiChatReturnsContent() {
        AiProvider provider = new AiProvider();
        provider.setProviderCode(PROVIDER_CODE);
        provider.setEndpoint(POLLINATIONS_ENDPOINT);
        provider.setProtocol("OPENAI_COMPATIBLE");
        provider.setTimeoutMs(30000);
        provider.setApiKeyRef("");

        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(
                provider, "", new ObjectMapper());

        LlmRequest request = new LlmRequest(MODEL_CODE,
                List.of(LlmMessage.user("Say hello in one short sentence.")),
                0.7, 256, false, "e2e-smoke");

        LlmResponse response = adapter.chat(request);

        assertFalse(response.isError(),
                () -> "pollinations E2E 调用失败: " +
                        (response.error() != null ? response.error().getMessage() : "unknown"));
        assertNotNull(response.content(), "返回内容不能为空");
        assertFalse(response.content().isBlank(), "返回内容不能为空白");
        assertEquals(PROVIDER_CODE, response.providerCode(), "providerCode 应被正确设置");
        assertEquals(MODEL_CODE, response.modelCode(), "modelCode 应被正确设置");
        assertNotNull(response.tokenInput(), "输入 token 数不能为空");
        assertNotNull(response.tokenOutput(), "输出 token 数不能为空");
        assertTrue(response.tokenInput() >= 0, "输入 token 数应 >= 0");
        assertTrue(response.tokenOutput() >= 0, "输出 token 数应 >= 0");

        System.out.println("pollinations E2E response: " + response.content());
    }
}
