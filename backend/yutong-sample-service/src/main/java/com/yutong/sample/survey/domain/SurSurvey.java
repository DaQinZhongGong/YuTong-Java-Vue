package com.yutong.sample.survey.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 问卷主表。设计来源: 35-样例业务矩阵扩展设计 P2 问卷表单。
 *
 * <p>GA2-42 落地: 验证动态表单渲染 + 条件显隐 + 字段校验 + 移动端填写 + 统计报表 + AI 生成题目草稿。
 *
 * <p>状态机:
 * <ul>
 *   <li>DRAFT → PUBLISHED (发布, 可选填写)</li>
 *   <li>PUBLISHED → COLLECTING (开始收集答卷)</li>
 *   <li>COLLECTING → CLOSED (提前结束收集)</li>
 *   <li>DRAFT → ARCHIVED (草稿直接归档)</li>
 *   <li>CLOSED → ARCHIVED (关闭后归档)</li>
 * </ul>
 */
@Getter
@Setter
@TableName("sur_survey")
public class SurSurvey extends BaseEntity {
    /** 问卷编号 (业务可读, SURyyyyMMddNNNNNN 格式) */
    private String surveyNo;
    /** 问卷标题 */
    private String title;
    /** 问卷描述 */
    private String description;
    /** 问卷状态: DRAFT / PUBLISHED / COLLECTING / CLOSED / ARCHIVED */
    private String status;
    /** 问卷分类 (如: 客户满意度 / 市场调研 / 内部调研 / 产品反馈) */
    private String category;
    /** 是否匿名 (true: 答卷不记录 respondent_id) */
    private Boolean anonymous;
    /** 每个用户可填写次数 (0 表示不限制) */
    private Integer maxResponsesPerUser;
    /** 计划开始时间 */
    private OffsetDateTime startTime;
    /** 计划结束时间 */
    private OffsetDateTime endTime;
    /** 发布时间 */
    private OffsetDateTime publishedTime;
    /** 关闭时间 */
    private OffsetDateTime closedTime;
    /** 答卷数 (冗余字段, 定期同步 sur_response 统计) */
    private Integer responseCount;
    /** 主题配置 JSON (颜色/logo 等) */
    private String themeJson;
    /** AI 生成草稿来源提示 */
    private String aiDraftPrompt;

    /** status 常量 */
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_COLLECTING = "COLLECTING";
    public static final String STATUS_CLOSED = "CLOSED";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    /** source 常量 (答卷来源) */
    public static final String SOURCE_WEB_ADMIN = "WEB_ADMIN";
    public static final String SOURCE_MOBILE_UNIAPP = "MOBILE_UNIAPP";
    public static final String SOURCE_API = "API";
}
