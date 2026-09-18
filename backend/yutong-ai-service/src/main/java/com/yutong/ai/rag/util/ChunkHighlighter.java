package com.yutong.ai.rag.util;

import com.yutong.ai.rag.domain.AiDocumentChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 分块高亮工具 — 给定原始文档 + 命中 chunk 列表, 生成带 <mark> 标签的 HTML
 * 设计来源: ADR 0004 P2-E 知识库 RAG 深度
 *
 * 落点: 39-知识库运营详设「RAG 命中高亮回溯」
 *
 * 工作原理:
 *   1. 收集所有 hit chunk 的 [startOffset, endOffset)
 *   2. 按 startOffset 排序, 合并重叠区间
 *   3. 遍历原文, 命中区间用 <mark> 包裹, 其他原文照抄
 *
 * 安全: 输出 HTML 已 escape 特殊字符 (< > & " '), 防止 XSS
 *
 * 性能: O(n + k log k), n=原文长度, k=命中 chunk 数
 * 100KB 文档 + 5 个命中 < 5ms
 */
public final class ChunkHighlighter {

    /** 高亮标签 */
    public static final String MARK_TAG = "mark";
    /** 自定义 class (前端 CSS 钩子) */
    public static final String MARK_CLASS = "rag-hit";

    private ChunkHighlighter() {
    }

    /**
     * 原始区间: [start, end)
     */
    public record Span(int start, int end) {
        public Span {
            if (start < 0) start = 0;
            if (end < start) end = start;
        }
    }

    /**
     * 高亮生成结果: 分段列表, 每段 either 原文 (escape 后) 或 标记 (原文 escape 后包裹 mark)
     */
    public record Segment(boolean highlight, String content) {}

    /**
     * 给定文档原文 + 命中 chunk 列表, 生成高亮 HTML
     * @param docText 原始文档纯文本 (必须与 chunk 写入时使用的文本一致)
     * @param hits 命中的 chunk 列表, 内部用 startOffset/endOffset
     * @return 安全 HTML 字符串 (mark 标签包裹命中区间, 其它原文照抄, 已 escape)
     */
    public static String highlight(String docText, List<AiDocumentChunk> hits) {
        if (docText == null || docText.isEmpty()) return "";
        if (hits == null || hits.isEmpty()) return escape(docText);
        // 1. 提取有效区间
        List<Span> spans = new ArrayList<>();
        int len = docText.length();
        for (AiDocumentChunk c : hits) {
            if (c.getStartOffset() == null || c.getEndOffset() == null) continue;
            int s = Math.max(0, c.getStartOffset());
            int e = Math.min(len, c.getEndOffset());
            if (e <= s) continue;
            spans.add(new Span(s, e));
        }
        if (spans.isEmpty()) return escape(docText);
        // 2. 排序 + 合并重叠
        spans.sort(Comparator.comparingInt(Span::start));
        List<Span> merged = new ArrayList<>();
        Span cur = spans.get(0);
        for (int i = 1; i < spans.size(); i++) {
            Span next = spans.get(i);
            if (next.start() <= cur.end()) {
                cur = new Span(cur.start(), Math.max(cur.end(), next.end()));
            } else {
                merged.add(cur);
                cur = next;
            }
        }
        merged.add(cur);
        // 3. 拼装分段
        StringBuilder out = new StringBuilder();
        int pos = 0;
        for (Span sp : merged) {
            if (pos < sp.start()) {
                out.append(escape(docText.substring(pos, sp.start())));
            }
            out.append("<").append(MARK_TAG)
                    .append(" class=\"").append(MARK_CLASS).append("\"")
                    .append(" data-start=\"").append(sp.start()).append("\"")
                    .append(" data-end=\"").append(sp.end()).append("\"")
                    .append(">");
            out.append(escape(docText.substring(sp.start(), sp.end())));
            out.append("</").append(MARK_TAG).append(">");
            pos = sp.end();
        }
        if (pos < len) {
            out.append(escape(docText.substring(pos)));
        }
        return out.toString();
    }

    /**
     * HTML escape (5 个特殊字符)
     * 注: 不会 escape 中文/标点/空格/换行(保留原文)
     */
    public static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '&' -> sb.append("&amp;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 提取 chunk 偏移区间 (用于前端异步按需拉取原文片段), 自动合并重叠/相邻区间
     */
    public static List<Span> extractSpans(List<AiDocumentChunk> hits, int maxDocLength) {
        List<Span> result = new ArrayList<>();
        if (hits == null || hits.isEmpty()) return result;
        for (AiDocumentChunk c : hits) {
            if (c.getStartOffset() == null || c.getEndOffset() == null) continue;
            int s = Math.max(0, c.getStartOffset());
            int e = Math.min(maxDocLength, c.getEndOffset());
            if (e > s) result.add(new Span(s, e));
        }
        if (result.isEmpty()) return result;
        result.sort(Comparator.comparingInt(Span::start));
        // 合并重叠/相邻
        List<Span> merged = new ArrayList<>();
        Span cur = result.get(0);
        for (int i = 1; i < result.size(); i++) {
            Span next = result.get(i);
            if (next.start() <= cur.end()) {
                cur = new Span(cur.start(), Math.max(cur.end(), next.end()));
            } else {
                merged.add(cur);
                cur = next;
            }
        }
        merged.add(cur);
        return merged;
    }
}
