package com.yutong.ai.drama.storyboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.ai.drama.compose.domain.DramaComposeJob;
import com.yutong.ai.drama.compose.dto.SubmitComposeRequest;
import com.yutong.ai.drama.compose.service.VideoComposeService;
import com.yutong.ai.drama.storyboard.domain.AiDramaProject;
import com.yutong.ai.drama.storyboard.domain.AiDramaStoryboard;
import com.yutong.ai.drama.storyboard.dto.BatchVideoResult;
import com.yutong.ai.drama.storyboard.dto.CreateProjectRequest;
import com.yutong.ai.drama.storyboard.dto.ProjectDetailVO;
import com.yutong.ai.drama.storyboard.dto.StoryboardItemRequest;
import com.yutong.ai.drama.storyboard.dto.StoryboardSaveRequest;
import com.yutong.ai.drama.storyboard.dto.VideoGenerateRequest;
import com.yutong.ai.drama.storyboard.gateway.VideoGateway;
import com.yutong.ai.drama.storyboard.mapper.AiDramaProjectMapper;
import com.yutong.ai.drama.storyboard.mapper.AiDramaStoryboardMapper;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.id.IdGenerator;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * 分镜视频生成编排服务。设计来源: docs/compose/spec/ai-depth-parity.md S2.2
 *
 * <p>状态机 (storyboard.video_status): PENDING → GENERATING → SUCCEEDED | FAILED。
 * 乐观锁 version 防并发双写。
 *
 * <p>批量语义: 按 location_name 分组; 同组 scene_no 升序串行, 上一镜 last_frame_url
 * 作下一镜首帧承接; 跨组线程池并发 ≤4; 每镜超时 fail-closed (默认 5min)。
 *
 * <p>合成: 仅当全部分镜 SUCCEEDED 时允许 compose, 复用 {@link VideoComposeService}。
 * 视频成功后项目 compose_status 置 PENDING (需重算)。
 */
@Service
public class StoryboardVideoService {

    private static final Logger log = LoggerFactory.getLogger(StoryboardVideoService.class);

    /** 跨组最大并发 */
    static final int MAX_GROUP_CONCURRENCY = 4;

    /** 默认单镜超时 (毫秒), 失败关闭。生产 5min, 测试可下调 */
    static final long DEFAULT_SHOT_TIMEOUT_MILLIS = 5L * 60L * 1000L;

    /** location 为空时的分组键 */
    static final String DEFAULT_LOCATION_KEY = "__default__";

    private final AiDramaProjectMapper projectMapper;
    private final AiDramaStoryboardMapper storyboardMapper;
    private final VideoGateway videoGateway;
    private final VideoComposeService videoComposeService;

    private final ExecutorService groupExecutor;
    private final ExecutorService shotExecutor;

    /** 单镜超时毫秒, 测试可下调 */
    private volatile long shotTimeoutMillis = DEFAULT_SHOT_TIMEOUT_MILLIS;

    public StoryboardVideoService(AiDramaProjectMapper projectMapper,
                                  AiDramaStoryboardMapper storyboardMapper,
                                  VideoGateway videoGateway,
                                  VideoComposeService videoComposeService) {
        this.projectMapper = projectMapper;
        this.storyboardMapper = storyboardMapper;
        this.videoGateway = videoGateway;
        this.videoComposeService = videoComposeService;
        this.groupExecutor = Executors.newFixedThreadPool(MAX_GROUP_CONCURRENCY, r -> {
            Thread t = new Thread(r, "storyboard-group");
            t.setDaemon(true);
            return t;
        });
        this.shotExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "storyboard-shot");
            t.setDaemon(true);
            return t;
        });
    }

    /** 仅供测试下调超时 (毫秒) */
    void setShotTimeoutMillis(long millis) {
        if (millis <= 0) {
            throw new IllegalArgumentException("shotTimeoutMillis must be > 0");
        }
        this.shotTimeoutMillis = millis;
    }

    // ==================== 项目 / 分镜落库 ====================

    @Transactional
    public ProjectDetailVO createProject(CreateProjectRequest request) {
        AiDramaProject project = new AiDramaProject();
        project.setId(IdGenerator.nextId());
        project.setTitle(request.getTitle().trim());
        project.setSynopsis(request.getSynopsis());
        project.setArtStyle(request.getArtStyle());
        project.setStyleRef(request.getStyleRef());
        project.setAspectRatio(request.getAspectRatioOrDefault());
        project.setComposeStatus(AiDramaProject.COMPOSE_NONE);
        project.setMetaJson(request.getMetaJson());
        String tenantId = requireTenantId();
        project.setTenantId(tenantId);
        projectMapper.insert(project);

        List<AiDramaStoryboard> boards = List.of();
        if (request.getStoryboards() != null && !request.getStoryboards().isEmpty()) {
            boards = saveStoryboards(project.getId(), request.getStoryboards());
        }
        return ProjectDetailVO.of(project, boards);
    }

    /**
     * 批量插入分镜 (追加到既有项目)。
     */
    @Transactional
    public List<AiDramaStoryboard> saveStoryboards(String projectId, List<StoryboardItemRequest> items) {
        AiDramaProject project = requireProject(projectId);
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "storyboards 不能为空");
        }
        List<AiDramaStoryboard> saved = new ArrayList<>(items.size());
        for (StoryboardItemRequest item : items) {
            AiDramaStoryboard board = new AiDramaStoryboard();
            board.setId(IdGenerator.nextId());
            board.setProjectId(project.getId());
            board.setSceneNo(item.getSceneNo());
            board.setShotType(item.getShotType());
            board.setLocationName(item.getLocationName());
            board.setImagePrompt(item.getImagePrompt());
            board.setVideoPrompt(item.getVideoPrompt());
            board.setDurationSeconds(item.getDurationSeconds() != null
                    ? item.getDurationSeconds() : new BigDecimal("5"));
            board.setReferenceImages(encodeJsonArray(item.getReferenceImages()));
            board.setVideoStatus(AiDramaStoryboard.STATUS_PENDING);
            board.setTenantId(project.getTenantId() != null
                    ? project.getTenantId() : requireTenantId());
            board.setCreatedBy(com.yutong.common.auth.CurrentUserContext.getUserId());
            storyboardMapper.insert(board);
            saved.add(board);
        }
        // 新分镜待生成 → 成片需重算
        if (!AiDramaProject.COMPOSE_NONE.equals(project.getComposeStatus())) {
            project.setComposeStatus(AiDramaProject.COMPOSE_PENDING);
            projectMapper.updateById(project);
        }
        return saved;
    }

    public List<StoryboardItemRequest> unwrapSave(StoryboardSaveRequest request) {
        return request.getStoryboards();
    }

    // ==================== 查询 ====================

    public PageResult<AiDramaProject> pageProjects(PageRequest request) {
        String tenantId = requireTenantId();
        LambdaQueryWrapper<AiDramaProject> wrapper = new LambdaQueryWrapper<AiDramaProject>()
                .eq(AiDramaProject::getTenantId, tenantId)
                .orderByDesc(AiDramaProject::getCreatedTime);
        Page<AiDramaProject> page = projectMapper.selectPage(
                new Page<>(request.page(), request.size()), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), request.page(), request.size());
    }

    public ProjectDetailVO getProjectDetail(String projectId) {
        AiDramaProject project = requireProject(projectId);
        return ProjectDetailVO.of(project, listStoryboards(projectId));
    }

    public AiDramaStoryboard getStoryboardVideo(String storyboardId) {
        return requireStoryboard(storyboardId);
    }

    // ==================== 单镜生成 ====================

    /**
     * 单镜生成: PENDING|FAILED → GENERATING → SUCCEEDED|FAILED。
     * 失败关闭: 异常落 FAILED + errorMessage 后上抛, 不返回假 URL。
     */
    public AiDramaStoryboard generateStoryboardVideo(String storyboardId) {
        return generateStoryboardVideo(storyboardId, null, null);
    }

    public AiDramaStoryboard generateStoryboardVideo(String storyboardId,
                                                     String previousLastFrameUrl,
                                                     VideoGenerateRequest overrides) {
        AiDramaStoryboard board = requireStoryboard(storyboardId);
        AiDramaProject project = requireProject(board.getProjectId());

        String status = board.getVideoStatus();
        if (!AiDramaStoryboard.STATUS_PENDING.equals(status)
                && !AiDramaStoryboard.STATUS_FAILED.equals(status)) {
            throw new BusinessException(ErrorCode.SYS_BUSINESS_CONFLICT,
                    "分镜当前状态不允许发起生成: " + status);
        }

        // PENDING|FAILED → GENERATING (乐观锁)
        board.setVideoStatus(AiDramaStoryboard.STATUS_GENERATING);
        board.setErrorMessage(null);
        int updated = storyboardMapper.updateById(board);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.SYS_OPTIMISTIC_LOCK,
                    "分镜状态并发冲突, 请刷新后重试: " + storyboardId);
        }

        try {
            String prompt = (overrides != null && overrides.getVideoPrompt() != null
                    && !overrides.getVideoPrompt().isBlank())
                    ? overrides.getVideoPrompt()
                    : board.getVideoPrompt();
            VideoGateway.Command command = new VideoGateway.Command(
                    prompt,
                    board.getDurationSeconds(),
                    project.getAspectRatio(),
                    decodeJsonArray(board.getReferenceImages()),
                    previousLastFrameUrl,
                    project.getStyleRef(),
                    overrides == null ? null : overrides.getProviderCode(),
                    overrides == null ? null : overrides.getModelCode()
            );
            VideoGateway.Result result = callGatewayWithTimeout(command);
            applySuccess(board, result);
            markProjectComposePending(project);
            log.info("storyboard video succeeded id={} mediaJobId={}",
                    board.getId(), result.mediaJobId());
            return board;
        } catch (Exception e) {
            applyFailure(board, e);
            if (e instanceof BusinessException be) {
                throw be;
            }
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR,
                    "分镜视频生成失败: " + e.getMessage());
        }
    }

    // ==================== 批量生成 ====================

    /**
     * 按项目批量生成: location 分组, 组内串行 + lastFrame 承接, 组间并发 ≤4。
     */
    public BatchVideoResult generateAllVideos(String projectId) {
        AiDramaProject project = requireProject(projectId);
        List<AiDramaStoryboard> boards = listStoryboards(projectId);
        if (boards.isEmpty()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    "项目无分镜, 请先保存 storyboard: " + projectId);
        }

        Map<String, List<AiDramaStoryboard>> groups = groupByLocation(boards);
        String tenantId = CurrentUserContext.getTenantId();
        String userId = CurrentUserContext.getUserId();
        String username = CurrentUserContext.getUsername();

        List<CompletableFuture<Void>> futures = new ArrayList<>(groups.size());
        for (Map.Entry<String, List<AiDramaStoryboard>> entry : groups.entrySet()) {
            List<AiDramaStoryboard> group = entry.getValue();
            futures.add(CompletableFuture.runAsync(() -> {
                CurrentUserContext.set(userId, tenantId, username);
                try {
                    processGroupSerially(project, group);
                } finally {
                    CurrentUserContext.clear();
                }
            }, groupExecutor));
        }

        // 等待全部分组结束 (每镜内部已有 fail-closed 超时)
        for (CompletableFuture<Void> f : futures) {
            try {
                f.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException(ErrorCode.AI_SERVICE_DEGRADED, "批量生成被中断");
            } catch (ExecutionException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                log.error("storyboard group failed project={}", projectId, cause);
            }
        }

        return BatchVideoResult.of(projectId, listStoryboards(projectId));
    }

    /**
     * 按 location_name 分组, 组内 scene_no 升序。
     * 包级可见, 供单测断言分组串行逻辑。
     */
    Map<String, List<AiDramaStoryboard>> groupByLocation(List<AiDramaStoryboard> boards) {
        return boards.stream()
                .collect(Collectors.groupingBy(
                        b -> {
                            String loc = b.getLocationName();
                            return (loc == null || loc.isBlank()) ? DEFAULT_LOCATION_KEY : loc.trim();
                        },
                        LinkedHashMap::new,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> list.stream()
                                        .sorted(Comparator.comparing(
                                                AiDramaStoryboard::getSceneNo,
                                                Comparator.nullsLast(Integer::compareTo)))
                                        .collect(Collectors.toList())
                        )
                ));
    }

    /**
     * 组内串行: 上一镜 last_frame_url 作下一镜首帧; 失败则清空承接并继续后续镜。
     */
    void processGroupSerially(AiDramaProject project, List<AiDramaStoryboard> group) {
        String lastFrameUrl = null;
        for (AiDramaStoryboard board : group) {
            // 已成功则跳过 (幂等重跑)；仅 last_frame_url 可作承接，禁止用 videoUrl
            if (AiDramaStoryboard.STATUS_SUCCEEDED.equals(board.getVideoStatus())
                    && board.getVideoUrl() != null && !board.getVideoUrl().isBlank()) {
                lastFrameUrl = board.getLastFrameUrl();
                continue;
            }
            if (AiDramaStoryboard.STATUS_GENERATING.equals(board.getVideoStatus())) {
                // 他线程占用中, 跳过避免双写
                continue;
            }
            try {
                AiDramaStoryboard done = generateStoryboardVideo(board.getId(), lastFrameUrl, null);
                lastFrameUrl = done.getLastFrameUrl();
            } catch (Exception e) {
                log.warn("storyboard shot failed in group, continue without lastFrame id={} err={}",
                        board.getId(), e.getMessage());
                lastFrameUrl = null;
            }
        }
    }

    // ==================== 合成 ====================

    /**
     * 收集 SUCCEEDED 分镜 video_url, 按 scene_no 排序后调用 {@link VideoComposeService}。
     * 任一镜未成功则拒绝 (失败关闭)。
     *
     * <p>故意不加 {@code @Transactional}: submit 失败后项目 compose_status=FAILED
     * 必须独立落库, 不能随异常回滚。
     */
    public DramaComposeJob composeFromProject(String projectId) {
        AiDramaProject project = requireProject(projectId);
        if (AiDramaProject.COMPOSE_RUNNING.equals(project.getComposeStatus())) {
            throw new BusinessException(ErrorCode.SYS_BUSINESS_CONFLICT,
                    "项目正在合成中，请勿重复提交: " + projectId);
        }
        List<AiDramaStoryboard> boards = listStoryboards(projectId);
        if (boards.isEmpty()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    "项目无分镜, 无法合成: " + projectId);
        }
        List<String> notReady = boards.stream()
                .filter(b -> !AiDramaStoryboard.STATUS_SUCCEEDED.equals(b.getVideoStatus())
                        || b.getVideoUrl() == null || b.getVideoUrl().isBlank())
                .map(b -> "scene#" + b.getSceneNo() + ":" + b.getVideoStatus())
                .toList();
        if (!notReady.isEmpty()) {
            throw new BusinessException(ErrorCode.SYS_BUSINESS_CONFLICT,
                    "存在未成功分镜, 拒绝合成: " + String.join(", ", notReady));
        }

        List<String> shotVideos = boards.stream()
                .sorted(Comparator.comparing(
                        AiDramaStoryboard::getSceneNo,
                        Comparator.nullsLast(Integer::compareTo)))
                .map(AiDramaStoryboard::getVideoUrl)
                .toList();

        SubmitComposeRequest req = new SubmitComposeRequest();
        req.setTitle(project.getTitle());
        req.setShotVideos(shotVideos);
        req.setResolution("720p");

        project.setComposeStatus(AiDramaProject.COMPOSE_RUNNING);
        projectMapper.updateById(project);

        try {
            DramaComposeJob job = videoComposeService.submit(req);
            project.setComposeJobId(job.getId());
            project.setComposedPath(job.getOutputPath());
            projectMapper.updateById(project);
            return job;
        } catch (BusinessException e) {
            project.setComposeStatus(AiDramaProject.COMPOSE_FAILED);
            projectMapper.updateById(project);
            throw e;
        } catch (Exception e) {
            project.setComposeStatus(AiDramaProject.COMPOSE_FAILED);
            projectMapper.updateById(project);
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR,
                    "成片合成提交失败: " + e.getMessage());
        }
    }

    // ==================== 内部 ====================

    private VideoGateway.Result callGatewayWithTimeout(VideoGateway.Command command) {
        CompletableFuture<VideoGateway.Result> future =
                CompletableFuture.supplyAsync(() -> videoGateway.generate(command), shotExecutor);
        try {
            return future.get(shotTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new BusinessException(ErrorCode.SYS_SERVICE_UNAVAILABLE,
                    "分镜视频生成超时 (" + shotTimeoutMillis + "ms), 失败关闭");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new BusinessException(ErrorCode.AI_SERVICE_DEGRADED, "分镜视频生成被中断");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof BusinessException be) {
                throw be;
            }
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR,
                    "分镜视频生成失败: " + cause.getMessage());
        }
    }

    private void applySuccess(AiDramaStoryboard board, VideoGateway.Result result) {
        board.setVideoStatus(AiDramaStoryboard.STATUS_SUCCEEDED);
        board.setVideoUrl(result.videoUrl());
        board.setLastFrameUrl(result.lastFrameUrl());
        board.setVideoId(result.videoId());
        board.setMediaJobId(result.mediaJobId());
        board.setErrorMessage(null);
        int updated = storyboardMapper.updateById(board);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.SYS_OPTIMISTIC_LOCK,
                    "分镜成功状态落库冲突(乐观锁): " + board.getId());
        }
    }

    private void applyFailure(AiDramaStoryboard board, Exception e) {
        board.setVideoStatus(AiDramaStoryboard.STATUS_FAILED);
        String msg = e.getMessage() == null ? "unknown" : e.getMessage();
        board.setErrorMessage(msg.length() > 500 ? msg.substring(0, 500) : msg);
        try {
            int updated = storyboardMapper.updateById(board);
            if (updated == 0) {
                log.error("storyboard FAILED status optimistic-lock miss id={} — 强制按 id 重写", board.getId());
                storyboardMapper.updateById(board);
            }
        } catch (Exception ex) {
            log.error("failed to mark storyboard FAILED id={}", board.getId(), ex);
        }
    }

    private void markProjectComposePending(AiDramaProject project) {
        if (project == null || AiDramaProject.COMPOSE_NONE.equals(project.getComposeStatus())) {
            return;
        }
        if (!AiDramaProject.COMPOSE_PENDING.equals(project.getComposeStatus())) {
            project.setComposeStatus(AiDramaProject.COMPOSE_PENDING);
            projectMapper.updateById(project);
        }
    }

    private AiDramaProject requireProject(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "projectId 不能为空");
        }
        AiDramaProject project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new ResourceNotFoundException("短剧项目不存在: " + projectId);
        }
        String tenantId = requireTenantId();
        if (!Objects.equals(tenantId, project.getTenantId())) {
            throw new ResourceNotFoundException("短剧项目不存在: " + projectId);
        }
        return project;
    }

    private AiDramaStoryboard requireStoryboard(String storyboardId) {
        if (storyboardId == null || storyboardId.isBlank()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "storyboardId 不能为空");
        }
        AiDramaStoryboard board = storyboardMapper.selectById(storyboardId);
        if (board == null) {
            throw new ResourceNotFoundException("分镜不存在: " + storyboardId);
        }
        String tenantId = requireTenantId();
        if (!Objects.equals(tenantId, board.getTenantId())) {
            throw new ResourceNotFoundException("分镜不存在: " + storyboardId);
        }
        return board;
    }

    private String requireTenantId() {
        String tenantId = CurrentUserContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new BusinessException(ErrorCode.SYS_UNAUTHORIZED, "缺失租户上下文");
        }
        return tenantId;
    }

    private List<AiDramaStoryboard> listStoryboards(String projectId) {
        String tenantId = requireTenantId();
        return storyboardMapper.selectList(new LambdaQueryWrapper<AiDramaStoryboard>()
                .eq(AiDramaStoryboard::getProjectId, projectId)
                .eq(AiDramaStoryboard::getTenantId, tenantId)
                .orderByAsc(AiDramaStoryboard::getSceneNo));
    }

    /** 极简 JSON 数组编码 (与 VideoComposeService 同风格, 避免额外依赖) */
    static String encodeJsonArray(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("\"")
                    .append(list.get(i).replace("\\", "\\\\").replace("\"", "\\\""))
                    .append("\"");
        }
        return sb.append("]").toString();
    }

    /** JSON 字符串数组解码 — 使用 Jackson，避免 URL 含逗号被截断。 */
    static List<String> decodeJsonArray(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) {
            return List.of();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String[] arr = mapper.readValue(json.trim(), String[].class);
            if (arr == null || arr.length == 0) {
                return List.of();
            }
            List<String> out = new ArrayList<>(arr.length);
            for (String v : arr) {
                if (v != null && !v.isBlank()) {
                    out.add(v);
                }
            }
            return out;
        } catch (Exception e) {
            log.warn("decodeJsonArray failed, fallback empty: {}", e.getMessage());
            return List.of();
        }
    }
}
