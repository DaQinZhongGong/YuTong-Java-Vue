package com.yutong.system.log.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.auth.DataScopeResolver;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.auth.DataScope;
import com.yutong.common.auth.DataScopeType;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.id.IdGenerator;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.trace.TraceContext;
import com.yutong.system.file.domain.SysFile;
import com.yutong.system.file.service.FileService;
import com.yutong.system.log.domain.SysImportExportTask;
import com.yutong.system.log.domain.SysJobLog;
import com.yutong.system.log.mapper.SysImportExportTaskMapper;
import com.yutong.system.log.mapper.SysJobLogMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * 导入导出任务服务。设计来源: 52-后端服务分工与接口实现详设 / 58-后端API逐接口任务清单 / 98-后端实现蓝图
 *
 * <p>职责:
 * <ul>
 *   <li>分页查询 / 详情 / 重试 (原有能力)</li>
 *   <li>异步提交导入任务: {@link #submitImportTask}</li>
 *   <li>异步提交导出任务: {@link #submitExportTask}</li>
 * </ul>
 *
 * <p>异步执行约定 (设计文档 52 / 58):
 * <ol>
 *   <li>controller 同步创建 {@code sys_import_export_task} 记录 (status=PENDING)，立即返回 taskId</li>
 *   <li>后台线程池执行业务工作函数，不阻塞 controller</li>
 *   <li>runner 在工作前后更新任务状态 (RUNNING→COMPLETED/FAILED) 与时间戳</li>
 *   <li>runner 同步写入 {@code sys_job_log} 记录开始/结束/耗时/错误信息</li>
 *   <li>导入源文件、导出结果文件、导入错误报告均上传至 MinIO 并回填 fileId/errorFileId</li>
 *   <li>业务工作函数由调用方 (controller) 以 lambda 提供，仅返回行数统计与生成的文件 ID</li>
 * </ol>
 *
 * <p>上下文透传: {@link CurrentUserContext} 为 ThreadLocal，跨线程不自动继承。
 * runner 在异步线程入口由调用方捕获的快照重新填充 tenantId/userId/username，保证
 * MetaObjectHandler 自动填充与租户隔离在异步线程中继续生效。
 *
 * <p>线程池: 自管理 {@link ThreadPoolExecutor}，核心 2 / 最大 4 / 队列 100，拒绝策略 CallerRuns
 * (队列满时由提交线程兜底执行，避免任务丢失)。未启用 Spring @Async，避免改动 boot 入口。
 */
@Service
public class ImportExportTaskService {

    private static final Logger log = LoggerFactory.getLogger(ImportExportTaskService.class);

    /** 任务状态 */
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";

    /** 任务类型 */
    private static final String TASK_TYPE_IMPORT = "IMPORT";
    private static final String TASK_TYPE_EXPORT = "EXPORT";

    /** 触发类型 (sys_job_log.trigger_type) */
    private static final String TRIGGER_TYPE_MANUAL = "MANUAL";

    /** errorMessage 列长度上限 (sys_import_export_task.error_message varchar(1024)) */
    private static final int ERROR_MESSAGE_MAX_LEN = 1024;

    /** 导入导出任务资源编码，对齐 permissions.yaml sys:import-export-task:* 命名 (SELF 策略)。 */
    public static final String RESOURCE_CODE = "sys:import-export-task";

    private final SysImportExportTaskMapper importExportTaskMapper;
    private final SysJobLogMapper jobLogMapper;
    private final FileService fileService;
    private final DataScopeResolver dataScopeResolver;

    /** 导入导出后台执行线程池。自管理，避免在 boot 入口加 @EnableAsync。 */
    private final ThreadPoolExecutor importExportExecutor;

    public ImportExportTaskService(SysImportExportTaskMapper importExportTaskMapper,
                                   SysJobLogMapper jobLogMapper,
                                   FileService fileService,
                                   DataScopeResolver dataScopeResolver) {
        this.importExportTaskMapper = importExportTaskMapper;
        this.jobLogMapper = jobLogMapper;
        this.fileService = fileService;
        this.dataScopeResolver = dataScopeResolver;
        BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(100);
        this.importExportExecutor = new ThreadPoolExecutor(
                2, 4, 60L, TimeUnit.SECONDS,
                workQueue,
                r -> {
                    Thread t = new Thread(r, "import-export-task-" + System.nanoTime());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    @PreDestroy
    void shutdown() {
        importExportExecutor.shutdown();
        try {
            if (!importExportExecutor.awaitTermination(15, TimeUnit.SECONDS)) {
                importExportExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            importExportExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ==================== 查询 / 重试 (原有能力) ====================

    /** 分页查询导入导出任务，按 taskType 和 status 过滤，按 createdTime DESC 排序。SELF 策略按 created_by 过滤。 */
    public PageResult<SysImportExportTask> pageTasks(PageRequest request, String taskType, String status) {
        LambdaQueryWrapper<SysImportExportTask> wrapper = new LambdaQueryWrapper<SysImportExportTask>()
                .eq(SysImportExportTask::getTenantId, CurrentUserContext.getTenantId())
                .eq(taskType != null && !taskType.isBlank(), SysImportExportTask::getTaskType, taskType)
                .eq(status != null && !status.isBlank(), SysImportExportTask::getStatus, status)
                .orderByDesc(SysImportExportTask::getCreatedTime);
        // GA2-DS: 接入 DataScope 过滤 (SELF 策略)，admin(ALL/TENANT) 放行，非 admin 按 created_by 过滤
        applyDataScope(wrapper, dataScopeResolver.resolve(RESOURCE_CODE));
        Page<SysImportExportTask> page = importExportTaskMapper.selectPage(
                new Page<>(request.page(), request.size()), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), request.page(), request.size());
    }

    /**
     * GA2-DS: 对 LambdaQueryWrapper 追加 DataScope 过滤条件 (SELF 策略)。
     * - ALL/TENANT: 无附加条件 (admin)
     * - 其它(SELF/DEPT/CUSTOM/NONE 等): created_by = currentUserId，userId 缺失时安全降级 1=0
     */
    private void applyDataScope(LambdaQueryWrapper<SysImportExportTask> wrapper, DataScope scope) {
        if (scope == null) {
            return;
        }
        if (scope.scopeType() == DataScopeType.ALL || scope.scopeType() == DataScopeType.TENANT) {
            return;
        }
        String userId = scope.userId();
        if (userId == null || userId.isBlank()) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.apply("created_by = {0}", userId);
    }

    /** 查询导入导出任务详情。 */
    public SysImportExportTask getTask(String id) {
        SysImportExportTask task = importExportTaskMapper.selectById(id);
        if (task == null) throw new ResourceNotFoundException("导入导出任务不存在: " + id);
        return task;
    }

    /** 重试任务，仅更新 status 为 PENDING 并返回任务记录。 */
    @Transactional
    public SysImportExportTask retryTask(String id) {
        SysImportExportTask existing = importExportTaskMapper.selectById(id);
        if (existing == null) {
            throw new ResourceNotFoundException("导入导出任务不存在: " + id);
        }
        existing.setStatus(STATUS_PENDING);
        importExportTaskMapper.updateById(existing);
        return existing;
    }

    // ==================== 异步提交 ====================

    /**
     * 异步提交导入任务。
     * 同步创建 PENDING 任务记录并上传源文件至 MinIO，立即返回 taskId；
     * 业务工作函数在后台线程执行，逐行处理并累计成功/失败行数。
     *
     * @param bizType        业务类型 (CUSTOMER/PRODUCT/BIZ_REQUEST)
     * @param originalFileName 原始文件名 (用于源文件上传与日志)
     * @param fileBytes      文件字节内容 (controller 已同步读取，避免请求结束后流关闭)
     * @param work           业务工作函数: (fileBytes, fileName) → ImportExportResult
     * @return 创建的任务记录 (status=PENDING)
     */
    public SysImportExportTask submitImportTask(String bizType,
                                                String originalFileName,
                                                byte[] fileBytes,
                                                BiFunction<byte[], String, ImportExportResult> work) {
        ContextSnapshot ctx = ContextSnapshot.capture();
        SysImportExportTask task = newTaskRecord(TASK_TYPE_IMPORT, bizType, ctx);
        importExportTaskMapper.insert(task);

        String taskId = task.getId();
        importExportExecutor.execute(() -> runImport(taskId, bizType, originalFileName, fileBytes, work, ctx));
        log.info("已提交导入任务: taskId={}, bizType={}, fileName={}", taskId, bizType, originalFileName);
        return task;
    }

    /**
     * 异步提交导出任务。
     * 同步创建 PENDING 任务记录，立即返回 taskId；
     * 业务工作函数在后台线程执行，生成导出文件并上传至 MinIO。
     *
     * @param bizType 业务类型 (CUSTOMER/PRODUCT/BIZ_REQUEST)
     * @param work    业务工作函数: () → ImportExportResult (含生成的 outputFileId)
     * @return 创建的任务记录 (status=PENDING)
     */
    public SysImportExportTask submitExportTask(String bizType, Supplier<ImportExportResult> work) {
        ContextSnapshot ctx = ContextSnapshot.capture();
        SysImportExportTask task = newTaskRecord(TASK_TYPE_EXPORT, bizType, ctx);
        importExportTaskMapper.insert(task);

        String taskId = task.getId();
        importExportExecutor.execute(() -> runExport(taskId, bizType, work, ctx));
        log.info("已提交导出任务: taskId={}, bizType={}", taskId, bizType);
        return task;
    }

    // ==================== 异步 runner ====================

    private void runImport(String taskId, String bizType, String originalFileName,
                           byte[] fileBytes, BiFunction<byte[], String, ImportExportResult> work,
                           ContextSnapshot ctx) {
        ContextSnapshot.apply(ctx);
        SysJobLog jobLog = startJobLog(TASK_TYPE_IMPORT, bizType, taskId, ctx);
        OffsetDateTime startedAt = OffsetDateTime.now();
        markTaskRunning(taskId, startedAt);

        try {
            // 上传源文件至 MinIO 并回填 file_id
            String sourceFileId = uploadSourceFile(originalFileName, fileBytes, ctx);
            if (sourceFileId != null) {
                SysImportExportTask patch = new SysImportExportTask();
                patch.setId(taskId);
                patch.setFileId(sourceFileId);
                importExportTaskMapper.updateById(patch);
            }

            ImportExportResult result = work.apply(fileBytes, originalFileName);

            // 回填错误报告 file_id
            if (result.outputFileId() != null) {
                SysImportExportTask patch = new SysImportExportTask();
                patch.setId(taskId);
                patch.setErrorFileId(result.outputFileId());
                importExportTaskMapper.updateById(patch);
            }

            markTaskCompleted(taskId, result, startedAt);
            finishJobLogSuccess(jobLog, startedAt, summarize(result));
            log.info("导入任务完成: taskId={}, total={}, success={}, fail={}",
                    taskId, result.totalRows(), result.successRows(), result.failRows());
        } catch (Exception e) {
            String msg = safeMessage(e);
            markTaskFailed(taskId, startedAt, msg);
            finishJobLogFailure(jobLog, startedAt, msg);
            log.error("导入任务失败: taskId={}", taskId, e);
        } finally {
            CurrentUserContext.clear();
        }
    }

    private void runExport(String taskId, String bizType, Supplier<ImportExportResult> work,
                           ContextSnapshot ctx) {
        ContextSnapshot.apply(ctx);
        SysJobLog jobLog = startJobLog(TASK_TYPE_EXPORT, bizType, taskId, ctx);
        OffsetDateTime startedAt = OffsetDateTime.now();
        markTaskRunning(taskId, startedAt);

        try {
            ImportExportResult result = work.get();

            // 回填导出文件 file_id
            if (result.outputFileId() != null) {
                SysImportExportTask patch = new SysImportExportTask();
                patch.setId(taskId);
                patch.setFileId(result.outputFileId());
                importExportTaskMapper.updateById(patch);
            }

            markTaskCompleted(taskId, result, startedAt);
            finishJobLogSuccess(jobLog, startedAt, summarize(result));
            log.info("导出任务完成: taskId={}, total={}, fileId={}",
                    taskId, result.totalRows(), result.outputFileId());
        } catch (Exception e) {
            String msg = safeMessage(e);
            markTaskFailed(taskId, startedAt, msg);
            finishJobLogFailure(jobLog, startedAt, msg);
            log.error("导出任务失败: taskId={}", taskId, e);
        } finally {
            CurrentUserContext.clear();
        }
    }

    // ==================== 任务状态更新 ====================

    private void markTaskRunning(String taskId, OffsetDateTime startedAt) {
        SysImportExportTask patch = new SysImportExportTask();
        patch.setId(taskId);
        patch.setStatus(STATUS_RUNNING);
        patch.setStartedTime(startedAt);
        importExportTaskMapper.updateById(patch);
    }

    private void markTaskCompleted(String taskId, ImportExportResult result, OffsetDateTime startedAt) {
        SysImportExportTask patch = new SysImportExportTask();
        patch.setId(taskId);
        patch.setStatus(STATUS_COMPLETED);
        patch.setTotalRows(result.totalRows());
        patch.setSuccessRows(result.successRows());
        patch.setFailRows(result.failRows());
        patch.setFinishedTime(OffsetDateTime.now());
        if (result.errorMessage() != null) {
            patch.setErrorMessage(truncate(result.errorMessage(), ERROR_MESSAGE_MAX_LEN));
        }
        importExportTaskMapper.updateById(patch);
    }

    private void markTaskFailed(String taskId, OffsetDateTime startedAt, String message) {
        SysImportExportTask patch = new SysImportExportTask();
        patch.setId(taskId);
        patch.setStatus(STATUS_FAILED);
        patch.setFinishedTime(OffsetDateTime.now());
        patch.setErrorMessage(truncate(message, ERROR_MESSAGE_MAX_LEN));
        importExportTaskMapper.updateById(patch);
    }

    // ==================== sys_job_log 写入 ====================

    private SysJobLog startJobLog(String taskType, String bizType, String taskId, ContextSnapshot ctx) {
        SysJobLog jobLog = new SysJobLog();
        jobLog.setId(IdGenerator.nextId());
        jobLog.setTenantId(ctx.tenantId());
        jobLog.setJobCode(taskType + "-" + bizType);
        jobLog.setJobName(taskType + "-" + bizType + "-" + taskId);
        jobLog.setBizType(bizType);
        jobLog.setBizId(taskId);
        jobLog.setTriggerType(TRIGGER_TYPE_MANUAL);
        jobLog.setStatus(STATUS_RUNNING);
        jobLog.setStartTime(OffsetDateTime.now());
        jobLog.setTraceId(ctx.traceId() != null ? ctx.traceId() : "no-trace");
        jobLogMapper.insert(jobLog);
        return jobLog;
    }

    private void finishJobLogSuccess(SysJobLog jobLog, OffsetDateTime startedAt, String message) {
        OffsetDateTime end = OffsetDateTime.now();
        jobLog.setStatus(STATUS_COMPLETED);
        jobLog.setEndTime(end);
        jobLog.setDurationMs(durationMs(startedAt, end));
        if (message != null) {
            jobLog.setErrorMessage(truncate(message, ERROR_MESSAGE_MAX_LEN));
        }
        jobLogMapper.updateById(jobLog);
    }

    private void finishJobLogFailure(SysJobLog jobLog, OffsetDateTime startedAt, String message) {
        OffsetDateTime end = OffsetDateTime.now();
        jobLog.setStatus(STATUS_FAILED);
        jobLog.setEndTime(end);
        jobLog.setDurationMs(durationMs(startedAt, end));
        jobLog.setErrorMessage(truncate(message, ERROR_MESSAGE_MAX_LEN));
        jobLogMapper.updateById(jobLog);
    }

    // ==================== 内部工具 ====================

    private SysImportExportTask newTaskRecord(String taskType, String bizType, ContextSnapshot ctx) {
        SysImportExportTask task = new SysImportExportTask();
        task.setId(IdGenerator.nextId());
        task.setTenantId(ctx.tenantId());
        task.setCreatedBy(ctx.userId());
        task.setTaskType(taskType);
        task.setBizType(bizType);
        task.setStatus(STATUS_PENDING);
        task.setTotalRows(0);
        task.setSuccessRows(0);
        task.setFailRows(0);
        return task;
    }

    /** 上传导入源文件至 MinIO，返回 file_id；失败仅记日志不阻断主流程 (业务工作函数仍可基于内存字节处理)。 */
    private String uploadSourceFile(String originalFileName, byte[] fileBytes, ContextSnapshot ctx) {
        if (fileBytes == null || fileBytes.length == 0) {
            return null;
        }
        String fileName = (originalFileName != null && !originalFileName.isBlank())
                ? originalFileName : "import-source.csv";
        try {
            String contentType = fileName.toLowerCase().endsWith(".csv") ? "text/csv" : "application/octet-stream";
            SysFile uploaded = fileService.uploadBytes(fileName, fileBytes, contentType);
            return uploaded.getId();
        } catch (Exception e) {
            log.warn("上传导入源文件失败 (任务继续执行): fileName={}, error={}", fileName, e.getMessage());
            return null;
        }
    }

    private static long durationMs(OffsetDateTime start, OffsetDateTime end) {
        if (start == null || end == null) return 0L;
        return Math.max(0L, end.toInstant().toEpochMilli() - start.toInstant().toEpochMilli());
    }

    private static String summarize(ImportExportResult result) {
        return "total=" + result.totalRows()
                + ", success=" + result.successRows()
                + ", fail=" + result.failRows();
    }

    private static String safeMessage(Throwable e) {
        if (e == null) return "未知错误";
        String msg = e.getMessage();
        return (msg != null && !msg.isBlank()) ? msg : e.getClass().getSimpleName();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }

    /**
     * 调用方上下文快照，用于在异步线程中恢复 {@link CurrentUserContext} 与 traceId。
     * record 字段不可变，跨线程安全传递。
     */
    private record ContextSnapshot(String tenantId, String userId, String username, String traceId) {
        static ContextSnapshot capture() {
            return new ContextSnapshot(
                    CurrentUserContext.getTenantId(),
                    CurrentUserContext.getUserId(),
                    CurrentUserContext.getUsername(),
                    TraceContext.getTraceId());
        }

        static void apply(ContextSnapshot ctx) {
            CurrentUserContext.set(ctx.userId(), ctx.tenantId(), ctx.username());
        }
    }
}
