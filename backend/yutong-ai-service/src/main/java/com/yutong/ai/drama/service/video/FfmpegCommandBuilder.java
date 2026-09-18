package com.yutong.ai.drama.service.video;

import java.util.ArrayList;
import java.util.List;

/**
 * ffmpeg 命令拼装器 — 纯函数, 不实际执行
 * 设计来源: platform-drama FfmpegCommandBuilder + ADR 0004 P2-D 短剧 ffmpeg
 *
 * 落点: 短剧工坊-视频合成阶段 (合成配音 + 镜头图 + 转场 + 字幕)
 *
 * 用法:
 *   FfmpegCommandBuilder.ComposeRequest req = new FfmpegCommandBuilder.ComposeRequest(
 *       "ffmpeg",                          // ffmpeg 二进制路径 (生产建议从配置注入, 默认 PATH ffmpeg)
 *       List.of("shot1.mp4", "shot2.mp4"), // 镜头片段 (按顺序)
 *       List.of("audio1.mp3", "audio2.mp3"), // 配音片段
 *       "subtitle.srt",                    // 字幕文件
 *       "720p",                            // 分辨率
 *       "output.mp4",                      // 输出文件
 *       5                                  // 镜头间隔过渡秒数
 *   );
 *   List<String> cmd = FfmpegCommandBuilder.compose(req);
 *   // 外部用 ProcessBuilder 实际执行
 *
 * 安全: 命令拼装是纯函数, 不引入外部依赖 (ffmpeg 二进制需在 docker 镜像 / 宿主机预装)
 *       docker 镜像内嵌: Dockerfile.backend 阶段加 `apt-get install -y ffmpeg`
 */
public final class FfmpegCommandBuilder {

    private FfmpegCommandBuilder() {
    }

    /**
     * 短剧合成请求
     *
     * @param ffmpegPath    ffmpeg 二进制路径 (默认 "ffmpeg" 走 PATH; 生产建议绝对路径如 /usr/bin/ffmpeg)
     * @param shotVideos    镜头视频文件路径列表 (按顺序拼接, 长度 >= 1)
     * @param audioTracks   配音音轨文件路径列表 (可空, 与视频等长或单轨)
     * @param subtitleFile  字幕文件路径 (SRT/ASS; 可空)
     * @param resolution    分辨率 ("480p" / "720p" / "1080p")
     * @param outputPath    输出文件路径 (建议 .mp4)
     * @param transitionSec 镜头间过渡秒数 (xfade duration; 0 = 无过渡)
     */
    public record ComposeRequest(
            String ffmpegPath,
            List<String> shotVideos,
            List<String> audioTracks,
            String subtitleFile,
            String resolution,
            String outputPath,
            int transitionSec
    ) {
        public ComposeRequest {
            if (ffmpegPath == null || ffmpegPath.isBlank()) ffmpegPath = "ffmpeg";
            if (shotVideos == null || shotVideos.isEmpty()) {
                throw new IllegalArgumentException("shotVideos 不能为空");
            }
            if (outputPath == null || outputPath.isBlank()) {
                throw new IllegalArgumentException("outputPath 不能为空");
            }
            if (transitionSec < 0 || transitionSec > 10) {
                throw new IllegalArgumentException("transitionSec 必须在 0-10 之间");
            }
        }
    }

    /**
     * 分辨率预设
     */
    public record Resolution(int width, int height) {}

    /**
     * 解析分辨率字符串
     */
    public static Resolution parseResolution(String res) {
        if (res == null) return new Resolution(1280, 720);
        return switch (res.toLowerCase()) {
            case "480p" -> new Resolution(854, 480);
            case "720p" -> new Resolution(1280, 720);
            case "1080p" -> new Resolution(1920, 1080);
            default -> new Resolution(1280, 720);
        };
    }

    /**
     * 拼装完整 ffmpeg 命令
     *
     * 命令结构:
     *   ffmpeg -y \
     *     [全局 -i 输入] (依次叠加 shot 视频 + audio 音轨) \
     *     -filter_complex "[v]xfade=... [vout]; [a]amix=... [aout]" \
     *     -map "[vout]" -map "[aout]" \
     *     -s WxH -c:v libx264 -preset medium -crf 23 \
     *     -c:a aac -b:a 128k \
     *     [字幕 -vf subtitles=] \
     *     outputPath
     *
     * 简化版: 单轨音 + 简单 concat, 不做复杂 xfade / amix; 转场通过 segment-to-segment
     *   -filter_complex concat=n=N:v=1:a=1
     *
     * 后续可扩展: 视频转场 (xfade), 音频混音 (amix), 字幕烧录 (subtitles=)
     */
    public static List<String> compose(ComposeRequest req) {
        List<String> cmd = new ArrayList<>();
        cmd.add(req.ffmpegPath());
        cmd.add("-y"); // 覆盖输出
        cmd.add("-hide_banner");
        cmd.add("-loglevel");
        cmd.add("error");

        // 输入: 全部 shot 视频
        for (String shot : req.shotVideos()) {
            cmd.add("-i");
            cmd.add(shot);
        }
        // 输入: 全部 audio 音轨 (如有)
        int audioCount = 0;
        if (req.audioTracks() != null) {
            for (String audio : req.audioTracks()) {
                if (audio != null && !audio.isBlank()) {
                    cmd.add("-i");
                    cmd.add(audio);
                    audioCount++;
                }
            }
        }
        // 字幕 (如有)
        if (req.subtitleFile() != null && !req.subtitleFile().isBlank()) {
            cmd.add("-i");
            cmd.add(req.subtitleFile());
        }

        int n = req.shotVideos().size();

        // filter_complex: 视频 / 音频分别 concat
        StringBuilder fc = new StringBuilder();
        if (n == 1) {
            // 单片段: 直接拷贝 (不写 filter_complex)
        } else {
            // 多片段: concat
            StringBuilder vIn = new StringBuilder();
            StringBuilder aIn = new StringBuilder();
            for (int i = 0; i < n; i++) {
                vIn.append("[").append(i).append(":v]");
                aIn.append("[").append(i).append(":a]");
            }
            fc.append(vIn).append("concat=n=").append(n).append(":v=1:a=1[v][a]");
        }
        if (!fc.isEmpty()) {
            cmd.add("-filter_complex");
            cmd.add(fc.toString());
        }
        if (n > 1) {
            cmd.add("-map"); cmd.add("[v]");
            cmd.add("-map"); cmd.add("[a]");
        } else {
            cmd.add("-map"); cmd.add("0:v");
            cmd.add("-map"); cmd.add("0:a");
        }

        // 视频编码
        Resolution r = parseResolution(req.resolution());
        cmd.add("-s"); cmd.add(r.width() + "x" + r.height());
        cmd.add("-c:v"); cmd.add("libx264");
        cmd.add("-preset"); cmd.add("medium");
        cmd.add("-crf"); cmd.add("23");
        cmd.add("-pix_fmt"); cmd.add("yuv420p");

        // 音频编码
        cmd.add("-c:a"); cmd.add("aac");
        cmd.add("-b:a"); cmd.add("128k");

        // 字幕烧录
        if (req.subtitleFile() != null && !req.subtitleFile().isBlank()) {
            int subIndex = n + audioCount; // 最后 1 个输入
            // 用 ass/srt 时使用 subtitles= 滤镜
            cmd.add("-vf"); cmd.add("subtitles=" + req.subtitleFile().replace("\\", "/").replace(":", "\\:"));
        }

        // 时长: 短剧默认 5 分钟内, 不强制 -t, 让 ffmpeg 跟随输入
        // (后续可加 -t 00:05:00 限制最大时长)

        cmd.add(req.outputPath());
        return cmd;
    }

    /**
     * 拼装 ffprobe 命令 (探测媒体元信息)
     */
    public static List<String> probe(String ffprobePath, String mediaFile) {
        if (ffprobePath == null || ffprobePath.isBlank()) ffprobePath = "ffprobe";
        if (mediaFile == null || mediaFile.isBlank()) {
            throw new IllegalArgumentException("mediaFile 不能为空");
        }
        return List.of(
                ffprobePath,
                "-v", "error",
                "-show_format",
                "-show_streams",
                "-of", "json",
                mediaFile
        );
    }
}
