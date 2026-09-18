package com.yutong.ai.skill.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * Skill 执行审计。设计来源: V044 ai_skill_run，业界同类实现 技能管理「执行日志」。
 */
@Getter
@Setter
@TableName("ai_skill_run")
public class AiSkillRun extends BaseEntity {

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    private String skillId;
    private String skillCode;
    private String action;
    private String status;

    @TableField("input_json")
    private String inputJson;

    @TableField("output_json")
    private String outputJson;

    private String outputFileId;
    private String errorMessage;
    private Integer latencyMs;
}
