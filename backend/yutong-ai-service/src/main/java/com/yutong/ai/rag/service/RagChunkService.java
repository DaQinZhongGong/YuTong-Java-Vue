package com.yutong.ai.rag.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档分块服务。设计来源: 13-AI能力设计 RAG 分块策略 + 业界同类实现 Markdown 切分器。
 *
 * <p>两级策略:
 * <ol>
 *   <li>Markdown 感知: 按标题层级 (# ~ ####) 切分, 每个章节独立成块 (保留标题上下文)</li>
 *   <li>固定窗口兜底: 非 Markdown 内容或超长章节按 CHUNK_SIZE + OVERLAP 切分</li>
 * </ol>
 *
 * <p>RAG 质量收益: 标题感知切分保持语义完整性, 避免跨章节截断导致检索碎片化。
 */
@Service
public class RagChunkService {

    private static final int CHUNK_SIZE = 1000;
    private static final int OVERLAP = 150;

    /** Markdown 标题行: # ~ #### + 空格 + 内容 */
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,4})\\s+(.+)$", Pattern.MULTILINE);

    /** 代码块围栏: ``` 开始/结束 */
    private static final Pattern CODE_FENCE = Pattern.compile("^```", Pattern.MULTILINE);

    /**
     * 将文本分块。
     * 自动检测 Markdown: 含标题行 → 按标题切分; 否则 → 固定窗口切分。
     */
    public List<String> splitIntoChunks(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }
        // Excel 表格文本优先检测 (ExcelLoader 输出含 "# Sheet:" 会被 Markdown 标题正则误匹配)
        if (isExcelTable(text)) {
            return splitByExcelRows(text);
        }
        // Markdown 文本: 按标题切分 (即使短文本也保持章节结构)
        if (isMarkdown(text)) {
            return splitByMarkdownHeadings(text);
        }
        // 非 Markdown: 短文本单块, 长文本固定窗口
        if (text.length() <= CHUNK_SIZE) {
            chunks.add(text);
            return chunks;
        }
        return splitByFixedWindow(text);
    }

    /** 每个 Excel 行批次的最大行数 */
    private static final int EXCEL_ROWS_PER_CHUNK = 50;

    /**
     * 检测是否为 Excel 表格文本 (ExcelLoader 输出格式)。
     * 启发: 含 "# Sheet:" 标记 或 多行含 " | " 分隔符。
     */
    boolean isExcelTable(String text) {
        if (text.contains("# Sheet:")) return true;
        // 至少 3 行含 " | " 分隔符
        int pipeLines = 0;
        for (String line : text.split("\n")) {
            if (line.contains(" | ")) {
                pipeLines++;
                if (pipeLines >= 3) return true;
            }
        }
        return false;
    }

    /**
     * 按 Excel Sheet + 行批次切分。
     * 每个 Sheet 独立; Sheet 内按 EXCEL_ROWS_PER_CHUNK 行一批, 每批重复表头行。
     */
    List<String> splitByExcelRows(String text) {
        List<String> chunks = new ArrayList<>();
        String[] lines = text.split("\n");
        String currentSheet = "";
        List<String> headerRows = new ArrayList<>();
        List<String> dataRows = new ArrayList<>();
        boolean headerDone = false;

        for (String line : lines) {
            if (line.startsWith("# Sheet:")) {
                flushExcelChunk(chunks, currentSheet, headerRows, dataRows);
                currentSheet = line.substring("# Sheet:".length()).trim();
                headerRows.clear();
                dataRows.clear();
                headerDone = false;
            } else if (line.isBlank()) {
                // 空行: 若已有表头行则标记表头结束
                if (!headerRows.isEmpty()) headerDone = true;
            } else if (line.contains(" | ")) {
                if (!headerDone && headerRows.isEmpty()) {
                    headerRows.add(line);
                } else {
                    headerDone = true;
                    dataRows.add(line);
                }
                if (dataRows.size() >= EXCEL_ROWS_PER_CHUNK) {
                    flushExcelChunk(chunks, currentSheet, headerRows, dataRows);
                    dataRows.clear();
                }
            } else {
                headerDone = true;
                if (dataRows.size() >= EXCEL_ROWS_PER_CHUNK) {
                    flushExcelChunk(chunks, currentSheet, headerRows, dataRows);
                    dataRows.clear();
                }
                dataRows.add(line);
            }
        }
        flushExcelChunk(chunks, currentSheet, headerRows, dataRows);
        return chunks;
    }

    private void flushExcelChunk(List<String> chunks, String sheetName,
                                  List<String> headerRows, List<String> dataRows) {
        if (dataRows.isEmpty() && headerRows.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        if (!sheetName.isEmpty()) {
            sb.append("# Sheet: ").append(sheetName).append("\n\n");
        }
        for (String h : headerRows) {
            sb.append(h).append("\n");
        }
        if (!headerRows.isEmpty() && !dataRows.isEmpty()) {
            sb.append("\n");
        }
        for (String d : dataRows) {
            sb.append(d).append("\n");
        }
        String chunk = sb.toString().trim();
        if (!chunk.isEmpty()) {
            chunks.add(chunk);
        }
    }

    /**
     * 按 Markdown 标题层级切分。
     * 每个章节 = 标题 + 内容; 超长章节内部再按固定窗口切分。
     * 标题上下文会附加到子块前缀, 保持语义完整。
     */
    List<String> splitByMarkdownHeadings(String text) {
        List<String> chunks = new ArrayList<>();
        Matcher matcher = HEADING_PATTERN.matcher(text);

        // 收集所有标题位置
        List<int[]> headingPositions = new ArrayList<>(); // [start, end]
        List<String> headingTexts = new ArrayList<>();
        while (matcher.find()) {
            // 跳过代码块内的 # (简单启发: 前面有 ``` 未闭合)
            int pos = matcher.start();
            if (isInsideCodeBlock(text, pos)) continue;
            headingPositions.add(new int[]{pos, matcher.end()});
            headingTexts.add(matcher.group().trim());
        }

        if (headingPositions.isEmpty()) {
            return splitByFixedWindow(text);
        }

        // 按标题切分章节
        for (int i = 0; i < headingPositions.size(); i++) {
            int sectionStart = headingPositions.get(i)[0];
            int sectionEnd = (i + 1 < headingPositions.size())
                    ? headingPositions.get(i + 1)[0]
                    : text.length();
            String section = text.substring(sectionStart, sectionEnd).trim();
            if (section.isEmpty()) continue;

            if (section.length() <= CHUNK_SIZE) {
                chunks.add(section);
            } else {
                // 超长章节: 固定窗口切分, 每块前缀当前标题
                String heading = headingTexts.get(i);
                for (String sub : splitByFixedWindow(section)) {
                    if (!sub.startsWith(heading)) {
                        chunks.add(heading + "\n\n" + sub);
                    } else {
                        chunks.add(sub);
                    }
                }
            }
        }

        // 处理第一个标题之前的内容 (前言/摘要)
        int firstHeading = headingPositions.get(0)[0];
        if (firstHeading > 0) {
            String preamble = text.substring(0, firstHeading).trim();
            if (!preamble.isEmpty()) {
                chunks.addAll(splitByFixedWindow(preamble));
            }
        }

        return chunks;
    }

    /**
     * 固定窗口 + 重叠切分 (兜底)。
     */
    List<String> splitByFixedWindow(String text) {
        List<String> chunks = new ArrayList<>();
        if (text.length() <= CHUNK_SIZE) {
            chunks.add(text);
            return chunks;
        }
        int step = CHUNK_SIZE - OVERLAP;
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + CHUNK_SIZE, text.length());
            chunks.add(text.substring(start, end));
            if (end >= text.length()) break;
            start += step;
        }
        return chunks;
    }

    /**
     * 检测是否为 Markdown 文本。
     * 启发式: 至少 2 个标题行 (排除代码块内的)。
     */
    boolean isMarkdown(String text) {
        Matcher matcher = HEADING_PATTERN.matcher(text);
        int count = 0;
        while (matcher.find()) {
            if (!isInsideCodeBlock(text, matcher.start())) {
                count++;
                if (count >= 2) return true;
            }
        }
        return false;
    }

    /**
     * 判断位置是否在代码块内 (前面有奇数个 ```)。
     */
    private boolean isInsideCodeBlock(String text, int position) {
        Matcher matcher = CODE_FENCE.matcher(text);
        int fenceCount = 0;
        while (matcher.find() && matcher.start() < position) {
            fenceCount++;
        }
        return fenceCount % 2 == 1;
    }

    /**
     * 计算分块的 hash（SHA-256），用于去重。
     */
    public String computeChunkHash(String chunkText) {
        if (chunkText == null) {
            chunkText = "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(chunkText.getBytes(StandardCharsets.UTF_8));
            return toHexString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }

    private String toHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
