package com.yutong.ai.ops.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 知识库日统计。设计来源: 35-样例业务矩阵扩展设计 P2 知识库运营。
 * 每个知识库每日一条统计记录。
 */
@Getter
@Setter
@TableName("kb_stats_daily")
public class KbStatsDaily extends BaseEntity {
    private LocalDate statDate;
    private String kbId;
    private Integer documentCount;
    private Integer chunkCount;
    private Integer embeddingCount;
    private Integer conversationCount;
    private Integer hitCount;
    private Integer refusedCount;
    private Double avgMaxScore;
    private Long avgLatencyMs;
}
