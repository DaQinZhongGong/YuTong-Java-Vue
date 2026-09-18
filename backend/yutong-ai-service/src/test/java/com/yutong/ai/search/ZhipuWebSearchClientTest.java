package com.yutong.ai.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 智谱 Web Search 客户端响应解析测试。
 * 覆盖:
 *  - parseResponse 成功解析
 *  - parseResponse 错误码失败关闭
 *  - parseResponse 空结果
 *  - search 参数校验
 */
class ZhipuWebSearchClientTest {

    private ZhipuWebSearchClient client;

    @BeforeEach
    void setUp() {
        client = new ZhipuWebSearchClient(
                new com.fasterxml.jackson.databind.ObjectMapper(),
                null, null);
    }

    @Test
    void parseResponse_success() {
        String json = """
                {"code":200,"data":{"search_result":[
                    {"title":"AI 新闻","content":"人工智能最新进展","link":"https://example.com/1","media":"TechNews","publish_date":"2026-01-01"},
                    {"title":"Java 25","content":"Java 25 新特性","link":"https://example.com/2","media":"Oracle","publish_date":"2026-01-02"}
                ]}}
                """;
        var response = client.parseResponse("AI", "req-1", json);
        assertEquals("AI", response.query());
        assertEquals(2, response.count());
        assertEquals("AI 新闻", response.results().get(0).title());
        assertEquals("https://example.com/1", response.results().get(0).link());
    }

    @Test
    void parseResponse_errorCode_throws() {
        String json = """
                {"code":400,"message":"invalid api key"}
                """;
        assertThrows(WebSearchClient.SearchException.class,
                () -> client.parseResponse("AI", "req-1", json));
    }

    @Test
    void parseResponse_emptyResults() {
        String json = """
                {"code":200,"data":{"search_result":[]}}
                """;
        var response = client.parseResponse("AI", "req-1", json);
        assertEquals(0, response.count());
        assertTrue(response.results().isEmpty());
    }

    @Test
    void search_blankQuery_throws() {
        assertThrows(WebSearchClient.SearchException.class,
                () -> client.search("", 10));
        assertThrows(WebSearchClient.SearchException.class,
                () -> client.search(null, 10));
    }

    @Test
    void search_tooLongQuery_throws() {
        String longQuery = "a".repeat(71);
        assertThrows(WebSearchClient.SearchException.class,
                () -> client.search(longQuery, 10));
    }

    @Test
    void parseResponse_alternateFormat() {
        // 兼容 search_result 在顶层的格式
        String json = """
                {"code":200,"search_result":[
                    {"title":"T","content":"C","link":"L","media":"M","publish_date":"D"}
                ]}
                """;
        var response = client.parseResponse("q", "r", json);
        assertEquals(1, response.count());
    }
}
