package com.yutong.ai.rag.util;

import com.yutong.ai.rag.domain.AiDocumentChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chunk 高亮单元测试
 * 设计来源: ADR 0004 P2-E 知识库 RAG 深度
 *
 * 覆盖:
 *  - 单命中区间
 *  - 多命中区间
 *  - 重叠区间合并
 *  - 边界 (文档头/尾)
 *  - 越界 offset (clamp 到文档长度)
 *  - null/空 chunk (空偏移字段) 跳过
 *  - XSS escape
 *  - 性能 / 边界 (1 个命中跨整文档)
 */
class ChunkHighlighterTest {

    private static AiDocumentChunk chunk(int start, int end) {
        AiDocumentChunk c = new AiDocumentChunk();
        c.setStartOffset(start);
        c.setEndOffset(end);
        c.setChunkText("placeholder");
        return c;
    }

    @Test
    void highlight_singleHit_wrapsInMark() {
        String doc = "人工智能是计算机科学的一个分支。";
        // 命中 "人工智能"
        String html = ChunkHighlighter.highlight(doc, List.of(chunk(0, 4)));
        assertTrue(html.contains("<mark class=\"rag-hit\" data-start=\"0\" data-end=\"4\">人工智能</mark>"));
        assertTrue(html.contains("是计算机科学的一个分支。"));
    }

    @Test
    void highlight_multipleHits_preservesOrder() {
        String doc = "AB12CD34EF56";
        // 命中 A(0-1), C(4-5), E(8-9)
        String html = ChunkHighlighter.highlight(doc, List.of(chunk(0, 1), chunk(4, 5), chunk(8, 9)));
        assertEquals("<mark class=\"rag-hit\" data-start=\"0\" data-end=\"1\">A</mark>B12"
                + "<mark class=\"rag-hit\" data-start=\"4\" data-end=\"5\">C</mark>D34"
                + "<mark class=\"rag-hit\" data-start=\"8\" data-end=\"9\">E</mark>F56", html);
    }

    @Test
    void highlight_overlappingSpans_mergedIntoOne() {
        String doc = "0123456789ABCDEF";
        // 两个区间 [0,5) 和 [3,8) 重叠 [3,5), 合并为 [0,8)
        String html = ChunkHighlighter.highlight(doc, List.of(chunk(0, 5), chunk(3, 8)));
        assertTrue(html.contains("<mark class=\"rag-hit\" data-start=\"0\" data-end=\"8\">01234567</mark>"));
        // 不应有两个独立 mark
        assertEquals(1, html.split("<mark ").length - 1);
    }

    @Test
    void highlight_adjacentSpans_merged() {
        // [0,5) 和 [5,8) 紧邻, 合并为 [0,8)
        String doc = "0123456789AB";
        String html = ChunkHighlighter.highlight(doc, List.of(chunk(0, 5), chunk(5, 8)));
        assertTrue(html.contains("data-start=\"0\" data-end=\"8\""));
    }

    @Test
    void highlight_clampsOutOfBoundsOffsets() {
        String doc = "hello";
        // endOffset 超过 doc.length 应被 clamp
        String html = ChunkHighlighter.highlight(doc, List.of(chunk(0, 100)));
        assertTrue(html.contains("<mark class=\"rag-hit\" data-start=\"0\" data-end=\"5\">hello</mark>"));
    }

    @Test
    void highlight_negativeOffsetClampedToZero() {
        String doc = "0123456789";
        // startOffset 负数 clamp 到 0
        String html = ChunkHighlighter.highlight(doc, List.of(chunk(-5, 3)));
        assertTrue(html.contains("data-start=\"0\" data-end=\"3\""));
    }

    @Test
    void highlight_invertedRangeDropped() {
        // endOffset < startOffset 应跳过
        String doc = "0123456789";
        String html = ChunkHighlighter.highlight(doc, List.of(chunk(5, 3)));
        assertFalse(html.contains("<mark"));
        assertEquals("0123456789", html);
    }

    @Test
    void highlight_nullOffsetChunksSkipped() {
        String doc = "hello world";
        AiDocumentChunk c1 = new AiDocumentChunk();
        c1.setStartOffset(null);
        c1.setEndOffset(null);
        AiDocumentChunk c2 = chunk(0, 5);
        String html = ChunkHighlighter.highlight(doc, List.of(c1, c2));
        assertTrue(html.contains("<mark class=\"rag-hit\" data-start=\"0\" data-end=\"5\">hello</mark>"));
    }

    @Test
    void highlight_emptyHitsList_returnsEscapedDoc() {
        String doc = "hello";
        String html = ChunkHighlighter.highlight(doc, List.of());
        assertEquals("hello", html);
    }

    @Test
    void highlight_nullHitsList_returnsEscapedDoc() {
        String doc = "hello";
        String html = ChunkHighlighter.highlight(doc, null);
        assertEquals("hello", html);
    }

    @Test
    void highlight_nullOrEmptyDoc_returnsEmpty() {
        assertEquals("", ChunkHighlighter.highlight(null, List.of(chunk(0, 1))));
        assertEquals("", ChunkHighlighter.highlight("", List.of(chunk(0, 1))));
    }

    @Test
    void escape_handlesXssPayloads() {
        assertEquals("&lt;script&gt;alert(1)&lt;/script&gt;", ChunkHighlighter.escape("<script>alert(1)</script>"));
        assertEquals("a &amp; b", ChunkHighlighter.escape("a & b"));
        assertEquals("&quot;hi&quot;", ChunkHighlighter.escape("\"hi\""));
        assertEquals("it&#39;s", ChunkHighlighter.escape("it's"));
    }

    @Test
    void highlight_xssInChunkTextIsEscaped() {
        String doc = "<script>alert(1)</script>";
        String html = ChunkHighlighter.highlight(doc, List.of(chunk(0, doc.length())));
        // < > 已被 escape, 但 mark 标签保留
        assertTrue(html.contains("<mark"));
        assertTrue(html.contains("&lt;script&gt;"));
        assertFalse(html.contains("<script>"), "原始 <script> 不应出现在输出");
    }

    @Test
    void highlight_chineseContent_preservesChars() {
        String doc = "北京是中华人民共和国的首都。";
        String html = ChunkHighlighter.highlight(doc, List.of(chunk(0, 2)));
        assertTrue(html.contains("<mark class=\"rag-hit\" data-start=\"0\" data-end=\"2\">北京</mark>"));
        assertTrue(html.contains("是中华人民共和国的首都。"));
    }

    @Test
    void extractSpans_returnsValidSpans() {
        String doc = "0123456789";
        // chunks: (0,3), (-2,5)→(0,5), (7,100)→(7,10), (5,3)→drop
        // 合并后: (0,5) 与 (7,10)
        List<ChunkHighlighter.Span> spans = ChunkHighlighter.extractSpans(
                List.of(chunk(0, 3), chunk(-2, 5), chunk(7, 100), chunk(5, 3)),
                doc.length());
        assertEquals(2, spans.size());
        assertEquals(0, spans.get(0).start());
        assertEquals(5, spans.get(0).end(), "(0,3) 与 (0,5) 合并");
        assertEquals(7, spans.get(1).start());
        assertEquals(10, spans.get(1).end());
    }
}
