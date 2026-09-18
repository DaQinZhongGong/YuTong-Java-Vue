package com.yutong.ai.rag.loader;

import com.yutong.common.errorcode.ErrorCode;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Set;

/**
 * Excel 加载器 — 支持 xls/xlsx，使用 POI WorkbookFactory。
 * <p>
 * 策略:
 * <ul>
 *   <li>遍历所有 Sheet，输出 "# Sheet: name" 分隔</li>
 *   <li>每行按单元格以 " | " 连接，使用 DataFormatter 保留显示值</li>
 *   <li>空行跳过，限制 50000 行防止 OOM</li>
 *   <li>公式单元格按缓存值输出</li>
 * </ul>
 */
@Component
public class ExcelLoader implements DocumentLoader {

    private static final Logger log = LoggerFactory.getLogger(ExcelLoader.class);

    private static final Set<String> EXTENSIONS = Set.of("xls", "xlsx");

    private static final int MAX_ROWS = 50000;

    @Override
    public String getLoaderType() {
        return LoaderType.EXCEL;
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
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            DataFormatter formatter = new DataFormatter();
            StringBuilder sb = new StringBuilder(16384);
            int totalRows = 0;
            int sheets = workbook.getNumberOfSheets();
            for (int s = 0; s < sheets; s++) {
                Sheet sheet = workbook.getSheetAt(s);
                if (sheet == null) {
                    continue;
                }
                String sheetName = sheet.getSheetName();
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append("# Sheet: ").append(sheetName).append('\n');
                for (Row row : sheet) {
                    if (row == null) {
                        continue;
                    }
                    // 判断空行
                    boolean allBlank = true;
                    for (Cell cell : row) {
                        if (cell != null && cell.getCellType() != CellType.BLANK) {
                            String v = formatter.formatCellValue(cell);
                            if (v != null && !v.isBlank()) {
                                allBlank = false;
                                break;
                            }
                        }
                    }
                    if (allBlank) {
                        continue;
                    }
                    int lastCell = row.getLastCellNum();
                    if (lastCell < 0) {
                        continue;
                    }
                    boolean first = true;
                    for (int c = 0; c < lastCell; c++) {
                        Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                        String val = "";
                        if (cell != null) {
                            // 公式/数值/文本统一按显示值
                            val = formatter.formatCellValue(cell);
                        }
                        if (!first) {
                            sb.append(" | ");
                        }
                        sb.append(val != null ? val.trim() : "");
                        first = false;
                    }
                    sb.append('\n');
                    totalRows++;
                    if (totalRows >= MAX_ROWS) {
                        log.warn("ExcelLoader truncated at {} rows: {}", MAX_ROWS, filename);
                        sb.append("\n[truncated at ").append(MAX_ROWS).append(" rows]\n");
                        return sb.toString();
                    }
                }
            }
            String text = sb.toString();
            if (log.isDebugEnabled()) {
                log.debug("ExcelLoader extracted filename={}, sheets={}, rows={}, chars={}",
                        filename, sheets, totalRows, text.length());
            }
            return text;
        } catch (Exception e) {
            if (e instanceof DocumentExtractException dee) {
                throw dee;
            }
            throw new DocumentExtractException(ErrorCode.KB_DOCUMENT_PARSE_FAILED,
                    "Excel 文件解析失败: " + filename, e);
        }
    }
}
