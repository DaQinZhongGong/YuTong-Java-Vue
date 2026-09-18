package com.yutong.ai.tool.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpMarketInstallParseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void configJson_shouldExposeHttpEndpoint() throws Exception {
        var node = mapper.readTree("{\"transport\":\"http\",\"endpoint\":\"http://127.0.0.1:3101/mcp\"}");
        assertEquals("http", node.path("transport").asText());
        assertEquals("http://127.0.0.1:3101/mcp", node.path("endpoint").asText());
    }

    @Test
    void stdioConfig_shouldKeepCommandArgs() throws Exception {
        var node = mapper.readTree("{\"transport\":\"stdio\",\"command\":\"npx\",\"args\":[\"-y\",\"@modelcontextprotocol/server-filesystem\",\"/data\"]}");
        assertEquals("stdio", node.path("transport").asText());
        assertEquals("npx", node.path("command").asText());
        assertTrue(node.path("args").isArray());
        assertEquals(3, node.path("args").size());
    }
}
