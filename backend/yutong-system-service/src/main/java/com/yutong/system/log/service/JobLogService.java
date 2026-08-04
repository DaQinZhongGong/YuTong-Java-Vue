package com.yutong.system.log.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.auth.DataScopeResolver;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.auth.DataScope;
import com.yutong.common.auth.DataScopeType;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.system.log.domain.SysJobLog;
import com.yutong.system.log.mapper.SysJobLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 定时任务日志服务。设计来源: 98-后端实现蓝图系统基础接口补齐规则
 */
@Service
public class JobLogService {

    /** 任务日志资源编码，对齐 permissions.yaml sys:job-log:* 命名。 */
    public static final String RESOURCE_CODE = "sys:job-log";

    private final SysJobLogMapper jobLogMapper;
    private final DataScopeResolver dataScopeResolver;

    public JobLogService(SysJobLogMapper jobLogMapper, DataScopeResolver dataScopeResolver) {
        this.jobLogMapper = jobLogMapper;
        this.dataScopeResolver = dataScopeResolver;
    }

    /** 分页查询任务日志，按 jobName 过滤，按 startTime DESC 排序。admin(ALL/TENANT) 读全部。 */
    public PageResult<SysJobLog> pageLogs(PageRequest request, String jobName, String status) {
        LambdaQueryWrapper<SysJobLog> wrapper = new LambdaQueryWrapper<SysJobLog>()
                .eq(SysJobLog::getTenantId, CurrentUserContext.getTenantId())
                .like(jobName != null && !jobName.isBlank(), SysJobLog::getJobName, jobName)
                .eq(status != null && !status.isBlank(), SysJobLog::getStatus, status)
                .orderByDesc(SysJobLog::getStartTime);
        // GA2-DS: 接入 DataScope 过滤，admin(ALL/TENANT) 读全部，非 admin 按 created_by 过滤
        applyDataScope(wrapper, dataScopeResolver.resolve(RESOURCE_CODE));
        Page<SysJobLog> page = jobLogMapper.selectPage(
                new Page<>(request.page(), request.size()), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), request.page(), request.size());
    }

    /**
     * GA2-DS: 对 LambdaQueryWrapper 追加 DataScope 过滤条件。
     * - ALL/TENANT: 无附加条件 (admin 读全部)
     * - 其它(SELF/DEPT/CUSTOM/NONE 等): created_by = currentUserId，userId 缺失时安全降级 1=0
     */
    private void applyDataScope(LambdaQueryWrapper<SysJobLog> wrapper, DataScope scope) {
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

    /** 查询任务日志详情。 */
    public SysJobLog getLog(String id) {
        SysJobLog log = jobLogMapper.selectById(id);
        if (log == null) throw new ResourceNotFoundException("任务日志不存在: " + id);
        return log;
    }

    /** 重试任务，仅更新 status 为 PENDING 并返回日志记录。 */
    @Transactional
    public SysJobLog retryJob(String id) {
        SysJobLog existing = jobLogMapper.selectById(id);
        if (existing == null) {
            throw new ResourceNotFoundException("任务日志不存在: " + id);
        }
        existing.setStatus("PENDING");
        jobLogMapper.updateById(existing);
        return existing;
    }
}
