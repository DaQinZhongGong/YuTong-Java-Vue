package com.yutong.ai.drama.compose.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 短剧合成提交请求。设计来源: ADR 0004 P2-D 批次 5-C。
 *
 * <p>所有媒体路径均为服务端 workDir 下的相对路径 (如 {@code shots/shot1.mp4}),
 * 绝对路径 / {@code ..} 越权由 service 层拒绝。输出路径由服务端分配, 不接受客户端指定。
 */
@Data
public class SubmitComposeRequest {

    @NotBlank(message = "title 不能为空")
    @Size(max = 200, message = "title 不超过 200 字")
    private String title;

    /** 镜头视频 (按顺序拼接, 1-20 个) */
    @NotEmpty(message = "shotVideos 不能为空")
    @Size(max = 20, message = "shotVideos 最多 20 个")
    private List<@Size(max = 512, message = "单个路径不超过 512 字符") String> shotVideos;

    /** 配音音轨 (可空, 最多 20 个) */
    @Size(max = 20, message = "audioTracks 最多 20 个")
    private List<@Size(max = 512, message = "单个路径不超过 512 字符") String> audioTracks;

    /** 字幕文件 SRT/ASS (可空) */
    @Size(max = 512, message = "subtitleFile 不超过 512 字符")
    private String subtitleFile;

    /** 分辨率档: 480p/720p/1080p */
    @Pattern(regexp = "^(480p|720p|1080p)$", message = "resolution 仅支持 480p/720p/1080p")
    private String resolution = "720p";
}
