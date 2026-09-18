package com.yutong.ai.rag.loader;

import java.io.InputStream;
import java.util.Set;

/**
 * 文档加载器策略接口。
 * <p>
 * 每个 Loader 负责一种或多种扩展名的文本抽取；返回纯文本，由上层统一分片与向量化。
 * 约定: extract 返回的文本不应为 null，空文档返回空字符串；异常抛 DocumentExtractException。
 */
public interface DocumentLoader {

    /**
     * 装载器类型标识，对齐数据库 loader_type 约束：pdf/word/excel/csv/md/txt
     */
    String getLoaderType();

    /**
     * 该 Loader 支持的文件扩展名集合（小写，不含点）。
     */
    Set<String> supportedExtensions();

    /**
     * 是否支持该文件名（按后缀判断）。
     */
    default boolean supports(String filename) {
        if (filename == null) {
            return false;
        }
        String ext = extractExtension(filename);
        return ext != null && supportedExtensions().contains(ext.toLowerCase());
    }

    /**
     * 从输入流抽取文本。
     *
     * @param inputStream 文件流（调用方负责打开；实现方不应关闭，由 Factory 统一管理）
     * @param filename    原始文件名，用于日志与异常信息
     * @return 纯文本内容（trim 后可能为空）
     * @throws DocumentExtractException 解析失败
     */
    String extract(InputStream inputStream, String filename) throws DocumentExtractException;

    static String extractExtension(String filename) {
        if (filename == null) {
            return null;
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return null;
        }
        return filename.substring(dot + 1);
    }
}
