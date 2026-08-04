package com.yutong.sample.extsync.service;

import com.yutong.sample.extsync.domain.ExtSyncRecord;
import com.yutong.sample.extsync.domain.ExtSyncTask;
import com.yutong.sample.extsync.domain.ExtSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 外部接口同步定时调度器。设计来源: 35-样例业务矩阵扩展设计 P2 外部接口同步。
 *
 * <p>每分钟扫描配置了 cron 表达式的活跃任务并触发同步执行。
 * <p>实际生产环境应解析 cron 表达式判断是否到达触发时刻; v1.0 样例使用简化策略:
 * 每分钟扫描 + 触发 (验证 @Scheduled 定时任务能力, 实际触发由 cron 表达式控制)。
 */
@Component
public class ExtSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExtSyncScheduler.class);

    private final ExtSyncTaskApplicationService taskService;
    private final ExtSystemApplicationService systemService;
    private final ExtSyncExecutor syncExecutor;

    public ExtSyncScheduler(ExtSyncTaskApplicationService taskService,
                            ExtSystemApplicationService systemService,
                            ExtSyncExecutor syncExecutor) {
        this.taskService = taskService;
        this.systemService = systemService;
        this.syncExecutor = syncExecutor;
    }

    /**
     * 每分钟扫描定时同步任务 (验证定时任务 + 同步执行链路)。
     * 仅触发配置了 cron_expression 的 ACTIVE 任务。
     */
    @Scheduled(cron = "0 */1 * * * *")
    public void scanScheduledTasks() {
        List<ExtSyncTask> tasks = taskService.listActiveTasksWithCron();
        if (tasks.isEmpty()) {
            return;
        }
        log.info("ext sync scheduler scan: {} tasks", tasks.size());
        for (ExtSyncTask task : tasks) {
            try {
                ExtSystem system = systemService.getSystem(task.getSystemId());
                if (!ExtSystem.STATUS_ACTIVE.equals(system.getStatus())) {
                    continue;
                }
                ExtSyncRecord record = syncExecutor.execute(task, system, ExtSyncRecord.TRIGGER_SCHEDULED);
                log.info("ext sync scheduled done: task={} status={}", task.getTaskCode(), record.getStatus());
            } catch (Exception e) {
                log.error("ext sync scheduled failed: task={} err={}", task.getTaskCode(), e.getMessage());
            }
        }
    }
}
