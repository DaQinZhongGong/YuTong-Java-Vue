package com.yutong.sample.drama.controller;

import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import com.yutong.sample.drama.domain.DramaScene;
import com.yutong.sample.drama.domain.DramaScript;
import com.yutong.sample.drama.dto.DramaDetailVO;
import com.yutong.sample.drama.dto.SaveDramaRequest;
import com.yutong.sample.drama.dto.SaveSceneRequest;
import com.yutong.sample.drama.service.DramaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 短剧垂类控制器 — 最小可用 CRUD + scenes。
 * 设计来源: V041 drama_script/drama_scene, Phase 7 yutong-sample-service
 */
@Tag(name = "Drama-短剧垂类")
@RestController
@RequestMapping("/api/v1/dramas")
public class DramaController {

    private final DramaService dramaService;

    public DramaController(DramaService dramaService) {
        this.dramaService = dramaService;
    }

    @Operation(summary = "分页查询剧本", operationId = "pageDramas")
    @GetMapping
    public Result<PageResult<DramaScript>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String status) {
        return Result.ok(dramaService.pageScripts(PageRequest.of(page, size), title, status), TraceContext.getTraceId());
    }

    @Operation(summary = "查询剧本详情(含场景)", operationId = "getDramaDetail")
    @GetMapping("/{id}")
    public Result<DramaDetailVO> detail(@PathVariable String id) {
        return Result.ok(dramaService.getDetail(id), TraceContext.getTraceId());
    }

    @Operation(summary = "创建剧本", operationId = "createDrama")
    @PostMapping
    public Result<DramaScript> create(@Valid @RequestBody SaveDramaRequest request) {
        return Result.ok(dramaService.createScript(request), TraceContext.getTraceId());
    }

    @Operation(summary = "更新剧本", operationId = "updateDrama")
    @PutMapping("/{id}")
    public Result<DramaScript> update(@PathVariable String id, @Valid @RequestBody SaveDramaRequest request) {
        return Result.ok(dramaService.updateScript(id, request), TraceContext.getTraceId());
    }

    @Operation(summary = "删除剧本", operationId = "deleteDrama")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        dramaService.deleteScript(id);
        return Result.ok(null, TraceContext.getTraceId());
    }

    @Operation(summary = "查询场景列表", operationId = "listDramaScenes")
    @GetMapping("/{dramaId}/scenes")
    public Result<List<DramaScene>> listScenes(@PathVariable String dramaId) {
        return Result.ok(dramaService.listScenes(dramaId), TraceContext.getTraceId());
    }

    @Operation(summary = "创建场景", operationId = "createDramaScene")
    @PostMapping("/{dramaId}/scenes")
    public Result<DramaScene> createScene(@PathVariable String dramaId, @Valid @RequestBody SaveSceneRequest request) {
        return Result.ok(dramaService.createScene(dramaId, request), TraceContext.getTraceId());
    }

    @Operation(summary = "更新场景", operationId = "updateDramaScene")
    @PutMapping("/{dramaId}/scenes/{sceneId}")
    public Result<DramaScene> updateScene(@PathVariable String dramaId, @PathVariable String sceneId,
                                          @Valid @RequestBody SaveSceneRequest request) {
        return Result.ok(dramaService.updateScene(dramaId, sceneId, request), TraceContext.getTraceId());
    }

    @Operation(summary = "删除场景", operationId = "deleteDramaScene")
    @DeleteMapping("/{dramaId}/scenes/{sceneId}")
    public Result<Void> deleteScene(@PathVariable String dramaId, @PathVariable String sceneId) {
        dramaService.deleteScene(dramaId, sceneId);
        return Result.ok(null, TraceContext.getTraceId());
    }
}
