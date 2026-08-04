package com.yutong.lowcode.meta.service;

import com.yutong.common.exception.BusinessConflictException;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/**
 * 低代码实体/页面状态机领域服务。设计来源: 14-低代码平台设计
 * 实体: DRAFT → PUBLISHED → DISABLED
 * 页面: DRAFT → PUBLISHED；回滚 = 历史版本复制为草稿
 */
@Service
public class LcDomainService {

    /** 实体动作 → 目标状态 */
    private static final Map<String, String> ENTITY_ACTION_TARGET = Map.of(
            "PUBLISH", "PUBLISHED",
            "DISABLE", "DISABLED",
            "RE_ENABLE", "DRAFT"
    );

    /** 实体动作 → 允许的源状态 */
    private static final Map<String, Set<String>> ENTITY_ALLOWED_SOURCES = Map.of(
            "PUBLISH", Set.of("DRAFT"),
            "DISABLE", Set.of("PUBLISHED", "DRAFT"),
            "RE_ENABLE", Set.of("DISABLED")
    );

    /** 页面动作 → 目标状态 */
    private static final Map<String, String> PAGE_ACTION_TARGET = Map.of(
            "PUBLISH", "PUBLISHED",
            "ROLLBACK", "DRAFT"
    );

    /** 页面动作 → 允许的源状态 */
    private static final Map<String, Set<String>> PAGE_ALLOWED_SOURCES = Map.of(
            "PUBLISH", Set.of("DRAFT"),
            "ROLLBACK", Set.of("PUBLISHED")
    );

    /** 生成任务动作 → 目标状态 */
    private static final Map<String, String> TASK_ACTION_TARGET = Map.of(
            "START", "RUNNING",
            "MARK_SUCCESS", "SUCCESS",
            "MARK_FAILED", "FAILED",
            "MARK_CONFLICT", "CONFLICT",
            "CANCEL", "CANCELLED"
    );

    /** 生成任务动作 → 允许的源状态 */
    private static final Map<String, Set<String>> TASK_ALLOWED_SOURCES = Map.of(
            "START", Set.of("PENDING"),
            "MARK_SUCCESS", Set.of("RUNNING"),
            "MARK_FAILED", Set.of("RUNNING"),
            "MARK_CONFLICT", Set.of("RUNNING"),
            "CANCEL", Set.of("PENDING", "RUNNING")
    );

    public String entityNextStatus(String action) {
        if (action == null) {
            throw new BusinessConflictException("不支持的动作: null");
        }
        String target = ENTITY_ACTION_TARGET.get(action);
        if (target == null) {
            throw new BusinessConflictException("不支持的动作: " + action);
        }
        return target;
    }

    public void validateEntityTransition(String currentStatus, String action) {
        if (action == null) {
            throw new BusinessConflictException("不支持的动作: null");
        }
        Set<String> allowed = ENTITY_ALLOWED_SOURCES.get(action);
        if (allowed == null) {
            throw new BusinessConflictException("不支持的动作: " + action);
        }
        if (!allowed.contains(currentStatus)) {
            throw new BusinessConflictException(
                    "实体当前状态[" + currentStatus + "]不允许执行[" + action + "]操作");
        }
    }

    public String pageNextStatus(String action) {
        if (action == null) {
            throw new BusinessConflictException("不支持的动作: null");
        }
        String target = PAGE_ACTION_TARGET.get(action);
        if (target == null) {
            throw new BusinessConflictException("不支持的动作: " + action);
        }
        return target;
    }

    public void validatePageTransition(String currentStatus, String action) {
        if (action == null) {
            throw new BusinessConflictException("不支持的动作: null");
        }
        Set<String> allowed = PAGE_ALLOWED_SOURCES.get(action);
        if (allowed == null) {
            throw new BusinessConflictException("不支持的动作: " + action);
        }
        if (!allowed.contains(currentStatus)) {
            throw new BusinessConflictException(
                    "页面当前状态[" + currentStatus + "]不允许执行[" + action + "]操作");
        }
    }

    public String taskNextStatus(String action) {
        if (action == null) {
            throw new BusinessConflictException("不支持的动作: null");
        }
        String target = TASK_ACTION_TARGET.get(action);
        if (target == null) {
            throw new BusinessConflictException("不支持的动作: " + action);
        }
        return target;
    }

    public void validateTaskTransition(String currentStatus, String action) {
        if (action == null) {
            throw new BusinessConflictException("不支持的动作: null");
        }
        Set<String> allowed = TASK_ALLOWED_SOURCES.get(action);
        if (allowed == null) {
            throw new BusinessConflictException("不支持的动作: " + action);
        }
        if (!allowed.contains(currentStatus)) {
            throw new BusinessConflictException(
                    "生成任务当前状态[" + currentStatus + "]不允许执行[" + action + "]操作");
        }
    }

    /**
     * 校验主键字段约束: primary_flag=true 时只允许 field_code=id, data_type=STRING
     * 设计来源: 14-低代码平台设计 主键策略
     */
    public void validatePrimaryKey(String fieldCode, String dataType, Boolean primaryFlag) {
        if (Boolean.TRUE.equals(primaryFlag)) {
            if (!"id".equals(fieldCode)) {
                throw new BusinessConflictException(
                        "主键字段只允许 field_code=id，当前: " + fieldCode);
            }
            if (!"STRING".equals(dataType)) {
                throw new BusinessConflictException(
                        "主键字段只允许 data_type=STRING，当前: " + dataType);
            }
        }
    }
}
