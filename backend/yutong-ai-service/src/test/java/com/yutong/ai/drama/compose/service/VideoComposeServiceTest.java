package com.yutong.ai.drama.compose.service;

import com.yutong.ai.drama.compose.domain.DramaComposeJob;
import com.yutong.ai.drama.compose.dto.SubmitComposeRequest;
import com.yutong.ai.drama.compose.mapper.DramaComposeJobMapper;
import com.yutong.ai.drama.compose.service.ProcessRunner.ProcessResult;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 短剧真机合成服务单元测试 (不依赖真实 ffmpeg, ProcessRunner 全 stub)。
 * 设计来源: ADR 0004 P2-D 批次 5-C。
 *
 * 覆盖:
 * - 提交成功 → 异步 SUCCESS + 时长/大小回填 + 服务端分配输出路径
 * - ffmpeg 缺失 → 提交直接 503 失败关闭
 * - 越权路径 (../) / 绝对路径 / 不存在文件 / 非法字幕后缀 → 拒绝
 * - 超时 / 非 0 退出 / 超时长上限 → FAILED + errorMessage
 * - capabilities / getById 不存在 / 分页
 */
class VideoComposeServiceTest {

    @TempDir
    Path tempDir;

    private DramaComposeJobMapper mapper;
    private final Map<String, DramaComposeJob> store = new HashMap<>();
    private DramaComposeProperties props;
    private StubRunner runner;
    private VideoComposeService service;

    /** 可编程 stub: 按首命令分发结果 */
    static class StubRunner implements ProcessRunner {
        ProcessResult versionResult = new ProcessResult(0, "ffmpeg version test-stub", "", false);
        ProcessResult composeResult = new ProcessResult(0, "", "", false);
        ProcessResult probeResult = new ProcessResult(0,
                "{\"format\": {\"duration\": \"42.500\"}}", "", false);

        @Override
        public ProcessResult run(List<String> command, long timeoutSec) {
            String bin = command.isEmpty() ? "" : command.get(0);
            if (command.size() == 2 && command.get(1).equals("-version")) return versionResult;
            if (bin.contains("ffprobe")) return probeResult;
            // 合成命令: 模拟 ffmpeg 真实落盘 (末位参数为输出路径)
            try {
                Path out = Path.of(command.get(command.size() - 1));
                if (out.getParent() != null) Files.createDirectories(out.getParent());
                if (!Files.exists(out)) Files.writeString(out, "fake-mp4");
            } catch (Exception ignored) {
                // ignore
            }
            return composeResult;
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        mapper = mock(DramaComposeJobMapper.class);
        when(mapper.insert(any(DramaComposeJob.class))).thenAnswer(inv -> {
            DramaComposeJob j = inv.getArgument(0);
            store.put(j.getId(), j);
            return 1;
        });
        when(mapper.selectById(any(String.class))).thenAnswer(inv -> store.get(inv.getArgument(0)));
        when(mapper.updateById(any(DramaComposeJob.class))).thenAnswer(inv -> {
            DramaComposeJob j = inv.getArgument(0);
            store.put(j.getId(), j);
            return 1;
        });
        when(mapper.selectPage(any(), any())).thenAnswer(inv -> {
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<DramaComposeJob> p = inv.getArgument(0);
            p.setRecords(new java.util.ArrayList<>(store.values()));
            p.setTotal(store.size());
            return p;
        });

        props = new DramaComposeProperties();
        props.setFfmpegPath("ffmpeg-stub");
        props.setFfprobePath("ffprobe-stub");
        props.setWorkDir(tempDir.toString());
        props.setTimeoutSec(30);
        props.setMaxDurationSec(300);
        props.setMaxShots(20);

        runner = new StubRunner();
        service = new VideoComposeService(mapper, props, runner);
        store.clear();

        Files.createDirectories(tempDir.resolve("shots"));
        Files.writeString(tempDir.resolve("shots/a.mp4"), "fake-video");
        Files.writeString(tempDir.resolve("shots/b.mp4"), "fake-video");
        Files.createDirectories(tempDir.resolve("audio"));
        Files.writeString(tempDir.resolve("audio/b.mp3"), "fake-audio");
        Files.writeString(tempDir.resolve("subs.srt"), "1\n00:00:00,000 --> 00:00:01,000\n你好\n");
    }

    private SubmitComposeRequest baseReq() {
        SubmitComposeRequest req = new SubmitComposeRequest();
        req.setTitle("测试短剧");
        req.setShotVideos(List.of("shots/a.mp4", "shots/b.mp4"));
        req.setAudioTracks(List.of("audio/b.mp3"));
        req.setSubtitleFile("subs.srt");
        req.setResolution("720p");
        return req;
    }

    private DramaComposeJob awaitTerminal(String jobId) throws Exception {
        long deadline = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < deadline) {
            DramaComposeJob j = store.get(jobId);
            if (j != null && (DramaComposeJob.STATUS_SUCCESS.equals(j.getStatus())
                    || DramaComposeJob.STATUS_FAILED.equals(j.getStatus()))) {
                return j;
            }
            Thread.sleep(50);
        }
        throw new IllegalStateException("任务未在 8s 内结束: " + jobId);
    }

    @Test
    void submitSuccessAsync() throws Exception {
        DramaComposeJob job = service.submit(baseReq());
        assertNotNull(job.getId());
        assertEquals(DramaComposeJob.STATUS_PENDING, job.getStatus());
        // 输出路径服务端分配
        assertTrue(job.getOutputPath().endsWith(job.getId() + ".mp4"));

        DramaComposeJob done = awaitTerminal(job.getId());
        assertEquals(DramaComposeJob.STATUS_SUCCESS, done.getStatus());
        assertEquals(42.5, done.getDurationSec());
        assertNotNull(done.getOutputSize());
        assertNotNull(done.getStartedAt());
        assertNotNull(done.getFinishedAt());
    }

    @Test
    void ffmpegMissingFailClose() {
        runner.versionResult = new ProcessResult(1, "", "not found", false);
        BusinessException e = assertThrows(BusinessException.class, () -> service.submit(baseReq()));
        assertTrue(store.isEmpty(), "失败关闭: 不落 PENDING 假任务");
    }

    @Test
    void pathTraversalRejected() {
        SubmitComposeRequest req = baseReq();
        req.setShotVideos(List.of("../evil.mp4"));
        assertThrows(BusinessException.class, () -> service.submit(req));
    }

    @Test
    void absolutePathRejected() {
        SubmitComposeRequest req = baseReq();
        req.setShotVideos(List.of(tempDir.resolve("shots/a.mp4").toString()));
        assertThrows(BusinessException.class, () -> service.submit(req));
    }

    @Test
    void missingFileRejected() {
        SubmitComposeRequest req = baseReq();
        req.setShotVideos(List.of("shots/not-exists.mp4"));
        assertThrows(BusinessException.class, () -> service.submit(req));
    }

    @Test
    void badSubtitleSuffixRejected() throws Exception {
        Files.writeString(tempDir.resolve("subs.txt"), "plain");
        SubmitComposeRequest req = baseReq();
        req.setSubtitleFile("subs.txt");
        assertThrows(BusinessException.class, () -> service.submit(req));
    }

    @Test
    void timeoutMarkedFailed() throws Exception {
        runner.composeResult = new ProcessResult(-1, "", "killed", true);
        DramaComposeJob job = service.submit(baseReq());
        DramaComposeJob done = awaitTerminal(job.getId());
        assertEquals(DramaComposeJob.STATUS_FAILED, done.getStatus());
        assertTrue(done.getErrorMessage().contains("超时"));
    }

    @Test
    void nonZeroExitMarkedFailed() throws Exception {
        runner.composeResult = new ProcessResult(1, "", "Invalid data found", false);
        DramaComposeJob job = service.submit(baseReq());
        DramaComposeJob done = awaitTerminal(job.getId());
        assertEquals(DramaComposeJob.STATUS_FAILED, done.getStatus());
        assertTrue(done.getErrorMessage().contains("退出码 1"));
        assertTrue(done.getLogTail().contains("Invalid data"));
    }

    @Test
    void overDurationLimitMarkedFailed() throws Exception {
        props.setMaxDurationSec(10);
        DramaComposeJob job = service.submit(baseReq());
        DramaComposeJob done = awaitTerminal(job.getId());
        assertEquals(DramaComposeJob.STATUS_FAILED, done.getStatus());
        assertTrue(done.getErrorMessage().contains("超过上限"));
    }

    @Test
    void getByIdMissingThrows404() {
        assertThrows(ResourceNotFoundException.class, () -> service.getById("no-such-id"));
    }

    @Test
    void capabilitiesKeys() {
        Map<String, Object> caps = service.capabilities();
        assertEquals(true, caps.get("ffmpegAvailable"));
        assertEquals("ffmpeg-stub", caps.get("ffmpegPath"));
        assertEquals(tempDir.toString(), caps.get("workDir"));
    }

    @Test
    void pageListsJobs() {
        service.submit(baseReq());
        var page = service.page(1, 20, null);
        assertEquals(1, page.getTotal());
    }
}
