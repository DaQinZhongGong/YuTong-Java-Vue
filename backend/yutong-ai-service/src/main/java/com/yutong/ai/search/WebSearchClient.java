package com.yutong.ai.search;

import java.util.List;

/**
 * Web Search 客户端接口。
 * 联网搜索客户端。
 * 实现: ZhipuWebSearchClient (智谱 Web Search API)。
 */
public interface WebSearchClient {

    /**
     * 执行网页搜索。
     *
     * @param query       搜索关键词 (必填, ≤70 字符)
     * @param resultCount 返回结果数 (1-50, 默认 10)
     * @return 搜索结果
     * @throws SearchException 调用失败时抛出 (失败关闭)
     */
    SearchResponse search(String query, int resultCount);

    /**
     * 搜索响应。
     */
    record SearchResponse(
            String query,
            String requestId,
            int count,
            List<SearchResult> results
    ) {
    }

    /**
     * 单条搜索结果。
     */
    record SearchResult(
            String title,
            String content,
            String link,
            String media,
            String publishDate
    ) {
    }

    /**
     * 搜索异常 (失败关闭)。
     */
    class SearchException extends RuntimeException {
        public SearchException(String message) {
            super(message);
        }

        public SearchException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
