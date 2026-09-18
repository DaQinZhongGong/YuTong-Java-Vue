package com.yutong.ai.rag.loader;

import com.yutong.common.errorcode.ErrorCode;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.Set;

/**
 * Word 加载器 — 支持 docx (XWPF) 与 doc (HWPF) 。
 * <p>
 * 策略:
 * <ul>
 *   <li>docx: 段落 + 表格文本拼接，保留换行</li>
 *   <li>doc: 使用 WordExtractor 全文抽取</li>
 *   <li>异常走 DocumentExtractException，带 KB-500002</li>
 * </ul>
 */
@Component
public class DocxLoader implements DocumentLoader {

    private static final Logger log = LoggerFactory.getLogger(DocxLoader.class);

    private static final Set<String> EXTENSIONS = Set.of("doc", "docx");

    @Override
    public String getLoaderType() {
        return LoaderType.WORD;
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
        String ext = DocumentLoader.extractExtension(filename);
        boolean isDoc = "doc".equalsIgnoreCase(ext);
        if (isDoc) {
            return extractDoc(inputStream, filename);
        }
        return extractDocx(inputStream, filename);
    }

    private String extractDocx(InputStream inputStream, String filename) {
        try (XWPFDocument doc = new XWPFDocument(inputStream)) {
            StringBuilder sb = new StringBuilder(16384);
            // 段落
            for (XWPFParagraph para : doc.getParagraphs()) {
                String text = para.getText();
                if (text != null && !text.isBlank()) {
                    sb.append(text).append('\n');
                }
            }
            // 表格
            for (XWPFTable table : doc.getTables()) {
                sb.append('\n');
                for (XWPFTableRow row : table.getRows()) {
                    List<XWPFTableCell> cells = row.getTableCells();
                    for (int i = 0; i < cells.size(); i++) {
                        if (i > 0) {
                            sb.append(" | ");
                        }
                        String cellText = cells.get(i).getText();
                        if (cellText != null) {
                            sb.append(cellText.trim());
                        }
                    }
                    sb.append('\n');
                }
            }
            String text = sb.toString();
            if (log.isDebugEnabled()) {
                log.debug("DocxLoader extracted filename={}, chars={}", filename, text.length());
            }
            if (text.isBlank()) {
                log.warn("DocxLoader empty content: {}", filename);
            }
            return text;
        } catch (Exception e) {
            throw new DocumentExtractException(ErrorCode.KB_DOCUMENT_PARSE_FAILED,
                    "Word(docx) 文件解析失败: " + filename, e);
        }
    }

    private String extractDoc(InputStream inputStream, String filename) {
        // 旧版 .doc (OLE2) 解析需 poi-scratchpad HWPF，已从 poi-ooxml 剥离。
        // 为保持编译兼容与生产级提示，.doc 提示转换为 docx 后重试；通过反射尝试 HWPF 兜底。
        try {
            Class<?> hwpfClass = Class.forName("org.apache.poi.hwpf.HWPFDocument");
            Class<?> extractorClass = Class.forName("org.apache.poi.hwpf.extractor.WordExtractor");
            // 反射调用: new HWPFDocument(inputStream), extractor.getText()
            Object doc = hwpfClass.getConstructor(InputStream.class).newInstance(inputStream);
            Object extractor = extractorClass.getConstructor(hwpfClass).newInstance(doc);
            String text = (String) extractorClass.getMethod("getText").invoke(extractor);
            if (text == null) {
                text = "";
            }
            extractorClass.getMethod("close").invoke(extractor);
            hwpfClass.getMethod("close").invoke(doc);
            if (log.isDebugEnabled()) {
                log.debug("DocLoader(doc) via reflection extracted filename={}, chars={}", filename, text.length());
            }
            return text;
        } catch (ClassNotFoundException cnf) {
            throw new DocumentExtractException(ErrorCode.KB_DOCUMENT_TYPE_UNSUPPORTED,
                    "旧版 .doc 格式暂不支持，请转换为 .docx 后上传: " + filename, cnf);
        } catch (Exception e) {
            if (e instanceof DocumentExtractException dee) {
                throw dee;
            }
            throw new DocumentExtractException(ErrorCode.KB_DOCUMENT_PARSE_FAILED,
                    "Word(doc) 文件解析失败: " + filename, e);
        }
    }
}
