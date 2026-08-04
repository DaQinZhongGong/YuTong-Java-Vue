package com.yutong.lowcode.generator.service;

import com.yutong.common.exception.BusinessConflictException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 代码生成 diff 计算领域服务。设计来源: 14-低代码平台设计、36-低代码高级能力、65-低代码代码生成模板详设
 * <p>
 * 核心约束: 人工修改过的文件默认提示冲突，禁止静默覆盖。
 * <p>
 * 冲突处理规则 (65 号文档):
 * 1. 新文件 → 直接生成草稿 (added)
 * 2. 已存在且未人工修改 → 可覆盖 (modified)
 * 3. 已人工修改 → 标记冲突 (conflict)
 * 4. 删除字段 → 不自动删除代码，生成 TODO (conflict + reason=FIELD_DELETED)
 * 5. 改字段类型 → 禁止自动变更 (conflict + reason=TYPE_CHANGED)
 */
@Service
public class GeneratorDiffService {

    /** 标记为人工修改的文件后缀（不允许覆盖） */
    private static final Set<String> PROTECTED_SUFFIXES = Set.of(
            ".custom.java", ".manual.vue", ".protected.ts"
    );

    /** 文件头标记，表示该文件被人工修改过 */
    private static final String MANUAL_EDIT_MARKER = "@manual-edit";

    /**
     * 生成器输出根目录。默认 ./target/generated-output，可通过 application.yml 配置。
     * 用于扫描已存在文件计算 diff。若目录不存在则视为全新生成。
     */
    @Value("${yutong.lowcode.generator.output-dir:./target/generated-output}")
    private String outputDir;

    /**
     * 计算 diff 结果。设计来源: 14 差异预览规则、65 号冲突处理规则
     * - 新增文件、删除文件、修改文件分组展示
     * - 人工修改过的文件标记为 conflict
     *
     * @param generatedFiles 生成器产出的文件列表
     * @param existingFiles  目标目录已存在的文件列表（可包含生成器未生成的文件，用于识别 deleted）
     * @return diff 结果
     */
    public DiffResult computeDiff(List<GeneratedFile> generatedFiles, List<ExistingFile> existingFiles) {
        DiffResult result = new DiffResult();
        Set<String> existingPaths = existingFiles.stream()
                .map(ExistingFile::path).collect(java.util.stream.Collectors.toSet());
        Set<String> generatedPaths = generatedFiles.stream()
                .map(GeneratedFile::path).collect(java.util.stream.Collectors.toSet());

        // 场景 4: 生成器未生成但目标目录已存在 → deleted
        for (ExistingFile existing : existingFiles) {
            if (!generatedPaths.contains(existing.path())) {
                result.addDeleted(new GeneratedFile(existing.path(), existing.content()));
            }
        }

        for (GeneratedFile gen : generatedFiles) {
            if (!existingPaths.contains(gen.path())) {
                // 场景 1: 新文件 → added
                result.addAdded(gen);
            } else {
                ExistingFile existing = existingFiles.stream()
                        .filter(f -> f.path().equals(gen.path()))
                        .findFirst().orElseThrow();
                if (isProtected(gen.path()) || existing.manuallyModified()) {
                    // 场景 3: 已人工修改 → conflict
                    result.addConflict(gen, "MANUAL_EDIT");
                } else if (!Objects.equals(existing.content(), gen.content())) {
                    // 场景 2: 已存在且未人工修改但内容不同 → modified (可覆盖)
                    result.addModified(gen);
                } else {
                    // 内容完全一致 → 标记为 unchanged (无变化)
                    result.addUnchanged(gen);
                }
            }
        }
        return result;
    }

    /**
     * 扫描输出目录，返回已存在的文件列表。
     * 若输出目录不存在则返回空列表。
     *
     * @param filePaths 生成器将要写入的文件路径列表 (相对路径)
     * @return 已存在文件列表 (含内容和人工修改标记)
     */
    public List<ExistingFile> scanExistingFiles(List<String> filePaths) {
        List<ExistingFile> existing = new ArrayList<>();
        Path baseDir = Paths.get(outputDir);
        if (!Files.exists(baseDir)) {
            return existing;
        }
        for (String relPath : filePaths) {
            Path fullPath = baseDir.resolve(relPath);
            if (Files.exists(fullPath)) {
                try {
                    String content = Files.readString(fullPath, StandardCharsets.UTF_8);
                    boolean manuallyModified = isProtected(relPath) || content.contains(MANUAL_EDIT_MARKER);
                    existing.add(new ExistingFile(relPath, content, manuallyModified));
                } catch (IOException e) {
                    // 读取失败的文件视为不存在
                }
            }
        }
        return existing;
    }

    /**
     * 递归扫描输出目录下所有已存在的文件。
     * 用于识别生成器未生成但目标目录仍存在的文件（deleted 场景）。
     *
     * @return 已存在文件列表 (含内容和人工修改标记)
     */
    public List<ExistingFile> scanAllExistingFiles() {
        List<ExistingFile> existing = new ArrayList<>();
        Path baseDir = Paths.get(outputDir);
        if (!Files.exists(baseDir) || !Files.isDirectory(baseDir)) {
            return existing;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(baseDir)) {
            paths.filter(Files::isRegularFile).forEach(fullPath -> {
                try {
                    String relPath = baseDir.relativize(fullPath).toString().replace('\\', '/');
                    String content = Files.readString(fullPath, StandardCharsets.UTF_8);
                    boolean manuallyModified = isProtected(relPath) || content.contains(MANUAL_EDIT_MARKER);
                    existing.add(new ExistingFile(relPath, content, manuallyModified));
                } catch (IOException e) {
                    // 读取失败的文件跳过
                }
            });
        } catch (IOException e) {
            // 扫描失败返回空列表，避免阻断生成流程
        }
        return existing;
    }

    /**
     * 计算删除字段和改字段类型的冲突。
     * v1.0 简化实现: 不实际比对元模型历史版本，由调用方根据需要传入。
     *
     * @param deletedFieldCodes 被删除的字段 code 列表
     * @param typeChangedFields 类型变更字段列表 (fieldCode, oldType, newType)
     */
    public List<ConflictItem> computeFieldLevelConflicts(List<String> deletedFieldCodes,
                                                          List<TypeChange> typeChangedFields) {
        List<ConflictItem> conflicts = new ArrayList<>();
        for (String code : deletedFieldCodes) {
            conflicts.add(new ConflictItem(code, "FIELD_DELETED",
                    "字段 " + code + " 已被删除，不自动删除代码，请人工处理 (生成 TODO)"));
        }
        for (TypeChange tc : typeChangedFields) {
            conflicts.add(new ConflictItem(tc.fieldCode(), "TYPE_CHANGED",
                    "字段 " + tc.fieldCode() + " 类型从 " + tc.oldType() + " 变更为 " + tc.newType()
                            + "，禁止自动变更，请人工迁移"));
        }
        return conflicts;
    }

    /**
     * 校验生成任务 scope 合法性。
     */
    public void validateScope(String targetScope) {
        if (targetScope == null) {
            throw new BusinessConflictException("target_scope 不能为空");
        }
        Set<String> allowed = Set.of("DDL", "JAVA", "VUE", "UNIAPP", "OPENAPI");
        if (!allowed.contains(targetScope)) {
            throw new BusinessConflictException(
                    "target_scope 不合法: " + targetScope + ", 允许: " + allowed);
        }
    }

    public String getOutputDir() {
        return outputDir;
    }

    private boolean isProtected(String path) {
        return PROTECTED_SUFFIXES.stream().anyMatch(path::endsWith);
    }

    /** 生成器产出的文件 */
    public record GeneratedFile(String path, String content) {}

    /** 目标目录已存在的文件 (含内容和人工修改标记) */
    public record ExistingFile(String path, String content, boolean manuallyModified) {}

    /** 字段类型变更 */
    public record TypeChange(String fieldCode, String oldType, String newType) {}

    /** 冲突项 */
    public record ConflictItem(String fieldCode, String reason, String message) {}

    /** diff 结果 */
    public static class DiffResult {
        private final List<GeneratedFile> added = new ArrayList<>();
        private final List<GeneratedFile> modified = new ArrayList<>();
        private final List<GeneratedFile> deleted = new ArrayList<>();
        private final List<ConflictEntry> conflict = new ArrayList<>();
        private final List<GeneratedFile> unchanged = new ArrayList<>();

        public void addAdded(GeneratedFile f) { added.add(f); }
        public void addModified(GeneratedFile f) { modified.add(f); }
        public void addDeleted(GeneratedFile f) { deleted.add(f); }
        public void addConflict(GeneratedFile f, String reason) {
            conflict.add(new ConflictEntry(f, reason));
        }
        public void addUnchanged(GeneratedFile f) { unchanged.add(f); }

        public List<GeneratedFile> getAdded() { return added; }
        public List<GeneratedFile> getModified() { return modified; }
        public List<GeneratedFile> getDeleted() { return deleted; }
        public List<ConflictEntry> getConflict() { return conflict; }
        public List<GeneratedFile> getUnchanged() { return unchanged; }

        public int conflictCount() { return conflict.size(); }
        public boolean hasConflict() { return !conflict.isEmpty(); }
    }

    /** 带原因的冲突条目 */
    public record ConflictEntry(GeneratedFile file, String reason) {}
}
