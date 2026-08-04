package com.yutong.system.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.auth.DataScopeResolver;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.auth.DataScope;
import com.yutong.common.auth.DataScopeType;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.system.message.domain.SysTodoTask;
import com.yutong.system.message.mapper.SysTodoTaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 轻量待办服务。设计来源: 98-后端实现蓝图系统基础接口补齐规则
 * 查询范围限定当前用户(assignee_id=当前用户)，按 created_time desc 排序。
 *
 * <p>GA2-DS: pageTodos 接入 DataScope 数据权限过滤。
 * admin (DataScope.scopeType() == ALL/TENANT) 可查看租户内全部待办；非 admin 仅查看 assignee_id 匹配当前用户的待办。
 */
@Service
public class TodoService {

    /** 待办资源编码，对齐 permissions.yaml system:todo:* 命名。 */
    public static final String RESOURCE_CODE = "system:todo";

    private final SysTodoTaskMapper todoTaskMapper;
    private final DataScopeResolver dataScopeResolver;

    public TodoService(SysTodoTaskMapper todoTaskMapper, DataScopeResolver dataScopeResolver) {
        this.todoTaskMapper = todoTaskMapper;
        this.dataScopeResolver = dataScopeResolver;
    }

    /**
     * 分页查询待办，按 created_time desc。
     * GA2-DS: admin (ALL/TENANT) 可查看租户内全部待办；非 admin 仅查看 assignee_id 匹配当前用户的待办。
     */
    public PageResult<SysTodoTask> pageTodos(PageRequest request, String todoStatus) {
        DataScope scope = dataScopeResolver.resolve(RESOURCE_CODE);
        boolean canReadAll = scope != null
                && (scope.scopeType() == DataScopeType.ALL || scope.scopeType() == DataScopeType.TENANT);
        LambdaQueryWrapper<SysTodoTask> wrapper = new LambdaQueryWrapper<SysTodoTask>()
                .eq(SysTodoTask::getTenantId, CurrentUserContext.getTenantId())
                .eq(!canReadAll, SysTodoTask::getAssigneeId, CurrentUserContext.getUserId())
                .eq(todoStatus != null && !todoStatus.isBlank(), SysTodoTask::getTodoStatus, todoStatus)
                .orderByDesc(SysTodoTask::getCreatedTime);
        Page<SysTodoTask> page = todoTaskMapper.selectPage(
                new Page<>(request.page(), request.size()), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), request.page(), request.size());
    }

    /** 当前用户待办数(PENDING)。 */
    public long getPendingCount() {
        LambdaQueryWrapper<SysTodoTask> wrapper = new LambdaQueryWrapper<SysTodoTask>()
                .eq(SysTodoTask::getTenantId, CurrentUserContext.getTenantId())
                .eq(SysTodoTask::getAssigneeId, CurrentUserContext.getUserId())
                .eq(SysTodoTask::getTodoStatus, "PENDING");
        Long count = todoTaskMapper.selectCount(wrapper);
        return count != null ? count : 0L;
    }

    /** 创建待办(内部调用，供申请单提交触发)。 */
    @Transactional
    public SysTodoTask createTodo(SysTodoTask todo) {
        todoTaskMapper.insert(todo);
        return todo;
    }

    /** 完成待办，todo_status=DONE, completed_time=now。检查待办存在且属于当前用户。 */
    @Transactional
    public void completeTodo(String id) {
        SysTodoTask existing = todoTaskMapper.selectById(id);
        if (existing == null || !CurrentUserContext.getUserId().equals(existing.getAssigneeId())) {
            throw new ResourceNotFoundException("待办不存在: " + id);
        }
        existing.setTodoStatus("DONE");
        existing.setCompletedTime(OffsetDateTime.now());
        todoTaskMapper.updateById(existing);
    }

    /** 取消某业务单据的所有 PENDING 待办。 */
    @Transactional
    public void cancelTodoByBiz(String bizType, String bizId) {
        LambdaUpdateWrapper<SysTodoTask> wrapper = new LambdaUpdateWrapper<SysTodoTask>()
                .eq(SysTodoTask::getTenantId, CurrentUserContext.getTenantId())
                .eq(SysTodoTask::getBizType, bizType)
                .eq(SysTodoTask::getBizId, bizId)
                .eq(SysTodoTask::getTodoStatus, "PENDING")
                .set(SysTodoTask::getTodoStatus, "CANCELLED");
        todoTaskMapper.update(null, wrapper);
    }

    /** 查询某业务的待办。 */
    public List<SysTodoTask> getByBiz(String bizType, String bizId) {
        LambdaQueryWrapper<SysTodoTask> wrapper = new LambdaQueryWrapper<SysTodoTask>()
                .eq(SysTodoTask::getTenantId, CurrentUserContext.getTenantId())
                .eq(SysTodoTask::getBizType, bizType)
                .eq(SysTodoTask::getBizId, bizId)
                .orderByDesc(SysTodoTask::getCreatedTime);
        return todoTaskMapper.selectList(wrapper);
    }
}
