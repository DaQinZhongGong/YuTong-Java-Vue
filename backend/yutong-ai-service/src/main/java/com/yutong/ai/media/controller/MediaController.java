package com.yutong.ai.media.controller;

import com.yutong.ai.media.domain.MediaJob;
import com.yutong.ai.media.dto.CreateMediaRequest;
import com.yutong.ai.media.service.MediaService;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 多模态媒体控制器 — /api/v1/media/{type}
 * 设计来源: V041/V044 ai_media_job — OpenAI 兼容 image/audio，video/ppt 未配置则失败
 * 前端可视化: POST 创建任务 (PENDING→RUNNING→SUCCESS/FAILED)，GET 查询/列表
 */
@Tag(name = "Media-多模态生成")
@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @Operation(summary = "创建媒体任务", operationId = "createMediaJob")
    @PostMapping("/{type}")
    public Result<MediaJob> create(@PathVariable("type") String type,
                                   @Valid @RequestBody CreateMediaRequest request) {
        MediaJob job = mediaService.createJob(type, request);
        return Result.ok(job, TraceContext.getTraceId());
    }

    @Operation(summary = "查询媒体任务详情", operationId = "getMediaJob")
    @GetMapping("/{type}/{id}")
    public Result<MediaJob> get(@PathVariable("type") String type,
                                @PathVariable("id") String id) {
        MediaService.validateMediaType(type);
        MediaJob job = mediaService.getJob(id);
        return Result.ok(job, TraceContext.getTraceId());
    }

    @Operation(summary = "分页查询媒体任务", operationId = "pageMediaJobs")
    @GetMapping("/jobs")
    public Result<PageResult<MediaJob>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String mediaType,
            @RequestParam(required = false) String status) {
        PageResult<MediaJob> result = mediaService.pageJobs(PageRequest.of(page, size), mediaType, status);
        return Result.ok(result, TraceContext.getTraceId());
    }

    /** 兼容按类型分页: GET /api/v1/media/{type}/jobs */
    @Operation(summary = "按类型分页查询媒体任务", operationId = "pageMediaJobsByType")
    @GetMapping("/{type}/jobs")
    public Result<PageResult<MediaJob>> pageByType(
            @PathVariable("type") String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        MediaService.validateMediaType(type);
        PageResult<MediaJob> result = mediaService.pageJobs(PageRequest.of(page, size), type, status);
        return Result.ok(result, TraceContext.getTraceId());
    }
}
