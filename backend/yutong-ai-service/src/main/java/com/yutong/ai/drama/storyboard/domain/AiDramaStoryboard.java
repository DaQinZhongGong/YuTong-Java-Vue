package com.yutong.ai.drama.storyboard.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 短剧分镜 — video_status 状态机 PENDING→GENERATING→SUCCEEDED/FAILED。
 * 设计来源: V060 ai_drama_storyboard, docs/compose/spec/ai-depth-parity.md S2.2
 * last_frame_url 供同 location 下一镜作首帧承接。
 */
@Getter
@Setter
@TableName(value = "ai_drama_storyboard", autoResultMap = true)
public class AiDramaStoryboard extends BaseEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_GENERATING = "GENERATING";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";

    /** 所属项目 ID */
    @TableField("project_id")
    private String projectId;

    /** 场次号 (项目内排序, compose 按此拼接) */
    @TableField("scene_no")
    private Integer sceneNo;

    /** 镜别: 近景/中景/远景/特写等 */
    @TableField("shot_type")
    private String shotType;

    /** 场景地点 — 同 location 串行并用上一镜末帧承接 */
    @TableField("location_name")
    private String locationName;

    /** 画面提示词 (文生图/首帧参考用) */
    @TableField("image_prompt")
    private String imagePrompt;

    /** 视频提示词 */
    @TableField("video_prompt")
    private String videoPrompt;

    /** 时长秒数 (0,60] */
    @TableField("duration_seconds")
    private BigDecimal durationSeconds;

    /** 参考图 URL 数组 JSON; ≥2 走多参考 image-to-video */
    @TableField("reference_images")
    private String referenceImages;

    /** 状态: PENDING/GENERATING/SUCCEEDED/FAILED */
    @TableField("video_status")
    private String videoStatus;

    /** 厂商异步任务 ID */
    @TableField("video_id")
    private String videoId;

    /** 成功后的视频 URL */
    @TableField("video_url")
    private String videoUrl;

    /** 本镜末帧 URL, 供同场景下一镜首帧 */
    @TableField("last_frame_url")
    private String lastFrameUrl;

    /** ai_media_job.id */
    @TableField("media_job_id")
    private String mediaJobId;

    /** 失败原因 */
    @TableField("error_message")
    private String errorMessage;
}
