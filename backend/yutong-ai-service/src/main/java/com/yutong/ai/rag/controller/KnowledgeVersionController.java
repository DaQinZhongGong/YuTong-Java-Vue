package com.yutong.ai.rag.controller;

import com.yutong.ai.rag.domain.AiKnowledgeVersion;
import com.yutong.ai.rag.service.KnowledgeVersionService;
import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.Result;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识库版本管理接口
 * 设计来源: ADR 0004 P2-E 知识库 RAG 深度
 *
 * 落点: 39-知识库运营详设
 *
 * 端点:
 *   GET  /api/v1/ai/knowledge-bases/{id}/versions            列表 (倒序)
 *   POST /api/v1/ai/knowledge-bases/{id}/versions            创建新版本 (BUILDING)
 *   POST /api/v1/ai/knowledge-bases/{id}/versions/{v}/activate  激活指定版本
 *   POST /api/v1/ai/knowledge-bases/{id}/versions/{v}/fail     标记失败
 */
@Hidden
@Tag(name = "AI-知识库版本管理")
@RestController
@RequestMapping("/api/v1/ai/knowledge-bases/{id}/versions")
public class KnowledgeVersionController {

    private final KnowledgeVersionService service;

    public KnowledgeVersionController(KnowledgeVersionService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermission("ai:rag:version:list")
    public Result<List<AiKnowledgeVersion>> list(@PathVariable("id") String id) {
        return Result.ok(service.list(id));
    }

    @PostMapping
    @RequiresPermission("ai:rag:version:create")
    public Result<Integer> create(@PathVariable("id") String id,
                                  @RequestParam(value = "note", required = false) String note) {
        return Result.ok(service.createVersion(id, note));
    }

    @PostMapping("/{version}/activate")
    @RequiresPermission("ai:rag:version:activate")
    public Result<Void> activate(@PathVariable("id") String id,
                                 @PathVariable("version") int version) {
        service.activate(id, version);
        return Result.ok();
    }

    @PostMapping("/{version}/fail")
    @RequiresPermission("ai:rag:version:create")
    public Result<Void> markFailed(@PathVariable("id") String id,
                                   @PathVariable("version") int version) {
        service.markFailed(id, version);
        return Result.ok();
    }
}
