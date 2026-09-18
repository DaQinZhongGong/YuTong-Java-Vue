package com.yutong.ai.chat.service.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.ai.gateway.domain.AiProvider;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CozeAdapterParseTest {

    @Test
    void resolveBotId_prefersConfigJson() {
        AiProvider provider = new AiProvider();
        provider.setEndpoint("https://api.coze.cn");
        provider.setConfigJson("{\"bot_id\":\"bot-from-config\"}");
        CozeAdapter adapter = new CozeAdapter(provider, "k", new ObjectMapper());
        Object id = ReflectionTestUtils.invokeMethod(adapter, "resolveBotId", "fallback-model");
        assertEquals("bot-from-config", id);
    }

    @Test
    void extractStreamDelta_readsNestedContent() throws Exception {
        AiProvider provider = new AiProvider();
        provider.setEndpoint("https://api.coze.cn");
        CozeAdapter adapter = new CozeAdapter(provider, "k", new ObjectMapper());
        var node = new ObjectMapper().readTree("{\"data\":{\"type\":\"answer\",\"content\":\"hello\"}}");
        Object delta = ReflectionTestUtils.invokeMethod(adapter, "extractStreamDelta", node);
        assertEquals("hello", String.valueOf(delta));
        assertTrue(adapter.supports("COZE"));
    }
}
