package com.yutong.ai.governance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * AI 用户反馈。设计来源: 37-AI治理与评测设计 人工反馈闭环章节
 * <p>
 * 6 类反馈标签 (37 号文档):
 * <ul>
 *   <li>HELPFUL      有帮助</li>
 *   <li>NOT_HELPFUL  没帮助</li>
 *   <li>INACCURATE   不准确</li>
 *   <li>CITATION_ERROR 引用错误</li>
 *   <li>FORMAT_ERROR 格式错误</li>
 *   <li>RISKY        存在风险</li>
 * </ul>
 * 反馈用于: 调整提示词 / 修正文档 / 优化分块 / 更新评测集 / 禁用高风险工具。
 */
@Getter
@Setter
@TableName("ai_feedback")
public class AiFeedback extends BaseEntity {

    public static final String TYPE_HELPFUL = "HELPFUL";
    public static final String TYPE_NOT_HELPFUL = "NOT_HELPFUL";
    public static final String TYPE_INACCURATE = "INACCURATE";
    public static final String TYPE_CITATION_ERROR = "CITATION_ERROR";
    public static final String TYPE_FORMAT_ERROR = "FORMAT_ERROR";
    public static final String TYPE_RISKY = "RISKY";

    public static final String TARGET_ANSWER = "ANSWER";
    public static final String TARGET_TOOL = "TOOL";
    public static final String TARGET_GENERATION = "GENERATION";

    /** 反馈类型: 6 类标签 */
    private String feedbackType;
    /** 目标类型: ANSWER / TOOL / GENERATION */
    private String targetType;
    /** 目标 ID: 消息 ID / 工具调用日志 ID / 生成草稿 ID */
    private String targetId;
    private String conversationId;
    private String messageId;
    private String userId;
    private String scenario;
    private String modelCode;
    /** 1~5 星评分, 可空 */
    private Integer rating;
    /** 反馈标签 JSON 数组, 支持多标签 */
    private String tagsJson;
    private String commentText;
    private String traceId;
    /** 是否已处理 */
    private Boolean handled;
    private String handledBy;
    private OffsetDateTime handledTime;
    private String handleResult;
}
