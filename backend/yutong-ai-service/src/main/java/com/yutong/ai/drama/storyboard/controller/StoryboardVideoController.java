package com.yutong.ai.drama.storyboard.controller;

import com.yutong.ai.drama.compose.domain.DramaComposeJob;
import com.yutong.ai.drama.storyboard.domain.AiDramaProject;
import com.yutong.ai.drama.storyboard.domain.AiDramaStoryboard;
import com.yutong.ai.drama.storyboard.dto.BatchVideoResult;
import com.yutong.ai.drama.storyboard.dto.CreateProjectRequest;
import com.yutong.ai.drama.storyboard.dto.ProjectDetailVO;
import com.yutong.ai.drama.storyboard.dto.StoryboardItemRequest;
import com.yutong.ai.drama.storyboard.dto.StoryboardSaveRequest;
import com.yutong.ai.drama.storyboard.dto.VideoGenerateRequest;
import com.yutong.ai.drama.storyboard.service.StoryboardVideoService;
import com.yutong.auth.RequiresPermission;
import com.yutong.common.ratelimit.RateLimiter;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 分镜视频生成接口。设计来源: docs/compose/spec/ai-depth-parity.md S2.2
 *
 * <p>端点:
 * <ul>
 *   <li>POST /api/v1/ai/drama/projects — 创建项目 (可内联分镜)</li>
 *   <li>GET  /api/v1/ai/drama/projects — 分页列表</li>
 *   <li>GET  /api/v1/ai/drama/projects/{id} — 详情含 storyboards</li>
 *   <li>POST /api/v1/ai/drama/storyboard/{id}/video — 单镜生成</li>
 *   <li>GET  /api/v1/ai/drama/storyboard/{id}/video — 单镜状态轮询</li>
 *   <li>POST /api/v1/ai/drama/project/{id}/videos/batch — 批量生成</li>
 *   <li>POST /api/v1/ai/drama/project/{id}/compose — 成片合成</li>
 * </ul>
 *
 * <p>失败关闭: 无 provider Key / 超时 / 存在未成功分镜时明确报错, 不返回假 URL。
 */
@Tag(name = "AI-短剧分镜视频")
@RestController
@RequestMapping("/api/v1/ai/drama")
public class StoryboardVideoController {

    private final StoryboardVideoService storyboardVideoService;

    public StoryboardVideoController(StoryboardVideoService storyboardVideoService) {
        this.storyboardVideoService = storyboardVideoService;
    }

    @PostMapping("/projects")
    @RequiresPermission("ai:assistant:use")
    @RateLimiter(keyPrefix = "drama:storyboard:project", permits = 30, windowSeconds = 300)
    @Operation(summary = "创建短剧项目 (可内联分镜)")
    public Result<ProjectDetailVO> createProject(@Valid @RequestBody CreateProjectRequest request) {
        return Result.ok(storyboardVideoService.createProject(request));
    }

    @GetMapping("/projects")
    @RequiresPermission("ai:assistant:use")
    @Operation(summary = "分页查询短剧项目")
    public Result<PageResult<AiDramaProject>> pageProjects(
            @RequestParam(name = "pageNo", required = false) Integer pageNo,
            @RequestParam(name = "pageSize", required = false) Integer pageSize,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        int p = pageNo != null ? pageNo : (page != null ? page : 1);
        int s = pageSize != null ? pageSize : (size != null ? size : 20);
        return Result.ok(storyboardVideoService.pageProjects(PageRequest.of(p, s)));
    }

    @GetMapping("/projects/{id}")
    @RequiresPermission("ai:assistant:use")
    @Operation(summary = "项目详情 (含 storyboards)")
    public Result<ProjectDetailVO> getProject(@PathVariable("id") String id) {
        return Result.ok(storyboardVideoService.getProjectDetail(id));
    }

    @PostMapping("/project/{id}/storyboards")
    @RequiresPermission("ai:assistant:use")
    @RateLimiter(keyPrefix = "drama:storyboard:append", permits = 30, windowSeconds = 300)
    @Operation(summary = "向项目追加分镜")
    public Result<ProjectDetailVO> appendStoryboards(
            @PathVariable("id") String id,
            @Valid @RequestBody StoryboardSaveRequest request) {
        storyboardVideoService.saveStoryboards(id, request.getStoryboards());
        return Result.ok(storyboardVideoService.getProjectDetail(id));
    }

    @PostMapping("/storyboard/{id}/video")
    @RequiresPermission("ai:assistant:use")
    @RateLimiter(keyPrefix = "drama:storyboard:video", permits = 20, windowSeconds = 300)
    @Operation(summary = "单镜视频生成 (同步, 失败关闭)")
    public Result<AiDramaStoryboard> generateVideo(
            @PathVariable("id") String id,
            @Valid @RequestBody(required = false) VideoGenerateRequest request) {
        return Result.ok(storyboardVideoService.generateStoryboardVideo(id, null, request));
    }

    @GetMapping("/storyboard/{id}/video")
    @RequiresPermission("ai:assistant:use")
    @Operation(summary = "单镜视频状态轮询")
    public Result<AiDramaStoryboard> getVideo(@PathVariable("id") String id) {
        return Result.ok(storyboardVideoService.getStoryboardVideo(id));
    }

    @PostMapping("/project/{id}/videos/batch")
    @RequiresPermission("ai:assistant:use")
    @RateLimiter(keyPrefix = "drama:storyboard:batch", permits = 5, windowSeconds = 600)
    @Operation(summary = "批量生成项目分镜视频 (同 location 串行 lastFrame, 跨组并发 ≤4)")
    public Result<BatchVideoResult> generateBatch(@PathVariable("id") String id) {
        return Result.ok(storyboardVideoService.generateAllVideos(id));
    }

    @PostMapping("/project/{id}/compose")
    @RequiresPermission("ai:assistant:use")
    @RateLimiter(keyPrefix = "drama:storyboard:compose", permits = 10, windowSeconds = 300)
    @Operation(summary = "项目成片合成 (全部分镜成功后)")
    public Result<DramaComposeJob> compose(@PathVariable("id") String id) {
        return Result.ok(storyboardVideoService.composeFromProject(id));
    }
}
