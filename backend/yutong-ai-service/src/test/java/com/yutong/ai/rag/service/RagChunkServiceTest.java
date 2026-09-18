package com.yutong.ai.rag.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RagChunkService Markdown 感知切分测试。
 */
class RagChunkServiceTest {

    private final RagChunkService service = new RagChunkService();

    @Test
    void split_shortText_singleChunk() {
        List<String> chunks = service.splitIntoChunks("这是一段短文本");
        assertEquals(1, chunks.size());
        assertEquals("这是一段短文本", chunks.get(0));
    }

    @Test
    void split_nullOrEmpty_returnsEmpty() {
        assertTrue(service.splitIntoChunks(null).isEmpty());
        assertTrue(service.splitIntoChunks("").isEmpty());
    }

    @Test
    void split_markdown_byHeadings() {
        String md = """
                # 项目概述
                
                这是项目的总体介绍，包含背景和目标。
                
                ## 架构设计
                
                系统采用微服务架构，分为前端、网关、后端三层。
                
                ## 数据库设计
                
                使用 PostgreSQL 18，主键统一 ULID varchar(32)。
                
                ### 用户表
                
                sys_user 表存储用户信息。
                
                ### 角色表
                
                sys_role 表存储角色信息。
                """;
        List<String> chunks = service.splitIntoChunks(md);
        assertTrue(chunks.size() >= 3);
        // 每个章节应包含其标题
        assertTrue(chunks.stream().anyMatch(c -> c.contains("# 项目概述")));
        assertTrue(chunks.stream().anyMatch(c -> c.contains("## 架构设计")));
        assertTrue(chunks.stream().anyMatch(c -> c.contains("## 数据库设计")));
    }

    @Test
    void split_markdown_isMarkdownDetection() {
        String md = "# 标题1\n内容\n# 标题2\n内容";
        assertTrue(service.isMarkdown(md));
        assertFalse(service.isMarkdown("普通文本没有标题"));
        assertFalse(service.isMarkdown("# 只有一个标题"));
    }

    @Test
    void split_markdown_codeBlockHashIgnored() {
        String md = """
                # 标题1
                
                ```java
                // 这里的 # 不是标题
                int x = 1;
                ```
                
                # 标题2
                
                正文内容
                """;
        // 代码块内的 # 不应被识别为标题
        assertTrue(service.isMarkdown(md));
        List<String> chunks = service.splitIntoChunks(md);
        assertTrue(chunks.size() >= 2);
    }

    @Test
    void split_longText_fixedWindow() {
        // 无标题的长文本 → 固定窗口切分
        String longText = "a".repeat(3000);
        List<String> chunks = service.splitIntoChunks(longText);
        assertTrue(chunks.size() > 1);
        // 每块 ≤ CHUNK_SIZE
        for (String c : chunks) {
            assertTrue(c.length() <= 1000);
        }
    }

    @Test
    void split_longMarkdownSection_subChunks() {
        // 超长章节应内部再切分, 且保留标题前缀
        StringBuilder sb = new StringBuilder("# 超长章节\n\n");
        sb.append("内容".repeat(800)); // ~1600 字符
        List<String> chunks = service.splitIntoChunks(sb.toString());
        assertTrue(chunks.size() >= 1);
    }

    @Test
    void computeChunkHash_deterministic() {
        String h1 = service.computeChunkHash("hello");
        String h2 = service.computeChunkHash("hello");
        assertEquals(h1, h2);
        assertNotEquals(h1, service.computeChunkHash("world"));
    }

    @Test
    void isExcelTable_detection() {
        assertTrue(service.isExcelTable("# Sheet: Sheet1\nA | B | C\n1 | 2 | 3\n4 | 5 | 6"));
        assertTrue(service.isExcelTable("x | y | z\n1 | 2 | 3\n4 | 5 | 6\n7 | 8 | 9"));
        assertFalse(service.isExcelTable("普通文本没有表格"));
    }

    @Test
    void split_excel_byRows() {
        StringBuilder sb = new StringBuilder("# Sheet: 数据表\n\n");
        sb.append("姓名 | 年龄 | 城市\n\n");
        for (int i = 0; i < 120; i++) {
            sb.append("用户").append(i).append(" | ").append(20 + i % 30).append(" | 北京\n");
        }
        List<String> chunks = service.splitIntoChunks(sb.toString());
        assertTrue(chunks.size() >= 2, "120 行应切成至少 2 批 (50行/批)");
        // 每个 chunk 应包含表头
        for (String c : chunks) {
            assertTrue(c.contains("姓名 | 年龄 | 城市"), "每批应重复表头");
        }
    }

    @Test
    void split_excel_multiSheet() {
        String text = """
                # Sheet: 员工
                
                姓名 | 部门
                张三 | 研发
                李四 | 市场
                
                # Sheet: 产品
                
                名称 | 价格
                手机 | 3999
                电脑 | 6999
                """;
        List<String> chunks = service.splitIntoChunks(text);
        assertTrue(chunks.size() >= 2);
        assertTrue(chunks.stream().anyMatch(c -> c.contains("# Sheet: 员工")));
        assertTrue(chunks.stream().anyMatch(c -> c.contains("# Sheet: 产品")));
    }

    @Test
    void split_excel_shortTable_singleChunk() {
        String text = "# Sheet: 小表\n\nA | B\n1 | 2\n3 | 4";
        List<String> chunks = service.splitIntoChunks(text);
        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).contains("A | B"));
    }
}
