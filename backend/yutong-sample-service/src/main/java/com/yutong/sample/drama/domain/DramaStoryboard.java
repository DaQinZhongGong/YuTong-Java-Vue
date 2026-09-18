package com.yutong.sample.drama.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 分镜脚本实体 — 关联场景与音频可选。
 * 设计来源: V043 drama_storyboard, 归属 drama_script, sceneId/audioId 可空
 */
@Getter
@Setter
@TableName(value = "drama_storyboard", autoResultMap = true)
public class DramaStoryboard extends BaseEntity {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_GENERATING = "GENERATING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    /** 所属剧本 ID (drama_script.id) */
    private String dramaId;

    /** 关联场景 ID (drama_scene.id)，可空 */
    private String sceneId;

    /** 分镜序号 (>=1, 剧内唯一) */
    private Integer storyboardNo;

    /** 画面 prompt */
    private String prompt;

    /** 分镜图片 URL */
    private String imageUrl;

    /** 关联音频 ID (drama_audio.id)，可空 */
    private String audioId;

    /** 状态: DRAFT/GENERATING/SUCCESS/FAILED */
    private String status;
}
