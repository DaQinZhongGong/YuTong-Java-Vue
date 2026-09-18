package com.yutong.sample.drama.controller;

import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import com.yutong.sample.drama.domain.*;
import com.yutong.sample.drama.dto.*;
import com.yutong.sample.drama.service.DramaAssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 短剧资产控制器 — 角色/形象/场景地/音频/分镜。
 * 设计来源: V043 5表 drama_character* + drama_location/audio/storyboard
 * 路径与 DramaController 保持一致: /api/v1/dramas/{dramaId}/...
 * 额外兼容任务描述 /api/v1/drama 单数前缀可通过网关别名处理，此处以复数 dramas 为主
 * 风格对齐 DramaController：Result.ok(..., TraceContext.getTraceId())，暂无 @RequiresPermission（与现有 DramaController 一致）
 */
@Tag(name = "Drama-短剧资产")
@RestController
@RequestMapping({"/api/v1/dramas", "/api/v1/drama"})
public class DramaAssetController {

    private final DramaAssetService assetService;

    public DramaAssetController(DramaAssetService assetService) {
        this.assetService = assetService;
    }

    // ---- characters ----
    @Operation(summary = "查询角色列表", operationId = "listDramaCharacters")
    @GetMapping("/{dramaId}/characters")
    public Result<List<DramaCharacter>> listCharacters(@PathVariable String dramaId) {
        return Result.ok(assetService.listCharacters(dramaId), TraceContext.getTraceId());
    }

    @Operation(summary = "创建角色", operationId = "createDramaCharacter")
    @PostMapping("/{dramaId}/characters")
    public Result<DramaCharacter> createCharacter(@PathVariable String dramaId, @Valid @RequestBody SaveCharacterRequest request) {
        return Result.ok(assetService.createCharacter(dramaId, request), TraceContext.getTraceId());
    }

    @Operation(summary = "更新角色", operationId = "updateDramaCharacter")
    @PutMapping("/{dramaId}/characters/{id}")
    public Result<DramaCharacter> updateCharacter(@PathVariable String dramaId, @PathVariable String id,
                                                  @Valid @RequestBody SaveCharacterRequest request) {
        return Result.ok(assetService.updateCharacter(dramaId, id, request), TraceContext.getTraceId());
    }

    @Operation(summary = "删除角色", operationId = "deleteDramaCharacter")
    @DeleteMapping("/{dramaId}/characters/{id}")
    public Result<Void> deleteCharacter(@PathVariable String dramaId, @PathVariable String id) {
        assetService.deleteCharacter(dramaId, id);
        return Result.ok(null, TraceContext.getTraceId());
    }

    // ---- appearances ----
    @Operation(summary = "查询角色形象列表", operationId = "listDramaAppearances")
    @GetMapping("/{dramaId}/characters/{characterId}/appearances")
    public Result<List<DramaCharacterAppearance>> listAppearances(@PathVariable String dramaId, @PathVariable String characterId) {
        return Result.ok(assetService.listAppearances(dramaId, characterId), TraceContext.getTraceId());
    }

    @Operation(summary = "创建角色形象", operationId = "createDramaAppearance")
    @PostMapping("/{dramaId}/characters/{characterId}/appearances")
    public Result<DramaCharacterAppearance> createAppearance(@PathVariable String dramaId, @PathVariable String characterId,
                                                             @RequestBody SaveCharacterAppearanceRequest request) {
        return Result.ok(assetService.createAppearance(dramaId, characterId, request), TraceContext.getTraceId());
    }

    @Operation(summary = "更新角色形象", operationId = "updateDramaAppearance")
    @PutMapping("/{dramaId}/characters/{characterId}/appearances/{id}")
    public Result<DramaCharacterAppearance> updateAppearance(@PathVariable String dramaId, @PathVariable String characterId,
                                                             @PathVariable String id, @RequestBody SaveCharacterAppearanceRequest request) {
        return Result.ok(assetService.updateAppearance(dramaId, characterId, id, request), TraceContext.getTraceId());
    }

    @Operation(summary = "删除角色形象", operationId = "deleteDramaAppearance")
    @DeleteMapping("/{dramaId}/characters/{characterId}/appearances/{id}")
    public Result<Void> deleteAppearance(@PathVariable String dramaId, @PathVariable String characterId, @PathVariable String id) {
        assetService.deleteAppearance(dramaId, characterId, id);
        return Result.ok(null, TraceContext.getTraceId());
    }

    @Operation(summary = "确认角色形象 (选中并锁定)", operationId = "confirmDramaAppearance")
    @PostMapping("/{dramaId}/characters/{characterId}/appearances/{id}/confirm")
    public Result<DramaCharacterAppearance> confirmAppearance(@PathVariable String dramaId,
                                                               @PathVariable String characterId,
                                                               @PathVariable String id) {
        return Result.ok(assetService.confirmAppearance(dramaId, characterId, id), TraceContext.getTraceId());
    }

    @Operation(summary = "撤销角色形象确认 (回到可重选)", operationId = "undoDramaAppearance")
    @PostMapping("/{dramaId}/characters/{characterId}/appearances/{id}/undo")
    public Result<DramaCharacterAppearance> undoAppearance(@PathVariable String dramaId,
                                                            @PathVariable String characterId,
                                                            @PathVariable String id) {
        return Result.ok(assetService.undoAppearance(dramaId, characterId, id), TraceContext.getTraceId());
    }

    // ---- locations ----
    @Operation(summary = "查询场景地列表", operationId = "listDramaLocations")
    @GetMapping("/{dramaId}/locations")
    public Result<List<DramaLocation>> listLocations(@PathVariable String dramaId) {
        return Result.ok(assetService.listLocations(dramaId), TraceContext.getTraceId());
    }

    @Operation(summary = "创建场景地", operationId = "createDramaLocation")
    @PostMapping("/{dramaId}/locations")
    public Result<DramaLocation> createLocation(@PathVariable String dramaId, @Valid @RequestBody SaveLocationRequest request) {
        return Result.ok(assetService.createLocation(dramaId, request), TraceContext.getTraceId());
    }

    @Operation(summary = "更新场景地", operationId = "updateDramaLocation")
    @PutMapping("/{dramaId}/locations/{id}")
    public Result<DramaLocation> updateLocation(@PathVariable String dramaId, @PathVariable String id,
                                                @Valid @RequestBody SaveLocationRequest request) {
        return Result.ok(assetService.updateLocation(dramaId, id, request), TraceContext.getTraceId());
    }

    @Operation(summary = "删除场景地", operationId = "deleteDramaLocation")
    @DeleteMapping("/{dramaId}/locations/{id}")
    public Result<Void> deleteLocation(@PathVariable String dramaId, @PathVariable String id) {
        assetService.deleteLocation(dramaId, id);
        return Result.ok(null, TraceContext.getTraceId());
    }

    // ---- audios ----
    @Operation(summary = "查询音频列表", operationId = "listDramaAudios")
    @GetMapping("/{dramaId}/audios")
    public Result<List<DramaAudio>> listAudios(@PathVariable String dramaId) {
        return Result.ok(assetService.listAudios(dramaId), TraceContext.getTraceId());
    }

    @Operation(summary = "创建音频", operationId = "createDramaAudio")
    @PostMapping("/{dramaId}/audios")
    public Result<DramaAudio> createAudio(@PathVariable String dramaId, @Valid @RequestBody SaveAudioRequest request) {
        return Result.ok(assetService.createAudio(dramaId, request), TraceContext.getTraceId());
    }

    @Operation(summary = "更新音频", operationId = "updateDramaAudio")
    @PutMapping("/{dramaId}/audios/{id}")
    public Result<DramaAudio> updateAudio(@PathVariable String dramaId, @PathVariable String id,
                                          @Valid @RequestBody SaveAudioRequest request) {
        return Result.ok(assetService.updateAudio(dramaId, id, request), TraceContext.getTraceId());
    }

    @Operation(summary = "删除音频", operationId = "deleteDramaAudio")
    @DeleteMapping("/{dramaId}/audios/{id}")
    public Result<Void> deleteAudio(@PathVariable String dramaId, @PathVariable String id) {
        assetService.deleteAudio(dramaId, id);
        return Result.ok(null, TraceContext.getTraceId());
    }

    // ---- storyboards ----
    @Operation(summary = "查询分镜列表", operationId = "listDramaStoryboards")
    @GetMapping("/{dramaId}/storyboards")
    public Result<List<DramaStoryboard>> listStoryboards(@PathVariable String dramaId) {
        return Result.ok(assetService.listStoryboards(dramaId), TraceContext.getTraceId());
    }

    @Operation(summary = "创建分镜", operationId = "createDramaStoryboard")
    @PostMapping("/{dramaId}/storyboards")
    public Result<DramaStoryboard> createStoryboard(@PathVariable String dramaId, @Valid @RequestBody SaveStoryboardRequest request) {
        return Result.ok(assetService.createStoryboard(dramaId, request), TraceContext.getTraceId());
    }

    @Operation(summary = "更新分镜", operationId = "updateDramaStoryboard")
    @PutMapping("/{dramaId}/storyboards/{id}")
    public Result<DramaStoryboard> updateStoryboard(@PathVariable String dramaId, @PathVariable String id,
                                                    @Valid @RequestBody SaveStoryboardRequest request) {
        return Result.ok(assetService.updateStoryboard(dramaId, id, request), TraceContext.getTraceId());
    }

    @Operation(summary = "删除分镜", operationId = "deleteDramaStoryboard")
    @DeleteMapping("/{dramaId}/storyboards/{id}")
    public Result<Void> deleteStoryboard(@PathVariable String dramaId, @PathVariable String id) {
        assetService.deleteStoryboard(dramaId, id);
        return Result.ok(null, TraceContext.getTraceId());
    }
}
