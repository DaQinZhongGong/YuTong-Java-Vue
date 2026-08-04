package com.yutong.sample.extsync.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.auth.DataScopeResolver;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.auth.DataScope;
import com.yutong.common.auth.DataScopeType;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.id.IdGenerator;
import com.yutong.sample.extsync.domain.ExtSyncError;
import com.yutong.sample.extsync.domain.ExtSyncRecord;
import com.yutong.sample.extsync.domain.ExtSyncTask;
import com.yutong.sample.extsync.domain.ExtSystem;
import com.yutong.sample.extsync.dto.ExtSyncStatsVO;
import com.yutong.sample.extsync.dto.SaveExtSyncTaskRequest;
import com.yutong.sample.extsync.dto.TriggerSyncRequest;
import com.yutong.sample.extsync.mapper.ExtSyncErrorMapper;
import com.yutong.sample.extsync.mapper.ExtSyncRecordMapper;
import com.yutong.sample.extsync.mapper.ExtSyncTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 同步任务应用服务。设计来源: 35-样例业务矩阵扩展设计 P2 外部接口同步。
 *
 * <p>提供同步任务 CRUD + 手动触发同步执行 + 错误队列管理 + 同步监控统计。
 */
@Service
public class ExtSyncTaskApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ExtSyncTaskApplicationService.class);

    /** 同步任务资源编码，对齐 permissions.yaml biz:ext-sync:task:* 命名。 */
    public static final String RESOURCE_CODE = "biz:ext-sync:task";

    private final ExtSyncTaskMapper taskMapper;
    private final ExtSyncRecordMapper recordMapper;
    private final ExtSyncErrorMapper errorMapper;
    private final ExtSystemApplicationService systemService;
    private final ExtSyncExecutor syncExecutor;
    private final DataScopeResolver dataScopeResolver;

    public ExtSyncTaskApplicationService(ExtSyncTaskMapper taskMapper,
                                         ExtSyncRecordMapper recordMapper,
                                         ExtSyncErrorMapper errorMapper,
                                         ExtSystemApplicationService systemService,
                                         ExtSyncExecutor syncExecutor,
                                         DataScopeResolver dataScopeResolver) {
        this.taskMapper = taskMapper;
        this.recordMapper = recordMapper;
        this.errorMapper = errorMapper;
        this.systemService = systemService;
        this.syncExecutor = syncExecutor;
        this.dataScopeResolver = dataScopeResolver;
    }

    public Page<ExtSyncTask> pageTasks(int pageNo, int pageSize, String taskCode, String taskName,
                                       String systemId, String status) {
        DataScope scope = dataScopeResolver.resolve(RESOURCE_CODE);
        Page<ExtSyncTask> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<ExtSyncTask> wrapper = new LambdaQueryWrapper<ExtSyncTask>()
                .eq(ExtSyncTask::getTenantId, CurrentUserContext.getTenantId())
                .orderByDesc(ExtSyncTask::getCreatedTime);
        if (taskCode != null && !taskCode.isBlank()) {
            wrapper.eq(ExtSyncTask::getTaskCode, taskCode);
        }
        if (taskName != null && !taskName.isBlank()) {
            wrapper.like(ExtSyncTask::getTaskName, taskName);
        }
        if (systemId != null && !systemId.isBlank()) {
            wrapper.eq(ExtSyncTask::getSystemId, systemId);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(ExtSyncTask::getStatus, status);
        }
        applyDataScope(wrapper, scope);
        return taskMapper.selectPage(page, wrapper);
    }

    /**
     * GA2-DS: 对 LambdaQueryWrapper 追加 DataScope 过滤条件。
     * ExtSyncTask 实体无 owner_user_id 字段，使用 created_by (BaseEntity) 作为 owner 字段。
     * - ALL/TENANT: 无附加条件 (admin/viewer)
     * - SELF/DEPT/DEPT_AND_CHILD/CUSTOM/NONE: 安全降级为 created_by = currentUserId
     */
    private void applyDataScope(LambdaQueryWrapper<ExtSyncTask> wrapper, DataScope scope) {
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
        wrapper.eq(ExtSyncTask::getCreatedBy, userId);
    }

    public ExtSyncTask getTask(String id) {
        ExtSyncTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new ResourceNotFoundException(ErrorCode.EXT_TASK_NOT_FOUND);
        }
        return task;
    }

    public ExtSyncTask getTaskByCode(String taskCode) {
        return taskMapper.selectOne(new LambdaQueryWrapper<ExtSyncTask>()
                .eq(ExtSyncTask::getTaskCode, taskCode));
    }

    @Transactional
    public ExtSyncTask createTask(SaveExtSyncTaskRequest request) {
        ExtSyncTask existing = getTaskByCode(request.getTaskCode());
        if (existing != null) {
            throw new BusinessException(ErrorCode.EXT_TASK_CODE_DUPLICATE, "同步任务编码已存在: " + request.getTaskCode());
        }
        systemService.getSystem(request.getSystemId());

        ExtSyncTask task = new ExtSyncTask();
        task.setId(IdGenerator.nextId());
        task.setTaskCode(request.getTaskCode());
        task.setTaskName(request.getTaskName());
        task.setSystemId(request.getSystemId());
        task.setDescription(request.getDescription());
        task.setSourceApi(request.getSourceApi());
        task.setHttpMethod(request.getHttpMethod() == null ? ExtSyncTask.METHOD_GET : request.getHttpMethod());
        task.setRequestTemplate(request.getRequestTemplate());
        task.setBusinessKeyField(request.getBusinessKeyField() == null ? "id" : request.getBusinessKeyField());
        task.setSyncMode(request.getSyncMode() == null ? ExtSyncTask.MODE_FULL : request.getSyncMode());
        task.setTargetTable(request.getTargetTable());
        task.setCronExpression(request.getCronExpression());
        task.setStatus(request.getStatus() == null ? ExtSyncTask.STATUS_ACTIVE : request.getStatus());
        try {
            taskMapper.insert(task);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.EXT_TASK_CODE_DUPLICATE, "同步任务编码已存在: " + request.getTaskCode());
        }
        log.info("createTask: code={} system={}", task.getTaskCode(), task.getSystemId());
        return task;
    }

    @Transactional
    public ExtSyncTask updateTask(String id, SaveExtSyncTaskRequest request) {
        ExtSyncTask task = getTask(id);
        systemService.getSystem(request.getSystemId());
        task.setTaskName(request.getTaskName());
        task.setSystemId(request.getSystemId());
        task.setDescription(request.getDescription());
        task.setSourceApi(request.getSourceApi());
        task.setHttpMethod(request.getHttpMethod());
        task.setRequestTemplate(request.getRequestTemplate());
        task.setBusinessKeyField(request.getBusinessKeyField());
        task.setSyncMode(request.getSyncMode());
        task.setTargetTable(request.getTargetTable());
        task.setCronExpression(request.getCronExpression());
        if (request.getStatus() != null) {
            task.setStatus(request.getStatus());
        }
        taskMapper.updateById(task);
        return task;
    }

    @Transactional
    public void deleteTask(String id) {
        ExtSyncTask task = getTask(id);
        taskMapper.deleteById(task.getId());
        log.info("deleteTask: id={} code={}", id, task.getTaskCode());
    }

    /**
     * 手动触发同步任务执行。
     * 同步调用 ExtSyncExecutor (含重试 + 错误队列写入)。
     */
    public ExtSyncRecord triggerSync(String taskId, TriggerSyncRequest request) {
        ExtSyncTask task = getTask(taskId);
        if (!ExtSyncTask.STATUS_ACTIVE.equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.EXT_TASK_DISABLED, "同步任务已停用: " + task.getTaskCode());
        }
        ExtSystem system = systemService.getSystem(task.getSystemId());
        if (!ExtSystem.STATUS_ACTIVE.equals(system.getStatus())) {
            throw new BusinessException(ErrorCode.EXT_TASK_DISABLED, "外部系统已停用: " + system.getSystemCode());
        }
        String triggerType = (request != null && request.getTriggerType() != null)
                ? request.getTriggerType()
                : ExtSyncRecord.TRIGGER_MANUAL;
        log.info("triggerSync: task={} system={} trigger={}", task.getTaskCode(), system.getSystemCode(), triggerType);
        return syncExecutor.execute(task, system, triggerType);
    }

    // ==================== 同步记录 ====================

    public Page<ExtSyncRecord> pageRecords(int pageNo, int pageSize, String taskId, String status, String recordNo) {
        Page<ExtSyncRecord> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<ExtSyncRecord> wrapper = new LambdaQueryWrapper<ExtSyncRecord>()
                .orderByDesc(ExtSyncRecord::getCreatedTime);
        if (taskId != null && !taskId.isBlank()) {
            wrapper.eq(ExtSyncRecord::getTaskId, taskId);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(ExtSyncRecord::getStatus, status);
        }
        if (recordNo != null && !recordNo.isBlank()) {
            wrapper.like(ExtSyncRecord::getRecordNo, recordNo);
        }
        return recordMapper.selectPage(page, wrapper);
    }

    public ExtSyncRecord getRecord(String id) {
        ExtSyncRecord record = recordMapper.selectById(id);
        if (record == null) {
            throw new ResourceNotFoundException(ErrorCode.EXT_RECORD_NOT_FOUND);
        }
        return record;
    }

    // ==================== 错误队列 ====================

    public Page<ExtSyncError> pageErrors(int pageNo, int pageSize, String taskId, String status, String businessKey) {
        Page<ExtSyncError> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<ExtSyncError> wrapper = new LambdaQueryWrapper<ExtSyncError>()
                .orderByDesc(ExtSyncError::getCreatedTime);
        if (taskId != null && !taskId.isBlank()) {
            wrapper.eq(ExtSyncError::getTaskId, taskId);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(ExtSyncError::getStatus, status);
        }
        if (businessKey != null && !businessKey.isBlank()) {
            wrapper.like(ExtSyncError::getBusinessKey, businessKey);
        }
        return errorMapper.selectPage(page, wrapper);
    }

    /**
     * 重试单条错误记录 (PENDING/DEAD_LETTER → RETRYING)。
     * 若重试次数已达上限, 进入 DEAD_LETTER; 否则重试, 成功后 → RESOLVED。
     */
    @Transactional
    public ExtSyncError retryError(String errorId) {
        ExtSyncError error = errorMapper.selectById(errorId);
        if (error == null) {
            throw new ResourceNotFoundException(ErrorCode.EXT_RECORD_NOT_FOUND);
        }
        ExtSyncTask task = getTask(error.getTaskId());
        ExtSystem system = systemService.getSystem(task.getSystemId());

        int newRetryCount = (error.getRetryCount() == null ? 0 : error.getRetryCount()) + 1;
        int maxRetry = system.getMaxRetryCount() == null ? 3 : system.getMaxRetryCount();
        error.setRetryCount(newRetryCount);
        error.setLastRetryTime(OffsetDateTime.now());

        if (newRetryCount >= maxRetry) {
            error.setStatus(ExtSyncError.STATUS_DEAD_LETTER);
            errorMapper.updateById(error);
            log.warn("retryError dead letter: errorId={} retryCount={}/{}", errorId, newRetryCount, maxRetry);
            return error;
        }

        error.setStatus(ExtSyncError.STATUS_RETRYING);
        errorMapper.updateById(error);

        try {
            ExtSyncRecord retryRecord = syncExecutor.execute(task, system, ExtSyncRecord.TRIGGER_MANUAL);
            if (ExtSyncRecord.STATUS_SUCCESS.equals(retryRecord.getStatus())) {
                error.setStatus(ExtSyncError.STATUS_RESOLVED);
                error.setResolvedTime(OffsetDateTime.now());
                errorMapper.updateById(error);
                log.info("retryError resolved: errorId={} retryCount={}", errorId, newRetryCount);
            }
        } catch (Exception e) {
            log.warn("retryError failed: errorId={} err={}", errorId, e.getMessage());
        }
        return error;
    }

    /**
     * 标记死信记录为已解决 (手动处理后归档)。
     */
    @Transactional
    public ExtSyncError resolveError(String errorId) {
        ExtSyncError error = errorMapper.selectById(errorId);
        if (error == null) {
            throw new ResourceNotFoundException(ErrorCode.EXT_RECORD_NOT_FOUND);
        }
        error.setStatus(ExtSyncError.STATUS_RESOLVED);
        error.setResolvedTime(OffsetDateTime.now());
        errorMapper.updateById(error);
        log.info("resolveError: errorId={} from={}", errorId, error.getStatus());
        return error;
    }

    // ==================== 同步监控统计 ====================

    public ExtSyncStatsVO getStats() {
        ExtSyncStatsVO stats = new ExtSyncStatsVO();
        stats.setSystemCount(systemService.listAll() == null ? 0L : (long) systemService.listAll().size());

        long taskTotal = taskMapper.selectCount(null);
        long activeTask = taskMapper.selectCount(new LambdaQueryWrapper<ExtSyncTask>()
                .eq(ExtSyncTask::getStatus, ExtSyncTask.STATUS_ACTIVE));
        stats.setTaskCount(taskTotal);
        stats.setActiveTaskCount(activeTask);

        long recordTotal = recordMapper.selectCount(null);
        stats.setRecordCount(recordTotal);

        OffsetDateTime since = OffsetDateTime.now().minusHours(24);
        long recentRecord = recordMapper.selectCount(new LambdaQueryWrapper<ExtSyncRecord>()
                .ge(ExtSyncRecord::getCreatedTime, since));
        long recentSuccess = recordMapper.selectCount(new LambdaQueryWrapper<ExtSyncRecord>()
                .ge(ExtSyncRecord::getCreatedTime, since)
                .in(ExtSyncRecord::getStatus, ExtSyncRecord.STATUS_SUCCESS, ExtSyncRecord.STATUS_PARTIAL));
        long recentFailed = recordMapper.selectCount(new LambdaQueryWrapper<ExtSyncRecord>()
                .ge(ExtSyncRecord::getCreatedTime, since)
                .eq(ExtSyncRecord::getStatus, ExtSyncRecord.STATUS_FAILED));
        stats.setRecentRecordCount(recentRecord);
        stats.setRecentSuccessCount(recentSuccess);
        stats.setRecentFailedCount(recentFailed);

        long pendingError = errorMapper.selectCount(new LambdaQueryWrapper<ExtSyncError>()
                .in(ExtSyncError::getStatus, ExtSyncError.STATUS_PENDING, ExtSyncError.STATUS_RETRYING));
        long deadLetter = errorMapper.selectCount(new LambdaQueryWrapper<ExtSyncError>()
                .eq(ExtSyncError::getStatus, ExtSyncError.STATUS_DEAD_LETTER));
        stats.setPendingErrorCount(pendingError);
        stats.setDeadLetterCount(deadLetter);

        return stats;
    }

    public List<ExtSyncTask> listActiveTasksWithCron() {
        return taskMapper.selectList(new LambdaQueryWrapper<ExtSyncTask>()
                .eq(ExtSyncTask::getStatus, ExtSyncTask.STATUS_ACTIVE)
                .isNotNull(ExtSyncTask::getCronExpression)
                .ne(ExtSyncTask::getCronExpression, ""));
    }
}
