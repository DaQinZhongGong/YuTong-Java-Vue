package com.yutong.ai.rag.loader;

import com.yutong.common.errorcode.ErrorCode;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * CSV 加载器 — 使用 commons-csv 解析，行为:
 * <ul>
 *   <li>首行若为表头则保留，行间以 " | " 连接列，利于检索</li>
 *   <li>空行跳过，每行追加换行</li>
 *   <li>BOM 与编码按 UTF-8 处理</li>
 * </ul>
 */
@Component
public class CsvLoader implements DocumentLoader {

    private static final Logger log = LoggerFactory.getLogger(CsvLoader.class);

    private static final Set<String> EXTENSIONS = Set.of("csv");

    @Override
    public String getLoaderType() {
        return LoaderType.CSV;
    }

    @Override
    public Set<String> supportedExtensions() {
        return EXTENSIONS;
    }

    @Override
    public String extract(InputStream inputStream, String filename) throws DocumentExtractException {
        if (inputStream == null) {
            throw new DocumentExtractException(ErrorCode.KB_DOCUMENT_PARSE_FAILED, "输入流为空: " + filename);
        }
        // 使用 BOM-aware 包装: commons-csv 已处理 BOM，但需用 InputStreamReader UTF-8
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            // 去除可能的 UTF-8 BOM 首字符
            reader.mark(1);
            int first = reader.read();
            if (first != 0xFEFF) {
                reader.reset();
            }
            CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setIgnoreSurroundingSpaces(true)
                    .setTrim(true)
                    .setIgnoreEmptyLines(true)
                    .build();
            StringBuilder sb = new StringBuilder(8192);
            try (CSVParser parser = format.parse(reader)) {
                int rowCount = 0;
                for (CSVRecord record : parser) {
                    if (record.size() == 0) {
                        continue;
                    }
                    boolean allBlank = true;
                    for (String v : record) {
                        if (v != null && !v.isBlank()) {
                            allBlank = false;
                            break;
                        }
                    }
                    if (allBlank) {
                        continue;
                    }
                    // 行内列以 " | " 连接，保留可检索性
                    for (int i = 0; i < record.size(); i++) {
                        if (i > 0) {
                            sb.append(" | ");
                        }
                        String val = record.get(i);
                        if (val != null) {
                            sb.append(val.trim());
                        }
                    }
                    sb.append('\n');
                    rowCount++;
                    // 防止超大 CSV 内存爆炸：限制 50000 行
                    if (rowCount >= 50000) {
                        log.warn("CsvLoader truncated at 50000 rows: {}", filename);
                        sb.append("\n[truncated at 50000 rows]\n");
                        break;
                    }
                }
            }
            String text = sb.toString();
            if (log.isDebugEnabled()) {
                log.debug("CsvLoader extracted filename={}, chars={}", filename, text.length());
            }
            return text;
        } catch (IOException e) {
            throw new DocumentExtractException(ErrorCode.KB_DOCUMENT_PARSE_FAILED,
                    "CSV 文件解析失败: " + filename, e);
        }
    }
}
