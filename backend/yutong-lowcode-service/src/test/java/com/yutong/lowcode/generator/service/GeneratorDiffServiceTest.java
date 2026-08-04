package com.yutong.lowcode.generator.service;

import com.yutong.common.exception.BusinessConflictException;
import com.yutong.lowcode.generator.service.GeneratorDiffService.DiffResult;
import com.yutong.lowcode.generator.service.GeneratorDiffService.ExistingFile;
import com.yutong.lowcode.generator.service.GeneratorDiffService.GeneratedFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 代码生成 diff 计算领域服务单元测试。设计来源: 14-低代码平台设计、36-低代码高级能力
 * 约束: 人工修改过的文件标记为 conflict，禁止静默覆盖。
 */
@DisplayName("代码生成 diff 计算服务")
class GeneratorDiffServiceTest {

    private final GeneratorDiffService diffService = new GeneratorDiffService();

    @Nested
    @DisplayName("computeDiff: 全部新增场景")
    class AllAdded {

        @Test
        @DisplayName("目标目录为空，所有生成文件标记为 added")
        void emptyExistingAllAdded() {
            List<GeneratedFile> generated = List.of(
                    new GeneratedFile("/src/Customer.java", "class Customer{}"),
                    new GeneratedFile("/src/Product.java", "class Product{}"));
            List<ExistingFile> existing = List.of();

            DiffResult result = diffService.computeDiff(generated, existing);

            assertEquals(2, result.getAdded().size());
            assertTrue(result.getModified().isEmpty());
            assertTrue(result.getConflict().isEmpty());
            assertFalse(result.hasConflict());
        }
    }

    @Nested
    @DisplayName("computeDiff: 修改场景")
    class ModifiedFiles {

        @Test
        @DisplayName("已存在且未人工修改的文件标记为 modified")
        void existingNotModified() {
            List<GeneratedFile> generated = List.of(
                    new GeneratedFile("/src/Customer.java", "class Customer{}"));
            List<ExistingFile> existing = List.of(
                    new ExistingFile("/src/Customer.java", null, false));

            DiffResult result = diffService.computeDiff(generated, existing);

            assertEquals(1, result.getModified().size());
            assertTrue(result.getAdded().isEmpty());
            assertFalse(result.hasConflict());
        }
    }

    @Nested
    @DisplayName("computeDiff: 未变更场景")
    class UnchangedFiles {

        @Test
        @DisplayName("内容完全一致标记为 unchanged")
        void identicalContentMarkedUnchanged() {
            List<GeneratedFile> generated = List.of(
                    new GeneratedFile("/src/Customer.java", "class Customer{}"));
            List<ExistingFile> existing = List.of(
                    new ExistingFile("/src/Customer.java", "class Customer{}", false));

            DiffResult result = diffService.computeDiff(generated, existing);

            assertEquals(1, result.getUnchanged().size());
            assertTrue(result.getAdded().isEmpty());
            assertTrue(result.getModified().isEmpty());
            assertTrue(result.getConflict().isEmpty());
            assertFalse(result.hasConflict());
        }
    }

    @Nested
    @DisplayName("computeDiff: 冲突场景")
    class ConflictFiles {

        @Test
        @DisplayName("已存在且人工修改的文件标记为 conflict")
        void existingManuallyModified() {
            List<GeneratedFile> generated = List.of(
                    new GeneratedFile("/src/Customer.java", "class Customer{}"));
            List<ExistingFile> existing = List.of(
                    new ExistingFile("/src/Customer.java", null, true));

            DiffResult result = diffService.computeDiff(generated, existing);

            assertEquals(1, result.getConflict().size());
            assertTrue(result.hasConflict());
            assertEquals(1, result.conflictCount());
            assertTrue(result.getModified().isEmpty());
        }

        @Test
        @DisplayName("受保护后缀 .custom.java 强制冲突")
        void protectedSuffixConflict() {
            List<GeneratedFile> generated = List.of(
                    new GeneratedFile("/src/Customer.custom.java", "class Customer{}"));
            List<ExistingFile> existing = List.of(
                    new ExistingFile("/src/Customer.custom.java", null, false));

            DiffResult result = diffService.computeDiff(generated, existing);

            assertEquals(1, result.getConflict().size());
            assertTrue(result.hasConflict());
        }

        @Test
        @DisplayName("受保护后缀 .manual.vue 强制冲突")
        void protectedSuffixManualVue() {
            List<GeneratedFile> generated = List.of(
                    new GeneratedFile("/src/Hello.manual.vue", "<template/>"));
            List<ExistingFile> existing = List.of(
                    new ExistingFile("/src/Hello.manual.vue", null, false));

            DiffResult result = diffService.computeDiff(generated, existing);

            assertTrue(result.hasConflict());
            assertEquals(1, result.conflictCount());
        }

        @Test
        @DisplayName("受保护后缀 .protected.ts 强制冲突")
        void protectedSuffixTs() {
            List<GeneratedFile> generated = List.of(
                    new GeneratedFile("/src/config.protected.ts", "export const x = 1"));
            List<ExistingFile> existing = List.of(
                    new ExistingFile("/src/config.protected.ts", "export const x = 1", false));

            DiffResult result = diffService.computeDiff(generated, existing);

            assertTrue(result.hasConflict());
            assertEquals(1, result.conflictCount());
        }

        @Test
        @DisplayName("已存在文件标记为人工修改时产生冲突")
        void manuallyModifiedFlagConflict() {
            List<GeneratedFile> generated = List.of(
                    new GeneratedFile("/src/Customer.java", "class Customer{}"));
            List<ExistingFile> existing = List.of(
                    new ExistingFile("/src/Customer.java", "// @manual-edit\nclass Customer{}", true));

            DiffResult result = diffService.computeDiff(generated, existing);

            assertEquals(1, result.getConflict().size());
            assertTrue(result.hasConflict());
            assertTrue(result.getModified().isEmpty());
        }
    }

    @Nested
    @DisplayName("computeDiff: 混合场景")
    class MixedScenario {

        @Test
        @DisplayName("新增 + 修改 + 冲突 混合分组")
        void mixedGroups() {
            List<GeneratedFile> generated = List.of(
                    new GeneratedFile("/new/File1.java", "1"),
                    new GeneratedFile("/mod/File2.java", "2"),
                    new GeneratedFile("/conf/File3.java", "3"),
                    new GeneratedFile("/conf/Custom.manual.vue", "4"));
            List<ExistingFile> existing = List.of(
                    new ExistingFile("/mod/File2.java", null, false),
                    new ExistingFile("/conf/File3.java", null, true),
                    new ExistingFile("/conf/Custom.manual.vue", null, false));

            DiffResult result = diffService.computeDiff(generated, existing);

            assertEquals(1, result.getAdded().size());
            assertEquals(1, result.getModified().size());
            assertEquals(2, result.getConflict().size());
            assertTrue(result.hasConflict());
        }
    }

    @Nested
    @DisplayName("computeDiff: 删除场景")
    class DeletedFiles {

        @Test
        @DisplayName("生成器未生成但目标目录已存在的文件标记为 deleted")
        void existingNotGeneratedMarkedDeleted() {
            List<GeneratedFile> generated = List.of(
                    new GeneratedFile("/src/Customer.java", "class Customer{}"));
            List<ExistingFile> existing = List.of(
                    new ExistingFile("/src/Customer.java", "class Customer{}", false),
                    new ExistingFile("/src/Legacy.java", "class Legacy{}", false));

            DiffResult result = diffService.computeDiff(generated, existing);

            assertEquals(1, result.getDeleted().size());
            assertEquals("/src/Legacy.java", result.getDeleted().get(0).path());
            assertTrue(result.getAdded().isEmpty());
            assertTrue(result.getModified().isEmpty());
            assertTrue(result.getConflict().isEmpty());
        }
    }

    @Nested
    @DisplayName("scanAllExistingFiles: 全量扫描")
    class ScanAllExistingFiles {

        @Test
        @DisplayName("递归扫描输出目录并识别人工修改标记")
        void recursiveScanDetectsManualMarker(@TempDir Path tempDir) throws Exception {
            Files.createDirectories(tempDir.resolve("src/nested"));
            Files.writeString(tempDir.resolve("src/Customer.java"), "class Customer{}");
            Files.writeString(tempDir.resolve("src/nested/Custom.custom.java"), "// auto\nclass Custom{}");
            Files.writeString(tempDir.resolve("src/nested/Manual.java"), "// @manual-edit\nclass Manual{}");

            setOutputDir(tempDir.toString());

            List<ExistingFile> existing = diffService.scanAllExistingFiles();

            assertEquals(3, existing.size());
            long manualCount = existing.stream().filter(ExistingFile::manuallyModified).count();
            assertEquals(2, manualCount);
            assertTrue(existing.stream().anyMatch(f -> f.path().equals("src/nested/Custom.custom.java")));
            assertTrue(existing.stream().anyMatch(f -> f.path().equals("src/nested/Manual.java")));
        }

        @Test
        @DisplayName("输出目录不存在返回空列表")
        void missingDirReturnsEmpty(@TempDir Path tempDir) throws Exception {
            setOutputDir(tempDir.resolve("not-exist").toString());
            assertTrue(diffService.scanAllExistingFiles().isEmpty());
        }

        private void setOutputDir(String dir) throws Exception {
            Field field = GeneratorDiffService.class.getDeclaredField("outputDir");
            field.setAccessible(true);
            field.set(diffService, dir);
        }
    }

    @Nested
    @DisplayName("validateScope: scope 合法性")
    class ScopeValidation {

        @Test
        @DisplayName("DDL 合法")
        void ddlLegal() {
            assertDoesNotThrow(() -> diffService.validateScope("DDL"));
        }

        @Test
        @DisplayName("JAVA / VUE / UNIAPP / OPENAPI 合法")
        void allLegalScopes() {
            assertDoesNotThrow(() -> diffService.validateScope("JAVA"));
            assertDoesNotThrow(() -> diffService.validateScope("VUE"));
            assertDoesNotThrow(() -> diffService.validateScope("UNIAPP"));
            assertDoesNotThrow(() -> diffService.validateScope("OPENAPI"));
        }

        @Test
        @DisplayName("null 抛异常")
        void nullThrows() {
            assertThrows(BusinessConflictException.class,
                    () -> diffService.validateScope(null));
        }

        @Test
        @DisplayName("未知 scope 抛异常")
        void unknownThrows() {
            assertThrows(BusinessConflictException.class,
                    () -> diffService.validateScope("UNKNOWN"));
        }
    }
}
