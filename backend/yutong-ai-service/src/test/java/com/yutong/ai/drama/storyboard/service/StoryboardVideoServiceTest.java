package com.yutong.ai.drama.storyboard.service;

import com.yutong.ai.drama.compose.domain.DramaComposeJob;
import com.yutong.ai.drama.compose.dto.SubmitComposeRequest;
import com.yutong.ai.drama.compose.service.VideoComposeService;
import com.yutong.ai.drama.storyboard.domain.AiDramaProject;
import com.yutong.ai.drama.storyboard.domain.AiDramaStoryboard;
import com.yutong.ai.drama.storyboard.dto.BatchVideoResult;
import com.yutong.ai.drama.storyboard.dto.CreateProjectRequest;
import com.yutong.ai.drama.storyboard.dto.ProjectDetailVO;
import com.yutong.ai.drama.storyboard.dto.StoryboardItemRequest;
import com.yutong.ai.drama.storyboard.gateway.VideoGateway;
import com.yutong.ai.drama.storyboard.mapper.AiDramaProjectMapper;
import com.yutong.ai.drama.storyboard.mapper.AiDramaStoryboardMapper;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 分镜视频生成服务单元测试。设计来源: ai-depth-parity.md S2.2 T4。
 *
 * <p>覆盖:
 * <ul>
 *   <li>状态机: PENDING→GENERATING→SUCCEEDED / FAILED; 非法状态拒绝; 乐观锁冲突</li>
 *   <li>分组串行: 同 location 按 scene_no 串行且 lastFrameUrl 承接; 失败清空承接</li>
 *   <li>compose 拒绝: 任一镜未成功则拒绝; 全成功时按 scene_no 排序提交</li>
 *   <li>超时 fail-closed / 无 Key 失败关闭 (VideoGateway mock 抛异常)</li>
 * </ul>
 */
class StoryboardVideoServiceTest {

    private static final String TENANT = "tenant-test";

    private AiDramaProjectMapper projectMapper;
    private AiDramaStoryboardMapper storyboardMapper;
    private VideoGateway videoGateway;
    private VideoComposeService composeService;
    private StoryboardVideoService service;

    private final Map<String, AiDramaProject> projectStore = new ConcurrentHashMap<>();
    private final Map<String, AiDramaStoryboard> boardStore = new ConcurrentHashMap<>();
    private final List<VideoGateway.Command> gatewayCalls = new CopyOnWriteArrayList<>();
    private final List<SubmitComposeRequest> composeCalls = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        CurrentUserContext.set("user-1", TENANT, "tester");
        projectStore.clear();
        boardStore.clear();
        gatewayCalls.clear();
        composeCalls.clear();

        projectMapper = mock(AiDramaProjectMapper.class);
        storyboardMapper = mock(AiDramaStoryboardMapper.class);
        videoGateway = mock(VideoGateway.class);
        composeService = mock(VideoComposeService.class);

        when(projectMapper.insert(any(AiDramaProject.class))).thenAnswer(inv -> {
            AiDramaProject p = inv.getArgument(0);
            if (p.getTenantId() == null) {
                p.setTenantId(TENANT);
            }
            if (p.getVersion() == null) {
                p.setVersion(0);
            }
            projectStore.put(p.getId(), p);
            return 1;
        });
        when(projectMapper.selectById(any(String.class)))
                .thenAnswer(inv -> projectStore.get(inv.getArgument(0)));
        when(projectMapper.updateById(any(AiDramaProject.class))).thenAnswer(inv -> {
            AiDramaProject p = inv.getArgument(0);
            projectStore.put(p.getId(), p);
            return 1;
        });

        when(storyboardMapper.insert(any(AiDramaStoryboard.class))).thenAnswer(inv -> {
            AiDramaStoryboard b = inv.getArgument(0);
            if (b.getTenantId() == null) {
                b.setTenantId(TENANT);
            }
            if (b.getVersion() == null) {
                b.setVersion(0);
            }
            boardStore.put(b.getId(), b);
            return 1;
        });
        when(storyboardMapper.selectById(any(String.class)))
                .thenAnswer(inv -> boardStore.get(inv.getArgument(0)));
        when(storyboardMapper.updateById(any(AiDramaStoryboard.class))).thenAnswer(inv -> {
            AiDramaStoryboard b = inv.getArgument(0);
            // 模拟乐观锁: 仅当存储中的 version 与入参一致时成功
            AiDramaStoryboard existing = boardStore.get(b.getId());
            if (existing != null && existing.getVersion() != null && b.getVersion() != null
                    && !existing.getVersion().equals(b.getVersion())) {
                return 0;
            }
            b.setVersion((b.getVersion() == null ? 0 : b.getVersion()) + 1);
            boardStore.put(b.getId(), b);
            return 1;
        });
        when(storyboardMapper.selectList(any())).thenAnswer(inv ->
                new ArrayList<>(sortedBoards()));

        service = new StoryboardVideoService(projectMapper, storyboardMapper, videoGateway, composeService);
        service.setShotTimeoutMillis(2000L);
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    private List<AiDramaStoryboard> sortedBoards() {
        List<AiDramaStoryboard> list = new ArrayList<>(boardStore.values());
        list.sort((a, b) -> {
            Integer sa = a.getSceneNo() == null ? Integer.MAX_VALUE : a.getSceneNo();
            Integer sb = b.getSceneNo() == null ? Integer.MAX_VALUE : b.getSceneNo();
            return sa.compareTo(sb);
        });
        return list;
    }

    private CreateProjectRequest projectReq(String title, List<StoryboardItemRequest> boards) {
        CreateProjectRequest req = new CreateProjectRequest();
        req.setTitle(title);
        req.setAspectRatio("16:9");
        req.setStoryboards(boards);
        return req;
    }

    private StoryboardItemRequest item(int sceneNo, String location, String prompt) {
        StoryboardItemRequest it = new StoryboardItemRequest();
        it.setSceneNo(sceneNo);
        it.setLocationName(location);
        it.setVideoPrompt(prompt);
        it.setDurationSeconds(new BigDecimal("5"));
        return it;
    }

    private ProjectDetailVO seedProject(int... sceneNos) {
        List<StoryboardItemRequest> items = new ArrayList<>();
        for (int sceneNo : sceneNos) {
            String loc = (sceneNo <= 2) ? "客厅" : "街道";
            items.add(item(sceneNo, loc, "prompt-" + sceneNo));
        }
        return service.createProject(projectReq("测试短剧", items));
    }

    // ==================== 状态机 ====================

    @Test
    @DisplayName("PENDING → GENERATING → SUCCEEDED 写入 video_url/last_frame/media_job")
    void stateMachineSuccess() {
        ProjectDetailVO vo = seedProject(1);
        AiDramaStoryboard board = vo.getStoryboards().get(0);
        assertEquals(AiDramaStoryboard.STATUS_PENDING, board.getVideoStatus());

        when(videoGateway.generate(any())).thenAnswer(inv -> {
            VideoGateway.Command cmd = inv.getArgument(0);
            gatewayCalls.add(cmd);
            return new VideoGateway.Result("https://cdn/v1.mp4", "https://cdn/f1.jpg", "vid-1", "job-1");
        });

        AiDramaStoryboard done = service.generateStoryboardVideo(board.getId());
        assertEquals(AiDramaStoryboard.STATUS_SUCCEEDED, done.getVideoStatus());
        assertEquals("https://cdn/v1.mp4", done.getVideoUrl());
        assertEquals("https://cdn/f1.jpg", done.getLastFrameUrl());
        assertEquals("job-1", done.getMediaJobId());
        assertEquals("vid-1", done.getVideoId());
        assertNull(done.getErrorMessage());
        assertEquals(1, gatewayCalls.size());

        // 项目 compose_status 保持 NONE (尚未合成过)
        AiDramaProject project = projectStore.get(vo.getProject().getId());
        assertEquals(AiDramaProject.COMPOSE_NONE, project.getComposeStatus());
    }

    @Test
    @DisplayName("Gateway 失败 → FAILED + errorMessage, 不返回假 URL")
    void stateMachineFailClosed() {
        ProjectDetailVO vo = seedProject(1);
        AiDramaStoryboard board = vo.getStoryboards().get(0);

        when(videoGateway.generate(any()))
                .thenThrow(new BusinessException(
                        com.yutong.common.errorcode.ErrorCode.AI_PROVIDER_ERROR,
                        "未配置可用的 video 供应商"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.generateStoryboardVideo(board.getId()));
        assertTrue(ex.getMessage().contains("未配置可用的 video 供应商"));

        AiDramaStoryboard after = boardStore.get(board.getId());
        assertEquals(AiDramaStoryboard.STATUS_FAILED, after.getVideoStatus());
        assertNull(after.getVideoUrl());
        assertNotNull(after.getErrorMessage());
    }

    @Test
    @DisplayName("SUCCEEDED 状态不可再次发起生成")
    void illegalTransitionRejected() {
        ProjectDetailVO vo = seedProject(1);
        AiDramaStoryboard board = vo.getStoryboards().get(0);
        board.setVideoStatus(AiDramaStoryboard.STATUS_SUCCEEDED);
        board.setVideoUrl("https://cdn/ok.mp4");
        boardStore.put(board.getId(), board);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.generateStoryboardVideo(board.getId()));
        assertTrue(ex.getMessage().contains("不允许发起生成"));
        verify(videoGateway, never()).generate(any());
    }

    @Test
    @DisplayName("乐观锁 version 冲突 → 拒绝并抛 SYS_OPTIMISTIC_LOCK")
    void optimisticLockConflict() {
        ProjectDetailVO vo = seedProject(1);
        AiDramaStoryboard board = vo.getStoryboards().get(0);

        // 模拟并发: 第一次 updateById 返回 0
        when(storyboardMapper.updateById(any(AiDramaStoryboard.class))).thenReturn(0);

        assertThrows(BusinessException.class,
                () -> service.generateStoryboardVideo(board.getId()));
        verify(videoGateway, never()).generate(any());
    }

    @Test
    @DisplayName("FAILED 可重试进入 GENERATING")
    void failedCanRetry() {
        ProjectDetailVO vo = seedProject(1);
        AiDramaStoryboard board = vo.getStoryboards().get(0);
        board.setVideoStatus(AiDramaStoryboard.STATUS_FAILED);
        board.setErrorMessage("previous error");
        boardStore.put(board.getId(), board);

        when(videoGateway.generate(any()))
                .thenReturn(new VideoGateway.Result("https://cdn/retry.mp4", null, null, "job-r"));

        AiDramaStoryboard done = service.generateStoryboardVideo(board.getId());
        assertEquals(AiDramaStoryboard.STATUS_SUCCEEDED, done.getVideoStatus());
        assertEquals("https://cdn/retry.mp4", done.getVideoUrl());
    }

    // ==================== 分组串行 ====================

    @Test
    @DisplayName("同 location 按 scene_no 串行, lastFrameUrl 作下一镜首帧")
    void sameLocationSerialWithLastFrame() {
        ProjectDetailVO vo = seedProject(1, 2, 3);
        // seedProject: scene 1,2 = 客厅; 3 = 街道
        List<AiDramaStoryboard> boards = vo.getStoryboards();
        assertEquals(3, boards.size());

        AtomicInteger call = new AtomicInteger();
        when(videoGateway.generate(any())).thenAnswer(inv -> {
            VideoGateway.Command cmd = inv.getArgument(0);
            gatewayCalls.add(cmd);
            int n = call.incrementAndGet();
            if (n == 1) {
                return new VideoGateway.Result("https://cdn/s1.mp4", "https://cdn/s1-frame.jpg", "v1", "j1");
            }
            if (n == 2) {
                return new VideoGateway.Result("https://cdn/s2.mp4", "https://cdn/s2-frame.jpg", "v2", "j2");
            }
            return new VideoGateway.Result("https://cdn/s3.mp4", "https://cdn/s3-frame.jpg", "v3", "j3");
        });

        // 直接测 processGroupSerially 的分组结果
        Map<String, List<AiDramaStoryboard>> groups = service.groupByLocation(boards);
        assertEquals(2, groups.size());
        List<AiDramaStoryboard> living = groups.get("客厅");
        assertNotNull(living);
        assertEquals(2, living.size());
        assertEquals(1, living.get(0).getSceneNo());
        assertEquals(2, living.get(1).getSceneNo());

        AiDramaProject project = vo.getProject();
        service.processGroupSerially(project, living);

        // 第一镜: 无 firstFrame
        assertNull(gatewayCalls.get(0).firstFrameUrl());
        // 第二镜: firstFrame = 第一镜 lastFrameUrl
        assertEquals("https://cdn/s1-frame.jpg", gatewayCalls.get(1).firstFrameUrl());

        assertEquals(AiDramaStoryboard.STATUS_SUCCEEDED, boardStore.get(living.get(0).getId()).getVideoStatus());
        assertEquals(AiDramaStoryboard.STATUS_SUCCEEDED, boardStore.get(living.get(1).getId()).getVideoStatus());
    }

    @Test
    @DisplayName("同 location 前一镜失败 → 后续镜 firstFrame 清空, 继续生成")
    void failureClearsLastFrameContinuation() {
        ProjectDetailVO vo = seedProject(1, 2);
        List<AiDramaStoryboard> living = service.groupByLocation(vo.getStoryboards()).get("客厅");

        when(videoGateway.generate(any())).thenAnswer(inv -> {
            VideoGateway.Command cmd = inv.getArgument(0);
            gatewayCalls.add(cmd);
            if (gatewayCalls.size() == 1) {
                throw new BusinessException(
                        com.yutong.common.errorcode.ErrorCode.AI_PROVIDER_ERROR, "shot1 boom");
            }
            return new VideoGateway.Result("https://cdn/s2.mp4", null, null, "j2");
        });

        service.processGroupSerially(vo.getProject(), living);

        assertEquals(AiDramaStoryboard.STATUS_FAILED, boardStore.get(living.get(0).getId()).getVideoStatus());
        assertEquals(AiDramaStoryboard.STATUS_SUCCEEDED, boardStore.get(living.get(1).getId()).getVideoStatus());
        assertNull(gatewayCalls.get(1).firstFrameUrl(), "前一镜失败后不得使用假 lastFrame");
    }

    @Test
    @DisplayName("已 SUCCEEDED 的分镜在重跑时跳过并作为 lastFrame 源")
    void succeededSkippedAndFeedsLastFrame() {
        ProjectDetailVO vo = seedProject(1, 2);
        List<AiDramaStoryboard> living = new ArrayList<>(vo.getStoryboards());
        AiDramaStoryboard first = living.get(0);
        first.setVideoStatus(AiDramaStoryboard.STATUS_SUCCEEDED);
        first.setVideoUrl("https://cdn/already.mp4");
        first.setLastFrameUrl("https://cdn/already-frame.jpg");
        boardStore.put(first.getId(), first);

        when(videoGateway.generate(any())).thenAnswer(inv -> {
            VideoGateway.Command cmd = inv.getArgument(0);
            gatewayCalls.add(cmd);
            return new VideoGateway.Result("https://cdn/s2.mp4", null, null, "j2");
        });

        service.processGroupSerially(vo.getProject(), living);

        assertEquals(1, gatewayCalls.size());
        assertEquals("https://cdn/already-frame.jpg", gatewayCalls.get(0).firstFrameUrl());
        assertEquals(AiDramaStoryboard.STATUS_SUCCEEDED, boardStore.get(living.get(1).getId()).getVideoStatus());
    }

    @Test
    @DisplayName("generateAllVideos 完整跑通两组, 跨组均成功")
    void generateAllVideosBothGroups() {
        ProjectDetailVO vo = seedProject(1, 2, 3);
        when(videoGateway.generate(any())).thenAnswer(inv -> {
            VideoGateway.Command cmd = inv.getArgument(0);
            gatewayCalls.add(cmd);
            return new VideoGateway.Result(
                    "https://cdn/" + System.nanoTime() + ".mp4",
                    "https://cdn/frame.jpg", null, "job-x");
        });

        BatchVideoResult result = service.generateAllVideos(vo.getProject().getId());
        assertEquals(3, result.getTotal());
        assertEquals(3, result.getSucceeded());
        assertEquals(0, result.getFailed());
        assertEquals(3, gatewayCalls.size());

        // 客厅组: 第二镜 firstFrame = 第一镜 lastFrame
        Map<String, List<VideoGateway.Command>> byHint = new HashMap<>();
        for (VideoGateway.Command c : gatewayCalls) {
            // prompt 含 prompt-N
            String p = c.prompt();
            if (p.contains("prompt-1")) byHint.put("s1", List.of(c));
            if (p.contains("prompt-2")) byHint.put("s2", List.of(c));
            if (p.contains("prompt-3")) byHint.put("s3", List.of(c));
        }
        // s1 无 firstFrame, s2 有 firstFrame(来自 s1), s3 无 (另一组首镜)
        assertNull(byHint.get("s1").get(0).firstFrameUrl());
        assertNotNull(byHint.get("s2").get(0).firstFrameUrl());
        assertNull(byHint.get("s3").get(0).firstFrameUrl());
    }

    @Test
    @DisplayName("groupByLocation 空 location 归入默认键, scene_no 升序")
    void groupByLocationDefaultKeyAndSort() {
        List<AiDramaStoryboard> boards = new ArrayList<>();
        AiDramaStoryboard a = new AiDramaStoryboard();
        a.setId("a");
        a.setSceneNo(3);
        a.setLocationName(null);
        AiDramaStoryboard b = new AiDramaStoryboard();
        b.setId("b");
        b.setSceneNo(1);
        b.setLocationName("  ");
        AiDramaStoryboard c = new AiDramaStoryboard();
        c.setId("c");
        c.setSceneNo(2);
        c.setLocationName("天台");
        boards.add(a);
        boards.add(b);
        boards.add(c);

        Map<String, List<AiDramaStoryboard>> groups = service.groupByLocation(boards);
        assertEquals(2, groups.size());
        List<AiDramaStoryboard> def = groups.get(StoryboardVideoService.DEFAULT_LOCATION_KEY);
        assertEquals(2, def.size());
        assertEquals(1, def.get(0).getSceneNo());
        assertEquals(3, def.get(1).getSceneNo());
        assertEquals(1, groups.get("天台").size());
    }

    // ==================== Compose 拒绝 ====================

    @Test
    @DisplayName("任一镜未成功 → compose 拒绝, 不调用 VideoComposeService")
    void composeRejectedWhenAnyNotSucceeded() {
        ProjectDetailVO vo = seedProject(1, 2);
        List<AiDramaStoryboard> boards = vo.getStoryboards();
        boards.get(0).setVideoStatus(AiDramaStoryboard.STATUS_SUCCEEDED);
        boards.get(0).setVideoUrl("https://cdn/ok.mp4");
        boardStore.put(boards.get(0).getId(), boards.get(0));
        boards.get(1).setVideoStatus(AiDramaStoryboard.STATUS_FAILED);
        boards.get(1).setErrorMessage("boom");
        boardStore.put(boards.get(1).getId(), boards.get(1));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.composeFromProject(vo.getProject().getId()));
        assertTrue(ex.getMessage().contains("拒绝合成"));
        assertTrue(ex.getMessage().contains("FAILED"));
        verify(composeService, never()).submit(any());
    }

    @Test
    @DisplayName("全部 SUCCEEDED → 按 scene_no 排序提交 compose, 回填 job_id/path")
    void composeSuccessSortedBySceneNo() {
        ProjectDetailVO vo = seedProject(1, 2);
        List<AiDramaStoryboard> boards = vo.getStoryboards();
        // 故意乱序写入 store: scene2 成功, scene1 成功
        for (AiDramaStoryboard b : boards) {
            b.setVideoStatus(AiDramaStoryboard.STATUS_SUCCEEDED);
            b.setVideoUrl("https://cdn/scene-" + b.getSceneNo() + ".mp4");
            boardStore.put(b.getId(), b);
        }

        when(composeService.submit(any())).thenAnswer(inv -> {
            SubmitComposeRequest req = inv.getArgument(0);
            composeCalls.add(req);
            DramaComposeJob job = new DramaComposeJob();
            job.setId("compose-job-1");
            job.setStatus(DramaComposeJob.STATUS_PENDING);
            job.setOutputPath("/work/outputs/compose-job-1.mp4");
            return job;
        });

        DramaComposeJob job = service.composeFromProject(vo.getProject().getId());
        assertEquals("compose-job-1", job.getId());
        assertEquals(1, composeCalls.size());
        List<String> shots = composeCalls.get(0).getShotVideos();
        assertEquals(2, shots.size());
        assertEquals("https://cdn/scene-1.mp4", shots.get(0));
        assertEquals("https://cdn/scene-2.mp4", shots.get(1));

        AiDramaProject project = projectStore.get(vo.getProject().getId());
        assertEquals(AiDramaProject.COMPOSE_RUNNING, project.getComposeStatus());
        assertEquals("compose-job-1", project.getComposeJobId());
        assertEquals("/work/outputs/compose-job-1.mp4", project.getComposedPath());
    }

    @Test
    @DisplayName("compose 提交异常 → 项目 compose_status FAILED")
    void composeSubmitFailureMarksProjectFailed() {
        ProjectDetailVO vo = seedProject(1);
        AiDramaStoryboard b = vo.getStoryboards().get(0);
        b.setVideoStatus(AiDramaStoryboard.STATUS_SUCCEEDED);
        b.setVideoUrl("https://cdn/ok.mp4");
        boardStore.put(b.getId(), b);

        when(composeService.submit(any()))
                .thenThrow(new BusinessException(
                        com.yutong.common.errorcode.ErrorCode.SYS_SERVICE_UNAVAILABLE,
                        "ffmpeg 不可用"));

        assertThrows(BusinessException.class,
                () -> service.composeFromProject(vo.getProject().getId()));
        AiDramaProject project = projectStore.get(vo.getProject().getId());
        assertEquals(AiDramaProject.COMPOSE_FAILED, project.getComposeStatus());
    }

    // ==================== 落库 / 查询 ====================

    @Test
    @DisplayName("createProject 落库项目+分镜, ULID 非空, PENDING 初始态")
    void createProjectPersists() {
        ProjectDetailVO vo = seedProject(1, 2);
        assertNotNull(vo.getProject().getId());
        assertEquals(26, vo.getProject().getId().length());
        assertEquals("测试短剧", vo.getProject().getTitle());
        assertEquals(AiDramaProject.COMPOSE_NONE, vo.getProject().getComposeStatus());
        assertEquals(2, vo.getStoryboards().size());
        assertTrue(vo.getStoryboards().stream()
                .allMatch(b -> AiDramaStoryboard.STATUS_PENDING.equals(b.getVideoStatus())));
    }

    @Test
    @DisplayName("项目不存在 → 404")
    void projectNotFound() {
        assertThrows(ResourceNotFoundException.class,
                () -> service.getProjectDetail("no-such-id"));
    }

    @Test
    @DisplayName("轮询 getStoryboardVideo 返回当前状态")
    void pollReturnsCurrentStatus() {
        ProjectDetailVO vo = seedProject(1);
        AiDramaStoryboard polled = service.getStoryboardVideo(vo.getStoryboards().get(0).getId());
        assertEquals(AiDramaStoryboard.STATUS_PENDING, polled.getVideoStatus());
    }

    @Test
    @DisplayName("encode/decode JSON 数组往返")
    void jsonArrayRoundTrip() {
        List<String> src = List.of("https://a/1.png", "https://b/2.png");
        String json = StoryboardVideoService.encodeJsonArray(src);
        assertEquals(src, StoryboardVideoService.decodeJsonArray(json));
        assertEquals(List.of(), StoryboardVideoService.decodeJsonArray("[]"));
        assertEquals(List.of(), StoryboardVideoService.decodeJsonArray(null));
    }

    @Test
    @DisplayName("超时 fail-closed: gateway 阻塞超过 shotTimeoutMillis → FAILED")
    void timeoutFailClosed() {
        ProjectDetailVO vo = seedProject(1);
        AiDramaStoryboard board = vo.getStoryboards().get(0);
        service.setShotTimeoutMillis(150L);

        when(videoGateway.generate(any())).thenAnswer(inv -> {
            Thread.sleep(2000L);
            return new VideoGateway.Result("https://cdn/never.mp4", null, null, "j");
        });

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.generateStoryboardVideo(board.getId()));
        assertTrue(ex.getMessage().contains("超时"));
        AiDramaStoryboard after = boardStore.get(board.getId());
        assertEquals(AiDramaStoryboard.STATUS_FAILED, after.getVideoStatus());
        assertNull(after.getVideoUrl(), "超时不得写入假 URL");
    }

    @Test
    @DisplayName("provider 抛异常 → FAILED + errorMessage")
    void providerExceptionFailClosed() {
        ProjectDetailVO vo = seedProject(1);
        AiDramaStoryboard board = vo.getStoryboards().get(0);
        when(videoGateway.generate(any())).thenThrow(
                new BusinessException(com.yutong.common.errorcode.ErrorCode.SYS_SERVICE_UNAVAILABLE,
                        "未配置可用的 video 供应商"));
        assertThrows(BusinessException.class, () -> service.generateStoryboardVideo(board.getId()));
        assertEquals(AiDramaStoryboard.STATUS_FAILED, boardStore.get(board.getId()).getVideoStatus());
    }
}
