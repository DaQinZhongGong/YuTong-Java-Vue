package com.yutong.sample.drama.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 短剧音频实体 — 配音/音效。
 * 设计来源: V043 drama_audio, 归属 drama_script, voiceId 关联 TTS 音色
 */
@Getter
@Setter
@TableName(value = "drama_audio", autoResultMap = true)
public class DramaAudio extends BaseEntity {

    /** 所属剧本 ID (drama_script.id) */
    private String dramaId;

    /** 音频名称 */
    private String name;

    /** 音频文件 URL */
    private String audioUrl;

    /** 时长秒数 */
    private Integer durationSeconds;

    /** 音色 ID（TTS 声库） */
    private String voiceId;
}
