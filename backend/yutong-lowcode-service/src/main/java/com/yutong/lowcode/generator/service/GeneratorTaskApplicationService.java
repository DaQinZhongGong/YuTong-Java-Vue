package com.yutong.lowcode.generator.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.api.facade.LicenseService;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.exception.BusinessConflictException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.id.IdGenerator;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.lowcode.meta.domain.LcEntity;
import com.yutong.lowcode.meta.domain.LcField;
import com.yutong.lowcode.meta.domain.LcGeneratorTask;
import com.yutong.lowcode.meta.mapper.LcEntityMapper;
import com.yutong.lowcode.meta.mapper.LcFieldMapper;
import com.yutong.lowcode.meta.mapper.LcGeneratorTaskMapper;
import com.yutong.lowcode.meta.service.LcDomainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 代码生成任务应用服务。设计来源: 14-低代码平台设计、52-后端服务分工、65-低代码代码生成模板详设
 * 流程: 创建任务(PENDING) → 启动(RUNNING) → 计算 diff → SUCCESS/CONFLICT
 * 生成结果只进草稿区，不直接写盘；冲突时返回 diff_json 供人工确认。
 */
@Service
public class GeneratorTaskApplicationService {

    private final LcGeneratorTaskMapper taskMapper;
    private final LcEntityMapper entityMapper;
    private final LcFieldMapper fieldMapper;
    private final LcDomainService domainService;
    private final GeneratorDiffService diffService;
    private final CodeTemplateService templateService;
    private final com.yutong.common.metrics.PlatformMetrics platformMetrics;
    /** GA2-L173: 商业授权额度校验（70 号文档「额度扣减规则」第 1 条：低代码生成先检查再扣减） */
    private final LicenseService licenseService;

    public GeneratorTaskApplicationService(LcGeneratorTaskMapper taskMapper,
                                          LcEntityMapper entityMapper,
                                          LcFieldMapper fieldMapper,
                                          LcDomainService domainService,
                                          GeneratorDiffService diffService,
                                          CodeTemplateService templateService,
                                          com.yutong.common.metrics.PlatformMetrics platformMetrics,
                                          LicenseService licenseService) {
        this.taskMapper = taskMapper;
        this.entityMapper = entityMapper;
        this.fieldMapper = fieldMapper;
        this.domainService = domainService;
        this.diffService = diffService;
        this.templateService = templateService;
        this.platformMetrics = platformMetrics;
        this.licenseService = licenseService;
    }

    public PageResult<LcGeneratorTask> pageTasks(PageRequest request, String taskNo, String status) {
        LambdaQueryWrapper<LcGeneratorTask> wrapper = new LambdaQueryWrapper<LcGeneratorTask>()
                .eq(LcGeneratorTask::getTenantId, CurrentUserContext.getTenantId())
                .like(taskNo != null && !taskNo.isBlank(), LcGeneratorTask::getTaskNo, taskNo)
                .eq(status != null && !status.isBlank(), LcGeneratorTask::getStatus, status)
                .orderByDesc(LcGeneratorTask::getCreatedTime);
        Page<LcGeneratorTask> page = taskMapper.selectPage(
                new Page<>(request.page(), request.size()), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), request.page(), request.size());
    }

    public LcGeneratorTask getTask(String id) {
        LcGeneratorTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new ResourceNotFoundException("生成任务不存在: " + id);
        }
        return task;
    }

    /**
     * 创建生成任务。状态 PENDING。
     */
    @Transactional
    public LcGeneratorTask createTask(String entityId, String pageId,
                                      String templateVersion, String targetScope) {
        diffService.validateScope(targetScope);
        LcGeneratorTask task = new LcGeneratorTask();
        task.setId(IdGenerator.nextId());
        task.setTenantId(CurrentUserContext.getTenantId());
        task.setCreatedBy(CurrentUserContext.getUserId());
        task.setTaskNo(generateTaskNo());
        task.setEntityId(entityId);
        task.setPageId(pageId);
        task.setTemplateVersion(templateVersion != null ? templateVersion : "1.0");
        task.setTargetScope(targetScope);
        task.setConflictCount(0);
        task.setStatus(LcGeneratorTask.STATUS_PENDING);
        taskMapper.insert(task);
        return task;
    }

    /**
     * 执行生成任务: PENDING → RUNNING → 计算 diff → SUCCESS/CONFLICT
     * 65 号文档要求: 生成多文件 (entity/dto/mapper/repository/service/controller)、真实文件扫描 diff、保存 diff_json。
     *
     * <p>GA2-L173: 接入 70 号文档「额度扣减规则」第 1 条——低代码生成必须先检查再扣减。
     * 在 PENDING→RUNNING 切换前调用 {@link LicenseService#checkQuota} 预校验额度，
     * 生成成功后调用 {@link LicenseService#recordQuotaUsage} 实际扣减（每次生成扣减 1 次）。
     * 额度不足时抛 LIC-429001，不执行生成动作；额度记录失败不阻断业务（容错策略）。
     */
    @Transactional
    public LcGeneratorTask runTask(String id) {
        LcGeneratorTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new ResourceNotFoundException("生成任务不存在: " + id);
        }
        domainService.validateTaskTransition(task.getStatus(), "START");

        // GA2-L173: 额度预校验（70 号文档「额度扣减规则」第 1 条：先检查再扣减，扣减失败不得执行业务动作）
        licenseService.checkQuota("lowcode.generate.count", 1);

        long startMs = System.currentTimeMillis();
        String tenantId = CurrentUserContext.getTenantId();

        // PENDING → RUNNING
        task.setStatus(LcGeneratorTask.STATUS_RUNNING);
        task.setStartedTime(OffsetDateTime.now());
        task.setUpdatedBy(CurrentUserContext.getUserId());
        taskMapper.updateById(task);

        try {
            // 1. 加载实体和字段
            LcEntity entity = entityMapper.selectById(task.getEntityId());
            if (entity == null) {
                throw new ResourceNotFoundException("实体不存在: " + task.getEntityId());
            }
            List<LcField> fields = fieldMapper.selectList(
                    new LambdaQueryWrapper<LcField>()
                            .eq(LcField::getEntityId, task.getEntityId())
                            .orderByAsc(LcField::getSortNo));

            // 2. 按 scope 批量生成多文件 (65 号文档要求)
            List<CodeTemplateService.GeneratedArtifact> artifacts =
                    templateService.generateByScope(task.getTargetScope(), entity, fields);

            // 3. 扫描输出目录已有文件，计算真实 diff (65 号文档要求)
            List<GeneratorDiffService.GeneratedFile> generatedFiles = artifacts.stream()
                    .map(a -> new GeneratorDiffService.GeneratedFile(a.path(), a.content()))
                    .toList();
            // P4-05: 全量扫描以识别 deleted 文件（生成器未生成但目标目录仍存在的文件）
            List<GeneratorDiffService.ExistingFile> existingFiles = diffService.scanAllExistingFiles();
            GeneratorDiffService.DiffResult diffResult = diffService.computeDiff(generatedFiles, existingFiles);

            // 4. 构建 diff JSON
            String diffJson = buildDiffJson(diffResult);

            task.setDiffJson(diffJson);
            String finalStatus;
            if (diffResult.conflictCount() == 0) {
                task.setConflictCount(0);
                task.setStatus(LcGeneratorTask.STATUS_SUCCESS);
                finalStatus = "SUCCESS";
            } else {
                task.setConflictCount(diffResult.conflictCount());
                task.setStatus(LcGeneratorTask.STATUS_CONFLICT);
                finalStatus = "CONFLICT";
            }
            task.setFinishedTime(OffsetDateTime.now());
            task.setUpdatedBy(CurrentUserContext.getUserId());
            taskMapper.updateById(task);

            // GA2-L173: 生成成功后扣减额度（70 号文档「额度扣减规则」第 1 条：业务成功后扣减）
            // 仅 SUCCESS/CONFLICT 视为生成动作已执行，需扣减；FAILED 不扣减（catch 分支不调用）
            licenseService.recordQuotaUsage("lowcode.generate.count", 1);

            // P5-04 业务指标埋点 (62-可观测性详设: lowcode_generate_task_total / lowcode_generate_duration_seconds)
            platformMetrics.recordLowcodeGenerateTask(tenantId, task.getTargetScope(), finalStatus,
                    java.time.Duration.ofMillis(System.currentTimeMillis() - startMs));
            return task;
        } catch (Exception e) {
            task.setStatus(LcGeneratorTask.STATUS_FAILED);
            task.setErrorMessage(e.getMessage());
            task.setFinishedTime(OffsetDateTime.now());
            task.setUpdatedBy(CurrentUserContext.getUserId());
            taskMapper.updateById(task);
            // P5-04 失败指标
            platformMetrics.recordLowcodeGenerateTask(tenantId, task.getTargetScope(), "FAILED",
                    java.time.Duration.ofMillis(System.currentTimeMillis() - startMs));
            throw new BusinessConflictException("代码生成失败: " + e.getMessage());
        }
    }

    /**
     * 取消任务: PENDING/RUNNING → CANCELLED
     */
    @Transactional
    public LcGeneratorTask cancel(String id) {
        LcGeneratorTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new ResourceNotFoundException("生成任务不存在: " + id);
        }
        domainService.validateTaskTransition(task.getStatus(), "CANCEL");
        task.setStatus(LcGeneratorTask.STATUS_CANCELLED);
        task.setFinishedTime(OffsetDateTime.now());
        task.setUpdatedBy(CurrentUserContext.getUserId());
        int affected = taskMapper.updateById(task);
        if (affected == 0) {
            throw new BusinessConflictException("数据已被他人修改，请刷新后重试");
        }
        return task;
    }

    /**
     * 构建 diff JSON。65 号文档要求保存 diff_json 和 output_files。
     * 结构: { added: [path...], modified: [path...], deleted: [path...], conflict: [{path, reason}...], unchanged: [path...] }
     *
     * P4-05: deleted 路径也纳入 diff_json，支持前端完整预览废弃文件。
     */
    private String buildDiffJson(GeneratorDiffService.DiffResult diffResult) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"added\":[");
        sb.append(String.join(",", diffResult.getAdded().stream()
                .map(f -> "\"" + escapeJson(f.path()) + "\"").toList()));
        sb.append("],\"modified\":[");
        sb.append(String.join(",", diffResult.getModified().stream()
                .map(f -> "\"" + escapeJson(f.path()) + "\"").toList()));
        sb.append("],\"deleted\":[");
        sb.append(String.join(",", diffResult.getDeleted().stream()
                .map(f -> "\"" + escapeJson(f.path()) + "\"").toList()));
        sb.append("],\"conflict\":[");
        sb.append(String.join(",", diffResult.getConflict().stream()
                .map(c -> "{\"path\":\"" + escapeJson(c.file().path()) + "\",\"reason\":\"" + c.reason() + "\"}")
                .toList()));
        sb.append("],\"unchanged\":[");
        sb.append(String.join(",", diffResult.getUnchanged().stream()
                .map(f -> "\"" + escapeJson(f.path()) + "\"").toList()));
        sb.append("]}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String generateTaskNo() {
        return "GEN" + OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", (int) (Math.random() * 10000));
    }
}
