package com.yutong.ai.rag.loader;

import com.yutong.common.errorcode.ErrorCode;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 代码文件加载器。
 * 落点: 业界同类实现 CodeLoader + ADR 0005 P1-B。
 *
 * <p>支持 Java/Python/JS/TS/Go/Rust/C/C++/SQL/Shell/HTML/CSS/XML/YAML 等常见代码格式。
 * 策略: 按 UTF-8 逐行读取, 保留原始缩进与注释 (便于 RAG 检索代码语义)。
 * 限制: 最大 2MB 文本 (超过截断并告警)。
 */
@Component
public class CodeLoader implements DocumentLoader {

    private static final long MAX_TEXT_LENGTH = 2L * 1024 * 1024; // 2MB

    private static final Set<String> EXTENSIONS = Set.of(
            // Java 生态
            "java", "kt", "kts", "scala", "groovy",
            // Python
            "py", "pyw", "pyx",
            // JavaScript / TypeScript
            "js", "jsx", "ts", "tsx", "mjs", "cjs",
            // Go / Rust
            "go", "rs",
            // C / C++
            "c", "h", "cpp", "hpp", "cc", "cxx",
            // C#
            "cs",
            // SQL
            "sql",
            // Shell
            "sh", "bash", "zsh", "ps1", "bat", "cmd",
            // Web
            "html", "htm", "css", "scss", "less", "vue", "svelte",
            // 配置
            "xml", "yaml", "yml", "toml", "ini", "properties", "env",
            // 其他
            "rb", "php", "swift", "dart", "lua", "r", "pl", "ex", "exs"
    );

    @Override
    public String getLoaderType() {
        return LoaderType.CODE;
    }

    @Override
    public Set<String> supportedExtensions() {
        return EXTENSIONS;
    }

    @Override
    public String extract(InputStream inputStream, String filename) throws DocumentExtractException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            long totalChars = 0;
            boolean truncated = false;
            while ((line = reader.readLine()) != null) {
                if (totalChars + line.length() + 1 > MAX_TEXT_LENGTH) {
                    truncated = true;
                    break;
                }
                sb.append(line).append('\n');
                totalChars += line.length() + 1;
            }
            if (truncated) {
                sb.append("\n/* [截断] 文件超过 ").append(MAX_TEXT_LENGTH / 1024 / 1024)
                  .append("MB 限制, 已截断 */\n");
            }
        } catch (IOException e) {
            throw new DocumentExtractException(ErrorCode.KB_DOCUMENT_PARSE_FAILED,
                    "代码文件读取失败: " + filename + " - " + e.getMessage(), e);
        }
        return sb.toString();
    }
}
