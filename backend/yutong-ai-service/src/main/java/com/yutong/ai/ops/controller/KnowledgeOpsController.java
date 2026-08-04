package com.yutong.ai.ops.controller;

import com.yutong.ai.ops.dto.AskQuestionRequest;
import com.yutong.ai.ops.dto.AskQuestionVO;
import com.yutong.ai.ops.dto.KbStatsVO;
import com.yutong.ai.ops.domain.KbConversationLog;
import com.yutong.ai.ops.service.KnowledgeOpsApplicationService;
import com.yutong.ai.rag.domain.AiDocument;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import com.yutong.auth.RequiresPermission;
import com.yutong.system.log.auditable.Auditable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识库运营接口。设计来源: 35-样例业务矩阵扩展设计 P2 知识库运营。
 *
 * <p>验证 6 项核心能力:
 * <ol>
 *   <li>文档导入: POST /api/v1/kb/documents/{id}/reindex</li>
 *   <li>分块和向量化: 文档导入后自动分块向量化 (DocumentIngestApplicationService.ingest)</li>
 *   <li>权限过滤: POST /api/v1/kb/ask 内部调用 RagRetrievalService 已含 ACL 过滤</li>
 *   <li>问答引用: POST /api/v1/kb/ask 返回 citations 列表</li>
 *   <li>命中率统计: GET /api/v1/kb/stats 返回 todayHitRate/totalHitCount</li>
 *   <li>低置信度拒答: POST /api/v1/kb/ask maxScore < 0.30 时返回 refused=true</li>
 * </ol>
 */
@Tag(name = "知识库运营")
@RestController
@RequestMapping("/api/v1/kb")
public class KnowledgeOpsController {

    private final KnowledgeOpsApplicationService service;

    public KnowledgeOpsController(KnowledgeOpsApplicationService service) {
        this.service = service;
    }

    @Operation(summary = "知识库问答", operationId = "askQuestion")
    @PostMapping("/ask")
    @Auditable(operationType = "ASK", module = "ai", bizType = "kb-conversation", bizIdExpr = "#result.data.conversationNo")
    public Result<AskQuestionVO> ask(@Valid @RequestBody AskQuestionRequest request) {
        return Result.ok(service.askQuestion(request), TraceContext.getTraceId());
    }

    @Operation(summary = "知识库运营统计", operationId = "getKbStats")
    @GetMapping("/stats")
    public Result<KbStatsVO> getStats(@RequestParam String kbId) {
        return Result.ok(service.getStats(kbId), TraceContext.getTraceId());
    }

    @Operation(summary = "分页查询问答历史", operationId = "pageKbConversations")
    @RequiresPermission("ai:knowledge-ops:list")
    @GetMapping("/conversations")
    public Result<PageResult<KbConversationLog>> pageConversations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String kbId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) Boolean refused) {
        return Result.ok(service.pageConversations(PageRequest.of(page, size), kbId, userId, refused),
                TraceContext.getTraceId());
    }

    @Operation(summary = "查询问答详情", operationId = "getKbConversation")
    @RequiresPermission("ai:knowledge-ops:list")
    @GetMapping("/conversations/{id}")
    public Result<KbConversationLog> getConversation(@PathVariable String id) {
        return Result.ok(service.getConversation(id), TraceContext.getTraceId());
    }

    @Operation(summary = "重新索引文档", operationId = "reindexKbDocument")
    @RequiresPermission("ai:knowledge-ops:reindex")
    @PostMapping("/documents/{id}/reindex")
    @Auditable(operationType = "REINDEX", module = "ai", bizType = "kb-document", bizIdExpr = "#id")
    public Result<AiDocument> reindexDocument(@PathVariable String id) {
        return Result.ok(service.reindexDocument(id), TraceContext.getTraceId());
    }
}
