package com.yutong.ai.drama.compose.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.ai.drama.compose.domain.DramaComposeJob;
import com.yutong.ai.drama.compose.dto.SubmitComposeRequest;
import com.yutong.ai.drama.compose.mapper.DramaComposeJobMapper;
import com.yutong.ai.drama.service.video.FfmpegCommandBuilder;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.id.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 短剧真机合成服务。设计来源: platform-drama CompositionWorker + ADR 0004 P2-D 批次 5-C。
 *
 * <p>流程: submit 校验落库 (PENDING) → 异步执行 ffmpeg → 回填 SUCCESS/FAILED。
 * <ul>
 *   <li>失败关闭: 二进制缺失 / 超时 / 非 0 退出 / 超时长限制 / 输入缺失, 全部落 FAILED + errorMessage,
 *       绝不返回假 URL;</li>
 *   <li>路径安全: 客户端只给 workDir 下相对路径, 服务端 normalize 后必须位于 workDir 内,
 *       输出路径服务端分配 ({@code outputs/{jobId}.mp4});</li>
 *   <li>命令以 List 传参 (不走 shell), 无注入面;</li>
 *   <li>单测通过 {@link ProcessRunner} stub 覆盖, 不依赖真实 ffmpeg。</li>
 * </ul>
 */
@Service
public class VideoComposeService {

    private static final Logger log = LoggerFactory.getLogger(VideoComposeService.class);

    private static final Pattern DURATION_PATTERN =
            Pattern.compile("\"duration\"\\s*:\\s*\"([0-9]+(?:\\.[0-9]+)?)\"");

    /** log_tail 落库截断 */
    static final int LOG_TAIL_LIMIT = 4000;

    private final DramaComposeJobMapper mapper;
    private final DramaComposeProperties props;
    private final ProcessRunner processRunner;
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "drama-compose");
        t.setDaemon(true);
        return t;
    });

    public VideoComposeService(DramaComposeJobMapper mapper,
                               DramaComposeProperties props,
                               ProcessRunner processRunner) {
        this.mapper = mapper;
        this.props = props;
        this.processRunner = processRunner;
    }

    // ==================== 查询 ====================

    public DramaComposeJob getById(String id) {
        DramaComposeJob job = mapper.selectById(id);
        if (job == null) {
            throw new ResourceNotFoundException("合成任务不存在: " + id);
        }
        return job;
    }

    public Page<DramaComposeJob> page(int pageNo, int pageSize, String status) {
        QueryWrapper<DramaComposeJob> w = new QueryWrapper<>();
        if (status != null && !status.isBlank()) w.eq("status", status);
        w.orderByDesc("created_time");
        return mapper.selectPage(Page.of(pageNo, pageSize), w);
    }

    /**
     * 运行能力探测: ffmpeg/ffprobe 是否可用 + 版本首行。
     * 前端合成页据此展示横幅; 不可用时 submit 直接 503。
     */
    public Map<String, Object> capabilities() {
        return Map.of(
                "ffmpegPath", props.getFfmpegPath(),
                "ffmpegAvailable", probeBinary(props.getFfmpegPath()),
                "ffmpegVersion", probeVersion(props.getFfmpegPath()),
                "ffprobeAvailable", probeBinary(props.getFfprobePath()),
                "workDir", props.getWorkDir(),
                "timeoutSec", props.getTimeoutSec(),
                "maxDurationSec", props.getMaxDurationSec(),
                "maxShots", props.getMaxShots());
    }

    // ==================== 提交 ====================

    /**
     * 提交合成任务 (同步校验 + 落库, 异步执行)。
     * ffmpeg 缺失时直接 503 失败关闭, 不落 PENDING 假任务。
     */
    public DramaComposeJob submit(SubmitComposeRequest req) {
        if (!probeBinary(props.getFfmpegPath())) {
            throw new BusinessException(ErrorCode.SYS_SERVICE_UNAVAILABLE,
                    "ffmpeg 不可用 (" + props.getFfmpegPath() + "), 合成服务未就绪");
        }
        Path workDir = workDir();
        List<String> shots = requireMediaFiles(workDir, req.getShotVideos(), "shotVideos",
                1, props.getMaxShots());
        List<String> audios = req.getAudioTracks() == null || req.getAudioTracks().isEmpty()
                ? List.of()
                : requireMediaFiles(workDir, req.getAudioTracks(), "audioTracks", 1, props.getMaxShots());
        String subtitle = null;
        if (req.getSubtitleFile() != null && !req.getSubtitleFile().isBlank()) {
            subtitle = requireMediaFiles(workDir, List.of(req.getSubtitleFile()), "subtitleFile", 1, 1).get(0);
            String lower = subtitle.toLowerCase();
            if (!lower.endsWith(".srt") && !lower.endsWith(".ass")) {
                throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "字幕仅支持 .srt/.ass");
            }
        }

        DramaComposeJob job = new DramaComposeJob();
        job.setId(IdGenerator.nextId());
        job.setTitle(req.getTitle().trim());
        job.setShotVideosJson(encodeJsonArray(req.getShotVideos()));
        job.setAudioTracksJson(encodeJsonArray(req.getAudioTracks() == null ? List.of() : req.getAudioTracks()));
        job.setSubtitleFile(req.getSubtitleFile());
        job.setResolution(req.getResolution() == null ? "720p" : req.getResolution());
        job.setTransitionSec(0);
        job.setStatus(DramaComposeJob.STATUS_PENDING);
        // 输出路径服务端分配
        Path outDir = workDir.resolve("outputs");
        try {
            Files.createDirectories(outDir);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYS_INTERNAL_ERROR, "合成输出目录创建失败: " + e.getMessage());
        }
        job.setOutputPath(outDir.resolve(job.getId() + ".mp4").toString());
        mapper.insert(job);

        String jobId = job.getId();
        List<String> absShots = toAbsolute(workDir, req.getShotVideos());
        List<String> absAudios = toAbsolute(workDir, req.getAudioTracks() == null ? List.of() : req.getAudioTracks());
        String absSubtitle = subtitle == null ? null : workDir.resolve(subtitle).toString();
        executor.execute(() -> execute(jobId, absShots, absAudios, absSubtitle, job.getResolution(),
                job.getOutputPath()));
        return job;
    }

    // ==================== 异步执行 ====================

    private void execute(String jobId, List<String> shots, List<String> audios,
                         String subtitle, String resolution, String outputPath) {
        markRunning(jobId);
        FfmpegCommandBuilder.ComposeRequest composeReq = new FfmpegCommandBuilder.ComposeRequest(
                props.getFfmpegPath(), shots, audios, subtitle, resolution, outputPath, 0);
        List<String> cmd = FfmpegCommandBuilder.compose(composeReq);
        ProcessRunner.ProcessResult result;
        try {
            result = processRunner.run(cmd, props.getTimeoutSec());
        } catch (DefaultProcessRunner.ProcessStartException e) {
            markFailed(jobId, null, "ffmpeg 启动失败: " + e.getMessage(), "");
            return;
        } catch (Exception e) {
            markFailed(jobId, null, "合成执行异常: " + e.getMessage(), "");
            return;
        }
        if (result.timedOut()) {
            markFailed(jobId, result.exitCode(), "合成超时 (" + props.getTimeoutSec() + "s 已杀进程)",
                    result.stderrTail());
            return;
        }
        if (result.exitCode() != 0) {
            markFailed(jobId, result.exitCode(),
                    "ffmpeg 退出码 " + result.exitCode(), result.stderrTail());
            return;
        }
        // 成功: ffprobe 回填时长 + 文件大小; 超时长限制则转 FAILED
        Double duration = probeDuration(outputPath);
        Long size = fileSize(outputPath);
        if (duration != null && duration > props.getMaxDurationSec()) {
            markFailed(jobId, result.exitCode(),
                    "成片时长 " + duration + "s 超过上限 " + props.getMaxDurationSec() + "s",
                    result.stderrTail());
            return;
        }
        markSuccess(jobId, result.exitCode(), duration, size, result.stderrTail());
    }

    // ==================== 状态流转 ====================

    private void markRunning(String jobId) {
        DramaComposeJob job = getById(jobId);
        job.setStatus(DramaComposeJob.STATUS_RUNNING);
        job.setStartedAt(LocalDateTime.now());
        mapper.updateById(job);
    }

    private void markSuccess(String jobId, int exitCode, Double duration, Long size, String logTail) {
        DramaComposeJob job = getById(jobId);
        job.setStatus(DramaComposeJob.STATUS_SUCCESS);
        job.setExitCode(exitCode);
        job.setDurationSec(duration);
        job.setOutputSize(size);
        job.setLogTail(tail(logTail));
        job.setFinishedAt(LocalDateTime.now());
        mapper.updateById(job);
        log.info("drama compose success jobId={} duration={}s size={}", jobId, duration, size);
    }

    private void markFailed(String jobId, Integer exitCode, String error, String logTail) {
        DramaComposeJob job = getById(jobId);
        job.setStatus(DramaComposeJob.STATUS_FAILED);
        job.setExitCode(exitCode);
        job.setErrorMessage(error == null ? "未知错误" : error.substring(0, Math.min(error.length(), 500)));
        job.setLogTail(tail(logTail));
        job.setFinishedAt(LocalDateTime.now());
        mapper.updateById(job);
        log.warn("drama compose failed jobId={} error={}", jobId, error);
    }

    // ==================== 路径安全 ====================

    private Path workDir() {
        Path dir = Paths.get(props.getWorkDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYS_INTERNAL_ERROR, "合成工作目录不可用: " + e.getMessage());
        }
        return dir;
    }

    /**
     * 校验媒体文件: 相对路径 + 归一化后必须位于 workDir 内 + 文件必须存在。
     * 返回归一化后的相对路径 (入库备查)。
     */
    List<String> requireMediaFiles(Path workDir, List<String> inputs, String field, int min, int max) {
        if (inputs == null || inputs.size() < min || inputs.size() > max) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    field + " 数量必须在 " + min + "-" + max + " 之间");
        }
        List<String> safe = new ArrayList<>();
        for (String raw : inputs) {
            if (raw == null || raw.isBlank()) {
                throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, field + " 含空路径");
            }
            Path p = Paths.get(raw.trim());
            if (p.isAbsolute()) {
                throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, field + " 仅接受 workDir 下相对路径: " + raw);
            }
            Path resolved = workDir.resolve(p).normalize();
            if (!resolved.startsWith(workDir)) {
                throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, field + " 越权路径已拒绝: " + raw);
            }
            if (!Files.isRegularFile(resolved)) {
                throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, field + " 文件不存在: " + raw);
            }
            safe.add(workDir.relativize(resolved).toString().replace("\\", "/"));
        }
        return safe;
    }

    private List<String> toAbsolute(Path workDir, List<String> rels) {
        List<String> out = new ArrayList<>();
        for (String r : rels) out.add(workDir.resolve(r).toString());
        return out;
    }

    // ==================== 二进制探测 ====================

    private boolean probeBinary(String binary) {
        try {
            ProcessRunner.ProcessResult r = processRunner.run(List.of(binary, "-version"), 15);
            return !r.timedOut() && r.exitCode() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String probeVersion(String binary) {
        try {
            ProcessRunner.ProcessResult r = processRunner.run(List.of(binary, "-version"), 15);
            if (r.timedOut() || r.exitCode() != 0) return "";
            String out = r.stdoutTail();
            int nl = out.indexOf('\n');
            return (nl < 0 ? out : out.substring(0, nl)).trim();
        } catch (Exception e) {
            return "";
        }
    }

    private Double probeDuration(String outputPath) {
        try {
            List<String> cmd = FfmpegCommandBuilder.probe(props.getFfprobePath(), outputPath);
            ProcessRunner.ProcessResult r = processRunner.run(cmd, 60);
            if (r.timedOut() || r.exitCode() != 0) return null;
            Matcher m = DURATION_PATTERN.matcher(r.stdoutTail());
            if (m.find()) return Double.parseDouble(m.group(1));
            return null;
        } catch (Exception e) {
            log.debug("ffprobe duration failed output={}", outputPath, e);
            return null;
        }
    }

    private Long fileSize(String outputPath) {
        try {
            return Files.size(Paths.get(outputPath));
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 小工具 ====================

    private String tail(String s) {
        if (s == null) return null;
        if (s.length() <= LOG_TAIL_LIMIT) return s;
        return s.substring(s.length() - LOG_TAIL_LIMIT);
    }

    /** 极简字符串数组 JSON 编码 (仅内部落库, 避免引入新依赖) */
    static String encodeJsonArray(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(list.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
        }
        return sb.append("]").toString();
    }
}
