package com.yutong.ai.rag.loader;

import com.yutong.common.errorcode.ErrorCode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * PDF 加载器 — 使用 PDFBox 3.x。
 * <p>
 * 生产级处理:
 * <ul>
 *   <li>加密/损坏 PDF 抛 KB-500002，带原因</li>
 *   <li>空页/扫描件无文本返回空字符串，由上游判空</li>
 *   <li>限制最大 50MB 解析（上游 FileService 已限 100MB），此处仅文本长度告警</li>
 * </ul>
 */
@Component
public class PdfLoader implements DocumentLoader {

    private static final Logger log = LoggerFactory.getLogger(PdfLoader.class);

    private static final Set<String> EXTENSIONS = Set.of("pdf");

    @Override
    public String getLoaderType() {
        return LoaderType.PDF;
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
            // PDFBox 3.x: Loader.loadPDF(byte[]) ; 回退到兼容的 PDDocument.load(InputStream)
            byte[] bytes = inputStream.readAllBytes();
            if (bytes.length == 0) {
                log.warn("PdfLoader empty file: {}", filename);
                return "";
            }
            // 50MB 文本告警阈值，仍尝试解析
            if (bytes.length > 50 * 1024 * 1024) {
                log.warn("PdfLoader large file bytes={}, filename={}", bytes.length, filename);
            }
            try (PDDocument document = loadPdf(bytes, filename)) {
                if (document.isEncrypted()) {
                    throw new DocumentExtractException(ErrorCode.KB_DOCUMENT_PARSE_FAILED,
                            "PDF 已加密无法解析: " + filename);
                }
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                // 避免单次超大文本 OOM，分段抽取由 PDFTextStripper 内部处理
                String text = stripper.getText(document);
                if (text == null) {
                    text = "";
                }
                if (log.isDebugEnabled()) {
                    log.debug("PdfLoader extracted filename={}, pages={}, chars={}",
                            filename, document.getNumberOfPages(), text.length());
                }
                if (text.isBlank()) {
                    log.warn("PdfLoader no extractable text (may be scanned image): {}", filename);
                }
                return text;
            }
        } catch (DocumentExtractException dee) {
            throw dee;
        } catch (IOException e) {
            throw new DocumentExtractException(ErrorCode.KB_DOCUMENT_PARSE_FAILED,
                    "PDF 文件解析失败: " + filename, e);
        } catch (Exception e) {
            throw new DocumentExtractException(ErrorCode.KB_DOCUMENT_PARSE_FAILED,
                    "PDF 文件解析异常: " + filename, e);
        }
    }

    /**
     * PDFBox 3.x 加载入口 — 仅使用 Loader.loadPDF。
     */
    private PDDocument loadPdf(byte[] bytes, String filename) throws IOException {
        return Loader.loadPDF(bytes);
    }
}
