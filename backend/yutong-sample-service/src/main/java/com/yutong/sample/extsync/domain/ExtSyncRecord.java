package com.yutong.sample.extsync.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 同步记录。设计来源: 35-样例业务矩阵扩展设计 P2 外部接口同步。
 *
 * <p>一次同步执行产生一条记录, 包含统计信息和请求/响应快照。
 * <p>状态机: PENDING → RUNNING → SUCCESS / FAILED / PARTIAL。
 *
 * <p>同步监控: total_count / success_count / failed_count + duration_ms。
 */
@Getter
@Setter
@TableName("ext_sync_record")
public class ExtSyncRecord extends BaseEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_PARTIAL = "PARTIAL";

    public static final String TRIGGER_MANUAL = "MANUAL";
    public static final String TRIGGER_SCHEDULED = "SCHEDULED";

    /** 记录编号 (唯一) */
    private String recordNo;
    /** 关联同步任务 ID */
    private String taskId;
    /** 关联外部系统 ID */
    private String systemId;
    /** 批次号 */
    private String batchNo;
    /** 状态: PENDING / RUNNING / SUCCESS / FAILED / PARTIAL */
    private String status;
    /** 触发方式: MANUAL / SCHEDULED */
    private String triggerType;
    /** 总条数 */
    private Integer totalCount;
    /** 成功条数 */
    private Integer successCount;
    /** 失败条数 */
    private Integer failedCount;
    /** 请求快照 */
    private String requestSnapshot;
    /** 响应快照 */
    private String responseSnapshot;
    /** HTTP 状态码 */
    private Integer httpStatus;
    /** 错误消息 */
    private String errorMessage;
    /** 开始时间 */
    private OffsetDateTime startedTime;
    /** 结束时间 */
    private OffsetDateTime finishedTime;
    /** 耗时 (毫秒) */
    private Long durationMs;
}
