package com.yutong.sample.extsync.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 同步错误明细 (死信队列)。设计来源: 35-样例业务矩阵扩展设计 P2 外部接口同步。
 *
 * <p>同步失败的单条业务记录, 支持重试和死信状态机。
 * <p>状态机: PENDING → RETRYING → RESOLVED / DEAD_LETTER。
 * <p>死信条件: retry_count >= 任务 max_retry_count。
 *
 * <p>幂等保证: (task_id, business_key) 未解决错误唯一索引, 防止重复入队。
 */
@Getter
@Setter
@TableName("ext_sync_error")
public class ExtSyncError extends BaseEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RETRYING = "RETRYING";
    public static final String STATUS_RESOLVED = "RESOLVED";
    public static final String STATUS_DEAD_LETTER = "DEAD_LETTER";

    /** 关联同步记录 ID */
    private String recordId;
    /** 关联同步任务 ID */
    private String taskId;
    /** 业务键 (用于幂等定位) */
    private String businessKey;
    /** 业务负载 (失败的原始数据) */
    private String payload;
    /** 错误码 */
    private String errorCode;
    /** 错误消息 */
    private String errorMessage;
    /** HTTP 状态码 */
    private Integer httpStatus;
    /** 状态: PENDING / RETRYING / RESOLVED / DEAD_LETTER */
    private String status;
    /** 重试次数 */
    private Integer retryCount;
    /** 上次重试时间 */
    private OffsetDateTime lastRetryTime;
    /** 解决时间 */
    private OffsetDateTime resolvedTime;
}
