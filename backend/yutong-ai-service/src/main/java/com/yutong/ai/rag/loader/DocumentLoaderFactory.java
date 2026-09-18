package com.yutong.ai.rag.loader;

import com.yutong.common.errorcode.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档加载器工厂 — 根据 loaderType 显式指定或文件名后缀自动路由。
 * <p>
 * 策略优先级:
 * <ol>
 *   <li>显式 loaderType (如前端传入 loaderType=pdf) 优先</li>
 *   <li>文件名后缀映射到 loaderType</li>
 *   <li>兜底 TextLoader</li>
 * </ol>
 * 生产级: 支持按 loaderType 与扩展名两维索引，未命中抛 KB-415001。
 */
@Component
public class DocumentLoaderFactory {

    private static final Logger log = LoggerFactory.getLogger(DocumentLoaderFactory.class);

    private final Map<String, DocumentLoader> byLoaderType = new HashMap<>();
    private final Map<String, DocumentLoader> byExtension = new HashMap<>();
    private final DocumentLoader fallbackLoader;

    public DocumentLoaderFactory(List<DocumentLoader> loaders) {
        DocumentLoader fallback = null;
        for (DocumentLoader loader : loaders) {
            String type = loader.getLoaderType();
            if (type != null) {
                byLoaderType.put(type.toLowerCase(), loader);
            }
            for (String ext : loader.supportedExtensions()) {
                byExtension.put(ext.toLowerCase(), loader);
            }
            if (loader instanceof TextLoader) {
                fallback = loader;
            }
        }
        // 确保有兜底；若未注入 TextLoader，则选第一个
        if (fallback == null && !loaders.isEmpty()) {
            fallback = loaders.get(0);
        }
        this.fallbackLoader = fallback;
        log.info("DocumentLoaderFactory init: byType={}, byExt={}, fallback={}",
                byLoaderType.keySet(), byExtension.keySet(),
                fallbackLoader != null ? fallbackLoader.getLoaderType() : "none");
    }

    /**
     * 解析 loader。
     *
     * @param filename           原始文件名（可为空）
     * @param loaderTypeHint     前端/DB 传入的 loader_type，可为空
     * @return 匹配的 Loader，未命中则抛 KB_DOCUMENT_TYPE_UNSUPPORTED
     */
    public DocumentLoader resolve(String filename, String loaderTypeHint) {
        // 1. 显式 loaderType 优先
        String normalized = LoaderType.normalize(loaderTypeHint);
        if (normalized != null) {
            DocumentLoader byType = byLoaderType.get(normalized);
            if (byType != null) {
                return byType;
            }
            // json/code 回退到文本
            if (LoaderType.JSON.equals(normalized) || LoaderType.CODE.equals(normalized)) {
                DocumentLoader text = byLoaderType.get(LoaderType.TXT);
                if (text != null) {
                    return text;
                }
            }
            log.warn("Unknown loaderType hint: {}, filename={}", loaderTypeHint, filename);
        }

        // 2. 按扩展名路由
        if (filename != null) {
            String ext = DocumentLoader.extractExtension(filename);
            if (ext != null) {
                String lower = ext.toLowerCase();
                DocumentLoader byExt = byExtension.get(lower);
                if (byExt != null) {
                    return byExt;
                }
                // 扩展名映射到 loaderType 再查
                String mappedType = LoaderType.fromExtension(lower);
                DocumentLoader mapped = byLoaderType.get(mappedType);
                if (mapped != null) {
                    return mapped;
                }
                // 已知扩展但无专用 Loader → 按类型抛不支持
                // 未知扩展回退到文本（而非抛错），提升容错
                if (isKnownExtension(lower)) {
                    throw new DocumentExtractException(ErrorCode.KB_DOCUMENT_TYPE_UNSUPPORTED,
                            "不支持的文档类型: ." + lower + " (文件: " + filename + ")");
                }
            }
        }

        // 3. 无 loaderType 也无扩展名 → 若有 hint 为空则抛或回退文本
        if (normalized == null && (filename == null || DocumentLoader.extractExtension(filename) == null)) {
            // 无任何信息时，若调用方未指定类型，视为不支持
            if (fallbackLoader != null) {
                return fallbackLoader;
            }
            throw new DocumentExtractException(ErrorCode.KB_DOCUMENT_TYPE_UNSUPPORTED,
                    "无法识别文档类型: filename=" + filename + ", loaderType=" + loaderTypeHint);
        }

        // 4. 兜底文本
        if (fallbackLoader != null) {
            log.debug("DocumentLoaderFactory fallback to {} for filename={}, hint={}",
                    fallbackLoader.getLoaderType(), filename, loaderTypeHint);
            return fallbackLoader;
        }
        throw new DocumentExtractException(ErrorCode.KB_DOCUMENT_TYPE_UNSUPPORTED,
                "无可用文档加载器: filename=" + filename + ", loaderType=" + loaderTypeHint);
    }

    /**
     * 一站式抽取：路由 + 抽取，输入流由调用方关闭。
     */
    public String extract(InputStream inputStream, String filename, String loaderTypeHint) {
        DocumentLoader loader = resolve(filename, loaderTypeHint);
        log.info("DocumentLoaderFactory routing filename={}, loaderTypeHint={} -> loader={}",
                filename, loaderTypeHint, loader.getLoaderType());
        return loader.extract(inputStream, filename);
    }

    private boolean isKnownExtension(String ext) {
        // 被明确识别为文档类但暂未实现 loader 时应抛不支持
        return switch (ext) {
            case "pdf", "doc", "docx", "xls", "xlsx", "csv", "md", "markdown", "txt", "log", "json" -> true;
            default -> false;
        };
    }

    public Map<String, DocumentLoader> getByLoaderType() {
        return Map.copyOf(byLoaderType);
    }
}
