package com.yutong.ai.trace.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 链路追踪监控大盘 VO。设计来源: P2-F 链路追踪监控大盘 (M-3)。
 *
 * <p>聚合口径: 租户内 + 数据权限 (SELF 等收敛为仅本人) + 日期区间 + 可选链路类型。
 * 趋势按日起点补零, 成功率 = SUCCESS / 全部。
 */
@Getter
@Setter
public class AiTraceDashboardVO {

    /** 汇总 */
    private Summary summary;

    /** 按日趋势 (起日→止日连续, 无数据补零) */
    private List<DailyPoint> daily;

    /** 按链路类型分布 */
    private List<TypeStat> byType;

    /** 近期失败 (最多 10) */
    private List<ErrorItem> recentErrors;

    /** 实际查询起日 */
    private LocalDate startDate;

    /** 实际查询止日 */
    private LocalDate endDate;

    @Getter
    @Setter
    public static class Summary {
        private long totalRuns;
        private long successRuns;
        private long failedRuns;
        private long runningRuns;
        /** 成功率 0~100, 无调用时为 null (前端显示 —) */
        private BigDecimal successRate;
        /** 平均耗时毫秒 (仅 latency 非空行参与), 无数据时为 null */
        private Long avgLatencyMs;
        /** 最大耗时毫秒, 无数据时为 null */
        private Integer maxLatencyMs;
    }

    @Getter
    @Setter
    public static class DailyPoint {
        private LocalDate day;
        private long total;
        private long success;
        private long failed;
        private Long avgLatencyMs;
    }

    @Getter
    @Setter
    public static class TypeStat {
        private String traceType;
        private long total;
        private long failed;
        /** 出错率 0~100 */
        private BigDecimal errorRate;
        private Long avgLatencyMs;
    }

    @Getter
    @Setter
    public static class ErrorItem {
        private String id;
        private String traceType;
        private String errorMessage;
        private OffsetDateTime createdTime;
    }

    // ===== Mapper 聚合行 (字段名与 @Select 别名对齐) =====

    /** statDaily 行: day/status/cnt/avgLatency/sumLatency/latencyRuns/maxLatency */
    @Getter
    @Setter
    public static class DailyAggRow {
        private LocalDate day;
        private String status;
        private Long cnt;
        private BigDecimal avgLatency;
        private Long sumLatency;
        private Long latencyRuns;
        private Integer maxLatency;
    }

    /** statByType 行: traceType/total/failed/avgLatency */
    @Getter
    @Setter
    public static class TypeAggRow {
        private String traceType;
        private Long total;
        private Long failed;
        private BigDecimal avgLatency;
    }
}
