package com.yutong.ai.skill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.ai.skill.domain.AiSkill;
import com.yutong.ai.skill.domain.AiSkillRun;
import com.yutong.ai.skill.dto.ExecuteSkillRequest;
import com.yutong.ai.skill.dto.ExecuteSkillResponse;
import com.yutong.ai.skill.mapper.AiSkillRunMapper;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.id.IdGenerator;
import com.yutong.system.file.domain.SysFile;
import com.yutong.system.file.service.FileService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 内置 Skill 执行器。docx/pdf/xlsx 用 POI/PDFBox 真生成或分析，产物落 MinIO。
 * 不调用外部 Python 脚本，避免不可控全局路径。
 */
@Service
public class SkillExecutor {

    private static final Logger log = LoggerFactory.getLogger(SkillExecutor.class);
    private static final int MAX_CONTENT = 200_000;

    private final SkillRegistry skillRegistry;
    private final AiSkillRunMapper runMapper;
    private final FileService fileService;
    private final ObjectMapper objectMapper;

    public SkillExecutor(SkillRegistry skillRegistry,
                         AiSkillRunMapper runMapper,
                         FileService fileService,
                         ObjectMapper objectMapper) {
        this.skillRegistry = skillRegistry;
        this.runMapper = runMapper;
        this.fileService = fileService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ExecuteSkillResponse execute(String skillId, ExecuteSkillRequest request) {
        long start = System.currentTimeMillis();
        AiSkill skill = skillRegistry.getSkill(skillId);
        if (!AiSkill.STATUS_PUBLISHED.equals(skill.getStatus())) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    "仅 PUBLISHED Skill 可执行，当前状态=" + skill.getStatus());
        }
        String action = normalizeAction(request.getAction());
        String content = request.getContent() == null ? "" : request.getContent();
        if (content.isBlank()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "content 不能为空");
        }
        if (content.length() > MAX_CONTENT) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "content 超过 " + MAX_CONTENT + " 字符");
        }

        AiSkillRun run = new AiSkillRun();
        run.setId(IdGenerator.nextId());
        run.setTenantId(CurrentUserContext.getTenantId());
        run.setCreatedBy(CurrentUserContext.getUserId());
        run.setSkillId(skill.getId());
        run.setSkillCode(skill.getSkillCode());
        run.setAction(action);
        run.setStatus(AiSkillRun.STATUS_SUCCESS);
        run.setInputJson(toJson(Map.of(
                "action", action,
                "contentLength", content.length(),
                "fileName", nvl(request.getFileName()),
                "title", nvl(request.getTitle())
        )));

        try {
            if ("create".equals(action)) {
                GeneratedFile generated = createDocument(skill, request, content);
                SysFile stored = fileService.uploadBytes(generated.fileName(), generated.bytes(), generated.contentType());
                int latency = (int) (System.currentTimeMillis() - start);
                run.setOutputFileId(stored.getId());
                run.setLatencyMs(latency);
                run.setOutputJson(toJson(Map.of(
                        "fileId", stored.getId(),
                        "fileName", generated.fileName(),
                        "bytes", generated.bytes().length
                )));
                runMapper.insert(run);
                fileService.bind("ai_skill_run", run.getId(), stored.getId(), "OUTPUT", 0);
                String downloadUrl = fileService.getDownloadUrl(stored.getId());
                return ExecuteSkillResponse.builder()
                        .runId(run.getId())
                        .skillCode(skill.getSkillCode())
                        .skillType(skill.getSkillType())
                        .action(action)
                        .status(AiSkillRun.STATUS_SUCCESS)
                        .summary("已生成 " + generated.fileName() + "（" + generated.bytes().length + " bytes）")
                        .outputFileId(stored.getId())
                        .downloadUrl(downloadUrl)
                        .latencyMs(latency)
                        .build();
            }

            AnalyzeResult analyzed = analyze(skill, content);
            int latency = (int) (System.currentTimeMillis() - start);
            run.setLatencyMs(latency);
            run.setOutputJson(toJson(analyzed.asMap()));
            runMapper.insert(run);
            return ExecuteSkillResponse.builder()
                    .runId(run.getId())
                    .skillCode(skill.getSkillCode())
                    .skillType(skill.getSkillType())
                    .action(action)
                    .status(AiSkillRun.STATUS_SUCCESS)
                    .summary(analyzed.summary())
                    .latencyMs(latency)
                    .build();
        } catch (BusinessException e) {
            persistFailure(run, start, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("skill execute failed: skillId={} action={}", skillId, action, e);
            persistFailure(run, start, e.getMessage());
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "Skill 执行失败: " + e.getMessage());
        }
    }

    private void persistFailure(AiSkillRun run, long start, String message) {
        try {
            run.setStatus(AiSkillRun.STATUS_FAILED);
            run.setErrorMessage(message == null ? "unknown" : message.substring(0, Math.min(message.length(), 1000)));
            run.setLatencyMs((int) (System.currentTimeMillis() - start));
            if (run.getId() == null) {
                run.setId(IdGenerator.nextId());
            }
            runMapper.insert(run);
        } catch (Exception ex) {
            log.warn("skill run failure persist skipped", ex);
        }
    }

    private AnalyzeResult analyze(AiSkill skill, String content) {
        String type = skill.getSkillType() == null ? AiSkill.TYPE_CUSTOM : skill.getSkillType().toLowerCase(Locale.ROOT);
        int chars = content.length();
        int lines = content.split("\\R", -1).length;
        int words = content.trim().isEmpty() ? 0 : content.trim().split("\\s+").length;
        String preview = content.strip().replaceAll("\\s+", " ");
        if (preview.length() > 240) {
            preview = preview.substring(0, 240) + "...";
        }
        String summary = switch (type) {
            case AiSkill.TYPE_DOCX -> "Word 分析：%d 字符 / %d 行 / %d 词。预览：%s".formatted(chars, lines, words, preview);
            case AiSkill.TYPE_PDF -> "PDF 分析：%d 字符 / %d 行。预览：%s".formatted(chars, lines, preview);
            case AiSkill.TYPE_XLSX -> "表格分析：%d 字符 / %d 行。预览：%s".formatted(chars, lines, preview);
            default -> "Skill 分析：%d 字符 / %d 行。预览：%s".formatted(chars, lines, preview);
        };
        return new AnalyzeResult(summary, chars, lines, words, preview);
    }

    private GeneratedFile createDocument(AiSkill skill, ExecuteSkillRequest request, String content) throws Exception {
        String type = skill.getSkillType() == null ? AiSkill.TYPE_CUSTOM : skill.getSkillType().toLowerCase(Locale.ROOT);
        String title = (request.getTitle() == null || request.getTitle().isBlank())
                ? skill.getSkillName() : request.getTitle().trim();
        String baseName = sanitizeFileName(request.getFileName(), skill.getSkillCode());
        return switch (type) {
            case AiSkill.TYPE_DOCX -> new GeneratedFile(baseName + ".docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    buildDocx(title, content));
            case AiSkill.TYPE_PDF -> new GeneratedFile(baseName + ".pdf", "application/pdf", buildPdf(title, content));
            case AiSkill.TYPE_XLSX -> new GeneratedFile(baseName + ".xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    buildXlsx(title, content));
            default -> new GeneratedFile(baseName + ".md", "text/markdown; charset=UTF-8",
                    ("# " + title + "\n\n" + content).getBytes(StandardCharsets.UTF_8));
        };
    }

    private byte[] buildDocx(String title, String content) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph titleP = doc.createParagraph();
            XWPFRun titleRun = titleP.createRun();
            titleRun.setBold(true);
            titleRun.setFontSize(16);
            titleRun.setText(title);
            for (String line : content.split("\\R", -1)) {
                XWPFParagraph p = doc.createParagraph();
                XWPFRun run = p.createRun();
                run.setFontSize(11);
                run.setText(line);
            }
            doc.write(out);
            return out.toByteArray();
        }
    }

    private byte[] buildPdf(String title, String content) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            float margin = 48;
            float y = page.getMediaBox().getHeight() - margin;
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(bold, 14);
                cs.newLineAtOffset(margin, y);
                cs.showText(sanitizePdf(title));
                cs.endText();
                y -= 22;
                cs.setFont(font, 11);
                for (String raw : wrapPdfLines(content, 92)) {
                    if (y < margin) {
                        break;
                    }
                    cs.beginText();
                    cs.newLineAtOffset(margin, y);
                    cs.showText(sanitizePdf(raw));
                    cs.endText();
                    y -= 14;
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private byte[] buildXlsx(String title, String content) throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Sheet1");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("title");
            header.createCell(1).setCellValue(title);
            int rowIdx = 1;
            for (String line : content.split("\\R", -1)) {
                if (rowIdx > 50000) {
                    break;
                }
                Row row = sheet.createRow(rowIdx++);
                String[] cols = line.split("\\t|,", -1);
                for (int i = 0; i < Math.min(cols.length, 32); i++) {
                    row.createCell(i).setCellValue(cols[i]);
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    private List<String> wrapPdfLines(String content, int max) {
        List<String> lines = new ArrayList<>();
        for (String raw : content.split("\\R", -1)) {
            String line = sanitizePdf(raw);
            while (line.length() > max) {
                lines.add(line.substring(0, max));
                line = line.substring(max);
            }
            lines.add(line);
            if (lines.size() > 60) {
                break;
            }
        }
        return lines;
    }

    private String sanitizePdf(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 32 && c <= 126) {
                sb.append(c);
            } else if (c == '\t') {
                sb.append(' ');
            } else {
                sb.append('?');
            }
        }
        return sb.toString();
    }

    private String sanitizeFileName(String requested, String fallback) {
        String base = requested == null || requested.isBlank() ? fallback : requested.trim();
        base = base.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (base.toLowerCase(Locale.ROOT).endsWith(".docx")
                || base.toLowerCase(Locale.ROOT).endsWith(".pdf")
                || base.toLowerCase(Locale.ROOT).endsWith(".xlsx")
                || base.toLowerCase(Locale.ROOT).endsWith(".md")) {
            int dot = base.lastIndexOf('.');
            base = base.substring(0, dot);
        }
        if (base.isBlank()) {
            base = "skill-output";
        }
        return base;
    }

    private String normalizeAction(String action) {
        if (action == null || action.isBlank()) {
            return "analyze";
        }
        String a = action.trim().toLowerCase(Locale.ROOT);
        if ("analyze".equals(a) || "create".equals(a)) {
            return a;
        }
        throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "action 仅支持 analyze/create");
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }

    private record GeneratedFile(String fileName, String contentType, byte[] bytes) {}

    private record AnalyzeResult(String summary, int chars, int lines, int words, String preview) {
        Map<String, Object> asMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("summary", summary);
            m.put("chars", chars);
            m.put("lines", lines);
            m.put("words", words);
            m.put("preview", preview);
            return m;
        }
    }
}
