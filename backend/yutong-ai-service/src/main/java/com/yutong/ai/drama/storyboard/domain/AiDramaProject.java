package com.yutong.ai.drama.storyboard.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 短剧项目聚合根 — 分镜视频生成与成片合成。
 * 设计来源: V060 ai_drama_project, docs/compose/spec/ai-depth-parity.md S2.2
 * compose_status: NONE/PENDING/RUNNING/SUCCESS/FAILED
 */
@Getter
@Setter
@TableName(value = "ai_drama_project", autoResultMap = true)
public class AiDramaProject extends BaseEntity {

    public static final String COMPOSE_NONE = "NONE";
    public static final String COMPOSE_PENDING = "PENDING";
    public static final String COMPOSE_RUNNING = "RUNNING";
    public static final String COMPOSE_SUCCESS = "SUCCESS";
    public static final String COMPOSE_FAILED = "FAILED";

    /** 项目标题 */
    private String title;

    /** 剧情梗概 */
    private String synopsis;

    /** 画风: realistic/anime/cartoon/cinematic 等 */
    @TableField("art_style")
    private String artStyle;

    /** 风格参考 URL/描述 */
    @TableField("style_ref")
    private String styleRef;

    /** 画幅: 16:9 / 9:16 / 1:1 / 4:3 */
    @TableField("aspect_ratio")
    private String aspectRatio;

    /** 成片状态: NONE 未合成 / PENDING 待重算 / RUNNING / SUCCESS / FAILED */
    @TableField("compose_status")
    private String composeStatus;

    /** 成片合成任务 ID (drama_compose_job.id) */
    @TableField("compose_job_id")
    private String composeJobId;

    /** 成片输出路径 */
    @TableField("composed_path")
    private String composedPath;

    /** 扩展元数据 JSON */
    @TableField("meta_json")
    private String metaJson;

    /** 详情响应附属分镜列表（非表列） */
    @TableField(exist = false)
    private java.util.List<AiDramaStoryboard> storyboards;
}
