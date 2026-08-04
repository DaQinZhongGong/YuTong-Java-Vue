package com.yutong.sample.survey.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 答卷表。设计来源: 35-样例业务矩阵扩展设计 P2 问卷表单。
 *
 * <p>GA2-42 落地: 一份问卷的一次作答记录。
 *
 * <p>状态机:
 * <ul>
 *   <li>IN_PROGRESS → SUBMITTED (提交答卷)</li>
 *   <li>IN_PROGRESS → ABANDONED (放弃作答)</li>
 * </ul>
 */
@Getter
@Setter
@TableName("sur_response")
public class SurResponse extends BaseEntity {
    /** 所属问卷 ID */
    private String surveyId;
    /** 答卷编号 (业务可读, RSPyyyyMMddNNNNNN 格式) */
    private String responseNo;
    /** 答卷人 ID (anonymous=true 时为 null) */
    private String respondentId;
    /** 答卷人名称 (冗余, 便于统计展示) */
    private String respondentName;
    /** 答卷状态: IN_PROGRESS / SUBMITTED / ABANDONED */
    private String status;
    /** 来源渠道: WEB_ADMIN / MOBILE_UNIAPP / API */
    private String source;
    /** 开始作答时间 */
    private OffsetDateTime startTime;
    /** 提交时间 */
    private OffsetDateTime submittedTime;
    /** 作答时长 (毫秒) */
    private Long durationMs;
    /** 客户端 IP */
    private String clientIp;
    /** User-Agent */
    private String userAgent;
    /** 答卷总分 (RATING 题型累计, 用于统计) */
    private Integer totalScore;

    /** status 常量 */
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_SUBMITTED = "SUBMITTED";
    public static final String STATUS_ABANDONED = "ABANDONED";
}
