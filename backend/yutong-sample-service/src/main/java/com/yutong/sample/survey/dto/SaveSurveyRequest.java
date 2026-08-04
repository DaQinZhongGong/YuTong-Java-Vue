package com.yutong.sample.survey.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 创建/更新问卷请求。设计来源: 35-样例业务矩阵扩展设计 P2 问卷表单 (GA2-42)。
 */
@Data
public class SaveSurveyRequest {

    /** 问卷标题 */
    @NotBlank(message = "问卷标题不能为空")
    @Size(max = 256, message = "问卷标题长度不能超过 256")
    private String title;

    /** 问卷描述 */
    @Size(max = 1024, message = "问卷描述长度不能超过 1024")
    private String description;

    /** 问卷分类 */
    @Size(max = 64, message = "问卷分类长度不能超过 64")
    private String category;

    /** 是否匿名 */
    private Boolean anonymous;

    /** 每个用户可填写次数 (0 表示不限制) */
    private Integer maxResponsesPerUser;

    /** 计划开始时间 */
    private OffsetDateTime startTime;

    /** 计划结束时间 */
    private OffsetDateTime endTime;

    /** 主题配置 JSON */
    private String themeJson;

    /** 备注 */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;
}
