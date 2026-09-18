package com.yutong.ai.rag.loader;

import com.yutong.common.errorcode.ErrorCode;
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
 * Markdown 加载器 — 首版直接保留原文，由 RagChunkService 按段落/标题分块。
 * <p>
 * 不做 markdown→html 转换，保留标题层级与代码块标记，利于后续结构化分块优化。
 */
@Component
public class MdLoader implements DocumentLoader {

    private static final Logger log = LoggerFactory.getLogger(MdLoader.class);

    private static final Set<String> EXTENSIONS = Set.of("md", "markdown");

    @Override
    public String getLoaderType() {
        return LoaderType.MD;
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
        try {
            StringBuilder sb = new StringBuilder(8192);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            String text = sb.toString();
            if (text.isBlank()) {
                log.warn("MdLoader empty content: {}", filename);
            } else if (log.isDebugEnabled()) {
                log.debug("MdLoader extracted filename={}, chars={}", filename, text.length());
            }
            return text;
        } catch (IOException e) {
            throw new DocumentExtractException(ErrorCode.KB_DOCUMENT_PARSE_FAILED,
                    "Markdown 文件解析失败: " + filename, e);
        }
    }
}
