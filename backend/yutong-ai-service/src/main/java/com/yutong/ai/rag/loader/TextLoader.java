package com.yutong.ai.rag.loader;

import com.yutong.common.errorcode.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 纯文本加载器，支持 txt/json/xml/log 等文本类文件。
 * <p>
 * 策略: 尝试 UTF-8 读取，自动去除 BOM，按行拼接保留换行以利分块。
 */
@Component
public class TextLoader implements DocumentLoader {

    private static final Logger log = LoggerFactory.getLogger(TextLoader.class);

    private static final Set<String> EXTENSIONS = Set.of("txt", "log", "json", "xml", "yaml", "yml", "properties", "text");

    @Override
    public String getLoaderType() {
        return LoaderType.TXT;
    }

    @Override
    public Set<String> supportedExtensions() {
        return EXTENSIONS;
    }

    @Override
    public boolean supports(String filename) {
        if (filename == null) {
            return false;
        }
        String ext = DocumentLoader.extractExtension(filename);
        if (ext == null) {
            // 无扩展名视为纯文本回退
            return true;
        }
        String lower = ext.toLowerCase();
        // 文本类白名单 + 未知类型回退也走文本
        return EXTENSIONS.contains(lower) || LoaderType.fromExtension(lower).equals(LoaderType.TXT);
    }

    @Override
    public String extract(InputStream inputStream, String filename) throws DocumentExtractException {
        if (inputStream == null) {
            throw new DocumentExtractException(ErrorCode.KB_DOCUMENT_PARSE_FAILED, "输入流为空: " + filename);
        }
        try {
            // 探测 BOM: UTF-8 BOM = EF BB BF
            inputStream.mark(4);
            byte[] bom = new byte[3];
            int read = inputStream.read(bom);
            boolean hasBom = read == 3 && (bom[0] & 0xFF) == 0xEF && (bom[1] & 0xFF) == 0xBB && (bom[2] & 0xFF) == 0xBF;
            if (!hasBom) {
                inputStream.reset();
            }
            Charset charset = StandardCharsets.UTF_8;
            StringBuilder sb = new StringBuilder(8192);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, charset))) {
                char[] buf = new char[8192];
                int len;
                while ((len = reader.read(buf)) != -1) {
                    sb.append(buf, 0, len);
                }
            }
            String text = sb.toString();
            if (log.isDebugEnabled()) {
                log.debug("TextLoader extracted filename={}, chars={}", filename, text.length());
            }
            return text;
        } catch (IOException e) {
            throw new DocumentExtractException(ErrorCode.KB_DOCUMENT_PARSE_FAILED,
                    "文本文件解析失败: " + filename, e);
        }
    }
}
