package com.yutong.ai.skill.controller;

import com.yutong.ai.skill.domain.AiSkill;
import com.yutong.ai.skill.dto.ExecuteSkillRequest;
import com.yutong.ai.skill.dto.ExecuteSkillResponse;
import com.yutong.ai.skill.service.BuiltinSkillCatalog;
import com.yutong.ai.skill.service.SkillExecutor;
import com.yutong.ai.skill.service.SkillRegistry;
import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * Skill 管理接口。设计来源: V038 Phase 3 Skill
 * 包含 CRUD + publish/disable + activate_skill (SKILL.md 加载与 per-tenant 缓存)。
 */
@Tag(name = "AI-Skill管理")
@RestController
@RequestMapping("/api/v1/skills")
public class SkillController {

    private final SkillRegistry registry;
    private final SkillExecutor executor;
    private final BuiltinSkillCatalog builtinCatalog;

    public SkillController(SkillRegistry registry, SkillExecutor executor, BuiltinSkillCatalog builtinCatalog) {
        this.registry = registry;
        this.executor = executor;
        this.builtinCatalog = builtinCatalog;
    }

    @Operation(summary = "分页查询 Skill", operationId = "pageSkills")
    @RequiresPermission("ai:skill:list")
    @GetMapping
    public Result<PageResult<AiSkill>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String skillCode,
            @RequestParam(required = false) String skillType,
            @RequestParam(required = false) String status) {
        builtinCatalog.ensurePublished();
        return Result.ok(registry.pageSkills(PageRequest.of(page, size), skillCode, skillType, status),
                TraceContext.getTraceId());
    }

    @Operation(summary = "查询 Skill 详情", operationId = "getSkill")
    @RequiresPermission("ai:skill:detail")
    @GetMapping("/{id}")
    public Result<AiSkill> get(@PathVariable String id) {
        return Result.ok(registry.getSkill(id), TraceContext.getTraceId());
    }

    @Operation(summary = "查询已激活 Skill (按 skillCode，回源缓存)", operationId = "getActivatedSkill")
    @RequiresPermission("ai:skill:detail")
    @GetMapping("/activated/{skillCode}")
    public Result<AiSkill> getActivated(@PathVariable String skillCode) {
        AiSkill skill = registry.getActivated(skillCode);
        if (skill == null) {
            return Result.ok(null, TraceContext.getTraceId());
        }
        return Result.ok(skill, TraceContext.getTraceId());
    }

    @Operation(summary = "创建或更新 Skill 草稿", operationId = "saveSkillDraft")
    @RequiresPermission("ai:skill:add")
    @PostMapping
    public Result<AiSkill> save(@RequestBody AiSkill skill) {
        return Result.ok(registry.saveDraft(skill), TraceContext.getTraceId());
    }

    @Operation(summary = "更新 Skill 草稿", operationId = "updateSkillDraft")
    @RequiresPermission("ai:skill:edit")
    @PutMapping("/{id}")
    public Result<AiSkill> update(@PathVariable String id, @RequestBody AiSkill skill) {
        skill.setId(id);
        return Result.ok(registry.saveDraft(skill), TraceContext.getTraceId());
    }

    @Operation(summary = "发布 Skill (DRAFT→PUBLISHED)", operationId = "publishSkill")
    @RequiresPermission("ai:skill:publish")
    @PostMapping("/{id}/publish")
    public Result<AiSkill> publish(@PathVariable String id, @RequestParam Integer version) {
        return Result.ok(registry.publishSkill(id, version), TraceContext.getTraceId());
    }

    @Operation(summary = "禁用 Skill (→DISABLED)", operationId = "disableSkill")
    @RequiresPermission("ai:skill:disable")
    @PostMapping("/{id}/disable")
    public Result<AiSkill> disable(@PathVariable String id, @RequestParam Integer version) {
        return Result.ok(registry.disableSkill(id, version), TraceContext.getTraceId());
    }

    @Operation(summary = "激活 Skill (加载 SKILL.md，解析并按租户缓存)", operationId = "activateSkill")
    @RequiresPermission("ai:skill:publish")
    @PostMapping("/{id}/activate")
    public Result<AiSkill> activate(@PathVariable String id) {
        return Result.ok(registry.activateSkill(id), TraceContext.getTraceId());
    }

    @Operation(summary = "执行 Skill（analyze/create，产物落 MinIO）", operationId = "executeSkill")
    @RequiresPermission("ai:skill:publish")
    @PostMapping("/{id}/execute")
    public Result<ExecuteSkillResponse> execute(@PathVariable String id,
                                                @Valid @RequestBody ExecuteSkillRequest request) {
        return Result.ok(executor.execute(id, request), TraceContext.getTraceId());
    }
}
