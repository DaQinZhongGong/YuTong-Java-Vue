package com.yutong.sample.survey.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 问卷监控统计 VO。设计来源: 35-样例业务矩阵扩展设计 P2 问卷表单 (GA2-42)。
 *
 * <p>仪表盘展示:
 * <ul>
 *   <li>totalSurveys: 问卷总数</li>
 *   <li>statusCounts: 按状态分布 (DRAFT/PUBLISHED/COLLECTING/CLOSED/ARCHIVED)</li>
 *   <li>collectingCount: 收集中的问卷数 (用户可填写)</li>
 *   <li>totalResponses: 答卷总数</li>
 *   <li>submittedCount: 已提交答卷数</li>
 *   <li>inProgressCount: 进行中答卷数</li>
 *   <li>questionCount: 题目总数</li>
 *   <li>avgDurationMs: 平均作答时长 (毫秒)</li>
 *   <li>avgScore: 平均评分 (RATING 题型累计)</li>
 *   <li>sourceCounts: 按来源渠道分布 (WEB_ADMIN/MOBILE_UNIAPP/API)</li>
 * </ul>
 */
@Data
public class SurveyStatsVO {
    /** 问卷总数 */
    private long totalSurveys;
    /** 按状态分布 */
    private Map<String, Long> statusCounts = new HashMap<>();
    /** 收集中的问卷数 (用户可填写) */
    private long collectingCount;
    /** 答卷总数 */
    private long totalResponses;
    /** 已提交答卷数 */
    private long submittedCount;
    /** 进行中答卷数 */
    private long inProgressCount;
    /** 题目总数 */
    private long questionCount;
    /** 平均作答时长 (毫秒) */
    private long avgDurationMs;
    /** 平均评分 (RATING 题型累计) */
    private double avgScore;
    /** 按来源渠道分布 */
    private Map<String, Long> sourceCounts = new HashMap<>();
    /** 分类分布 */
    private Map<String, Long> categoryCounts = new HashMap<>();
    /** 最近 7 日提交趋势 (日期 + 数量) */
    private List<DailyCount> recentTrend = new ArrayList<>();

    /**
     * 每日提交数量。
     */
    @Data
    public static class DailyCount {
        /** 日期 (yyyy-MM-dd) */
        private String date;
        /** 提交数 */
        private long count;

        public DailyCount() {}

        public DailyCount(String date, long count) {
            this.date = date;
            this.count = count;
        }
    }
}
