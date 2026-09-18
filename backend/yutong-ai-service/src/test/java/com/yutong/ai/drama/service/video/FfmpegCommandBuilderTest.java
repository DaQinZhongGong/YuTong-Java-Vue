package com.yutong.ai.drama.service.video;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ffmpeg 命令拼装器单元测试 (纯函数, 不实际执行 ffmpeg)
 * 设计来源: ADR 0004 P2-D 短剧 ffmpeg 真合成
 *
 * 覆盖:
 *  - 解析分辨率字符串 (480p/720p/1080p/null)
 *  - 拼装单镜头命令
 *  - 拼装多镜头命令 (concat filter)
 *  - 拼装带音轨命令
 *  - 拼装带字幕命令
 *  - 拼装带过渡命令
 *  - ffprobe 命令
 *  - 校验: 镜头不能为空 / 输出不能为空 / 过渡 0-10s
 */
class FfmpegCommandBuilderTest {

    @Test
    void parseResolution_handlesAllPresets() {
        assertEquals(854, FfmpegCommandBuilder.parseResolution("480p").width());
        assertEquals(480, FfmpegCommandBuilder.parseResolution("480p").height());
        assertEquals(1280, FfmpegCommandBuilder.parseResolution("720p").width());
        assertEquals(1920, FfmpegCommandBuilder.parseResolution("1080p").width());
        // null / 非法走默认 720p
        assertEquals(1280, FfmpegCommandBuilder.parseResolution(null).width());
        assertEquals(1280, FfmpegCommandBuilder.parseResolution("XXX").width());
    }

    @Test
    void compose_singleShot_buildsMinimalCommand() {
        FfmpegCommandBuilder.ComposeRequest req = new FfmpegCommandBuilder.ComposeRequest(
                "ffmpeg", List.of("shot1.mp4"), null, null, "720p", "out.mp4", 0);
        List<String> cmd = FfmpegCommandBuilder.compose(req);
        // 期望: ffmpeg -y -hide_banner -loglevel error -i shot1.mp4 -map 0:v -map 0:a -s 1280x720 -c:v libx264 ... out.mp4
        assertEquals("ffmpeg", cmd.get(0));
        assertTrue(cmd.contains("-y"));
        assertTrue(cmd.contains("-hide_banner"));
        assertTrue(cmd.contains("shot1.mp4"));
        assertTrue(cmd.contains("out.mp4"));
        assertTrue(cmd.contains("-s"), "应包含分辨率参数");
        assertTrue(cmd.contains("1280x720"));
        assertTrue(cmd.contains("libx264"));
        assertTrue(cmd.contains("aac"));
        // 单镜头不应使用 filter_complex
        assertEquals(cmd.indexOf("-filter_complex"), -1, "单镜头不需要 filter_complex");
    }

    @Test
    void compose_multiShots_usesConcatFilter() {
        FfmpegCommandBuilder.ComposeRequest req = new FfmpegCommandBuilder.ComposeRequest(
                "ffmpeg",
                List.of("s1.mp4", "s2.mp4", "s3.mp4"),
                null, null, "1080p", "out.mp4", 0);
        List<String> cmd = FfmpegCommandBuilder.compose(req);
        // 期望 3 个 -i 输入
        int iCount = 0;
        for (int i = 0; i < cmd.size(); i++) if ("-i".equals(cmd.get(i))) iCount++;
        assertEquals(3, iCount, "3 镜头应有 3 个 -i");
        // filter_complex 应包含 concat=n=3
        int fcIdx = cmd.indexOf("-filter_complex");
        assertNotNull(fcIdx);
        assertTrue(cmd.get(fcIdx + 1).contains("concat=n=3"));
        // 1920x1080
        assertTrue(cmd.contains("1920x1080"));
    }

    @Test
    void compose_withAudio_addsAudioInputs() {
        FfmpegCommandBuilder.ComposeRequest req = new FfmpegCommandBuilder.ComposeRequest(
                "ffmpeg",
                List.of("s1.mp4", "s2.mp4"),
                List.of("a1.mp3", "a2.mp3"),
                null, "720p", "out.mp4", 0);
        List<String> cmd = FfmpegCommandBuilder.compose(req);
        // 4 个 -i (2 视频 + 2 音轨)
        int iCount = 0;
        for (int i = 0; i < cmd.size(); i++) if ("-i".equals(cmd.get(i))) iCount++;
        assertEquals(4, iCount, "2 镜头 + 2 音轨 = 4 个 -i");
    }

    @Test
    void compose_withSubtitle_burnsInViaFilter() {
        FfmpegCommandBuilder.ComposeRequest req = new FfmpegCommandBuilder.ComposeRequest(
                "ffmpeg", List.of("s1.mp4"), null, "subs.srt", "720p", "out.mp4", 0);
        List<String> cmd = FfmpegCommandBuilder.compose(req);
        // 字幕应作为输入 + 烧录到视频
        assertTrue(cmd.contains("subs.srt"), "字幕文件应作为 -i 输入");
        int vfIdx = cmd.indexOf("-vf");
        assertTrue(vfIdx > 0, "应使用 -vf 烧录字幕");
        String vf = cmd.get(vfIdx + 1);
        assertTrue(vf.startsWith("subtitles="), "滤镜应是 subtitles= 滤镜");
    }

    @Test
    void compose_blankAudioEntriesAreSkipped() {
        FfmpegCommandBuilder.ComposeRequest req = new FfmpegCommandBuilder.ComposeRequest(
                "ffmpeg",
                List.of("s1.mp4"),
                java.util.Arrays.asList("a1.mp3", "", null, "  "),
                null, "720p", "out.mp4", 0);
        List<String> cmd = FfmpegCommandBuilder.compose(req);
        int iCount = 0;
        for (int i = 0; i < cmd.size(); i++) if ("-i".equals(cmd.get(i))) iCount++;
        assertEquals(2, iCount, "1 视频 + 1 有效音轨 (其他空白被跳过) = 2 个 -i");
    }

    @Test
    void compose_transitionFieldAcceptsZeroToTen() {
        for (int t : new int[]{0, 1, 5, 10}) {
            FfmpegCommandBuilder.ComposeRequest req = new FfmpegCommandBuilder.ComposeRequest(
                    "ffmpeg", List.of("s1.mp4"), null, null, "720p", "out.mp4", t);
            assertNotNull(FfmpegCommandBuilder.compose(req), "transition=" + t + " 应合法");
        }
    }

    @Test
    void compose_transitionOutOfRange_throws() {
        assertThrows(IllegalArgumentException.class, () -> new FfmpegCommandBuilder.ComposeRequest(
                "ffmpeg", List.of("s1.mp4"), null, null, "720p", "out.mp4", -1));
        assertThrows(IllegalArgumentException.class, () -> new FfmpegCommandBuilder.ComposeRequest(
                "ffmpeg", List.of("s1.mp4"), null, null, "720p", "out.mp4", 11));
    }

    @Test
    void compose_emptyShots_throws() {
        assertThrows(IllegalArgumentException.class, () -> new FfmpegCommandBuilder.ComposeRequest(
                "ffmpeg", List.of(), null, null, "720p", "out.mp4", 0));
        assertThrows(IllegalArgumentException.class, () -> new FfmpegCommandBuilder.ComposeRequest(
                "ffmpeg", null, null, null, "720p", "out.mp4", 0));
    }

    @Test
    void compose_emptyOutput_throws() {
        assertThrows(IllegalArgumentException.class, () -> new FfmpegCommandBuilder.ComposeRequest(
                "ffmpeg", List.of("s1.mp4"), null, null, "720p", "", 0));
    }

    @Test
    void compose_nullFfmpegPath_defaultsToFfmpeg() {
        FfmpegCommandBuilder.ComposeRequest req = new FfmpegCommandBuilder.ComposeRequest(
                null, List.of("s1.mp4"), null, null, "720p", "out.mp4", 0);
        assertEquals("ffmpeg", req.ffmpegPath());
        List<String> cmd = FfmpegCommandBuilder.compose(req);
        assertEquals("ffmpeg", cmd.get(0));
    }

    @Test
    void probe_buildsValidFfprobeCommand() {
        List<String> cmd = FfmpegCommandBuilder.probe("ffprobe", "video.mp4");
        assertEquals("ffprobe", cmd.get(0));
        assertTrue(cmd.contains("-show_format"));
        assertTrue(cmd.contains("-show_streams"));
        assertTrue(cmd.contains("video.mp4"));
    }

    @Test
    void probe_nullFfprobePath_defaultsToFfprobe() {
        List<String> cmd = FfmpegCommandBuilder.probe(null, "video.mp4");
        assertEquals("ffprobe", cmd.get(0));
    }

    @Test
    void probe_blankMediaFile_throws() {
        assertThrows(IllegalArgumentException.class, () -> FfmpegCommandBuilder.probe("ffprobe", ""));
        assertThrows(IllegalArgumentException.class, () -> FfmpegCommandBuilder.probe("ffprobe", null));
    }
}
