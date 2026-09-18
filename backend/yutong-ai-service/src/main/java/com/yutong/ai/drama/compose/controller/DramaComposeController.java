package com.yutong.ai.drama.compose.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.ai.drama.compose.domain.DramaComposeJob;
import com.yutong.ai.drama.compose.dto.SubmitComposeRequest;
import com.yutong.ai.drama.compose.service.VideoComposeService;
import com.yutong.auth.RequiresPermission;
import com.yutong.common.ratelimit.RateLimiter;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 短剧真机合成接口。设计来源: ADR 0004 P2-D 批次 5-C。
 *
 * <p>端点:
 * <ul>
 *   <li>POST /api/v1/ai/drama/compose — 提交合成 (同步校验落库, 异步执行)</li>
 *   <li>GET /api/v1/ai/drama/compose/{id} — 任务状态 (前端轮询)</li>
 *   <li>GET /api/v1/ai/drama/compose — 分页查询任务</li>
 *   <li>GET /api/v1/ai/drama/compose/capabilities — 运行能力 (ffmpeg 可用性, 前端横幅)</li>
 * </ul>
 *
 * <p>失败关闭: ffmpeg 缺失时提交直接 503; 执行失败落 FAILED + errorMessage, 不返回假 URL。
 */
@Tag(name = "AI-短剧合成")
@RestController
@RequestMapping("/api/v1/ai/drama/compose")
public class DramaComposeController {

    private final VideoComposeService composeService;

    public DramaComposeController(VideoComposeService composeService) {
        this.composeService = composeService;
    }

    @PostMapping
    @RequiresPermission("ai:assistant:use")
    @RateLimiter(keyPrefix = "drama:compose", permits = 10, windowSeconds = 300)
    @Operation(summary = "提交短剧合成 (异步执行)")
    public Result<DramaComposeJob> submit(@Valid @RequestBody SubmitComposeRequest req) {
        return Result.ok(composeService.submit(req));
    }

    @GetMapping("/{id}")
    @RequiresPermission("ai:assistant:use")
    @Operation(summary = "查询合成任务状态")
    public Result<DramaComposeJob> getById(@PathVariable("id") String id) {
        return Result.ok(composeService.getById(id));
    }

    @GetMapping
    @RequiresPermission("ai:assistant:use")
    @Operation(summary = "分页查询合成任务")
    public Result<PageResult<DramaComposeJob>> page(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status) {
        Page<DramaComposeJob> page = composeService.page(pageNo, pageSize, status);
        return Result.ok(PageResult.of(page.getRecords(), page.getTotal(),
                (int) page.getCurrent(), (int) page.getSize()));
    }

    @GetMapping("/capabilities")
    @RequiresPermission("ai:assistant:use")
    @Operation(summary = "合成运行能力 (ffmpeg/ffprobe 可用性)")
    public Result<Map<String, Object>> capabilities() {
        return Result.ok(composeService.capabilities());
    }
}
