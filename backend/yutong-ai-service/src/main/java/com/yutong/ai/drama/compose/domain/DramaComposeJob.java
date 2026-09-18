package com.yutong.ai.drama.compose.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 短剧合成任务。设计来源: platform-drama CompositionWorker + ADR 0004 P2-D 批次 5-C。
 *
 * <p>真实调用 ffmpeg 二进制执行合成 (多镜头 concat + 配音 + 字幕烧录),
 * 命令由 {@code FfmpegCommandBuilder} 拼装, 本实体记录任务生命周期。
 * 状态机: PENDING → RUNNING → SUCCESS / FAILED。失败关闭: 任何执行异常
 * (二进制缺失/超时/非 0 退出码) 均落 FAILED + errorMessage, 禁止返回假 URL。
 */
@Getter
@Setter
@TableName("drama_compose_job")
public class DramaComposeJob extends BaseEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    /** 任务标题 (便于列表展示) */
    private String title;

    /** 镜头视频路径 JSON 数组 (服务端 workDir 下相对路径, 已做越权校验) */
    @TableField("shot_videos_json")
    private String shotVideosJson;

    /** 配音音轨路径 JSON 数组 (可空) */
    @TableField("audio_tracks_json")
    private String audioTracksJson;

    /** 字幕文件路径 (workDir 下相对路径, 可空) */
    @TableField("subtitle_file")
    private String subtitleFile;

    /** 分辨率: 480p/720p/1080p (默认 720p) */
    private String resolution;

    /** 镜头间过渡秒数 0-10 (当前 concat 实现保留字段, xfade 后续扩展) */
    @TableField("transition_sec")
    private Integer transitionSec;

    /** 输出文件路径 (服务端分配: outputs/{jobId}.mp4) */
    @TableField("output_path")
    private String outputPath;

    /** 输出文件大小 (字节, 成功后 ffprobe/文件系统回填) */
    @TableField("output_size")
    private Long outputSize;

    /** 成片时长 (秒, 成功后 ffprobe 回填) */
    @TableField("duration_sec")
    private Double durationSec;

    /** 状态: PENDING/RUNNING/SUCCESS/FAILED */
    private String status;

    /** 失败原因 (超时/退出码/二进制缺失等, 成功时为空) */
    @TableField("error_message")
    private String errorMessage;

    /** ffmpeg 执行日志尾部 (截断 4KB, 便于排障) */
    @TableField("log_tail")
    private String logTail;

    /** ffmpeg 退出码 (未执行/超时 kill 时为 null) */
    @TableField("exit_code")
    private Integer exitCode;

    /** 开始执行时间 */
    @TableField("started_at")
    private LocalDateTime startedAt;

    /** 执行结束时间 */
    @TableField("finished_at")
    private LocalDateTime finishedAt;
}
