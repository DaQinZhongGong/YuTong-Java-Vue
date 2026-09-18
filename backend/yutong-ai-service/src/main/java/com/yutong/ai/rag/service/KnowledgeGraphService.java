package com.yutong.ai.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.ai.chat.service.llm.LlmMessage;
import com.yutong.ai.chat.service.llm.LlmProviderSelector;
import com.yutong.ai.chat.service.llm.LlmRequest;
import com.yutong.ai.chat.service.llm.LlmResponse;
import com.yutong.ai.rag.domain.AiDocumentChunk;
import com.yutong.ai.rag.domain.AiKnowledgeGraph;
import com.yutong.ai.rag.domain.AiKnowledgeGraphSegment;
import com.yutong.ai.rag.mapper.AiDocumentChunkMapper;
import com.yutong.ai.rag.mapper.AiKnowledgeGraphMapper;
import com.yutong.ai.rag.mapper.AiKnowledgeGraphSegmentMapper;
import com.yutong.auth.DataScopeResolver;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.auth.DataScope;
import com.yutong.common.auth.DataScopeType;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessConflictException;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.id.IdGenerator;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.trace.TraceContext;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 知识图谱服务。从知识库分块调用已启用 LLM 抽取实体/关系，禁止空图伪装 READY。
 */
@Service
public class KnowledgeGraphService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeGraphService.class);
    public static final String RESOURCE_CODE = "ai:knowledge-graph";
    private static final int MAX_CHUNKS = 20;
    private static final int MAX_CHUNK_CHARS = 4000;

    private static final String SYSTEM_PROMPT = """
            你是知识图谱抽取器。只输出 JSON，不要 markdown。
            格式：{"nodes":[{"id":"唯一名","label":"显示名","type":"PERSON|ORG|CONCEPT|PLACE|EVENT"}],"edges":[{"source":"节点id","target":"节点id","relation":"关系名"}]}
            没有实体时输出 {"nodes":[],"edges":[]}。id 必须稳定、简短、中文或英文均可。
            """;

    private final AiKnowledgeGraphMapper graphMapper;
    private final AiKnowledgeGraphSegmentMapper segmentMapper;
    private final AiDocumentChunkMapper chunkMapper;
    private final DataScopeResolver dataScopeResolver;
    private final LlmProviderSelector providerSelector;
    private final ExecutorService buildExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "yutong-kg-build-" + System.nanoTime());
        t.setDaemon(true);
        return t;
    });

    public KnowledgeGraphService(AiKnowledgeGraphMapper graphMapper,
                                 AiKnowledgeGraphSegmentMapper segmentMapper,
                                 AiDocumentChunkMapper chunkMapper,
                                 DataScopeResolver dataScopeResolver,
                                 LlmProviderSelector providerSelector) {
        this.graphMapper = graphMapper;
        this.segmentMapper = segmentMapper;
        this.chunkMapper = chunkMapper;
        this.dataScopeResolver = dataScopeResolver;
        this.providerSelector = providerSelector;
    }

    @PreDestroy
    public void shutdown() {
        buildExecutor.shutdown();
    }

    public PageResult<AiKnowledgeGraph> list(PageRequest request, String kbId, String status, String keyword) {
        DataScope scope = dataScopeResolver.resolve(RESOURCE_CODE);
        LambdaQueryWrapper<AiKnowledgeGraph> wrapper = new LambdaQueryWrapper<AiKnowledgeGraph>()
                .eq(AiKnowledgeGraph::getTenantId, CurrentUserContext.getTenantId())
                .eq(kbId != null && !kbId.isBlank(), AiKnowledgeGraph::getKbId, kbId)
                .eq(status != null && !status.isBlank(), AiKnowledgeGraph::getStatus, status)
                .like(keyword != null && !keyword.isBlank(), AiKnowledgeGraph::getName, keyword)
                .orderByDesc(AiKnowledgeGraph::getCreatedTime);
        applyDataScope(wrapper, scope);
        Page<AiKnowledgeGraph> page = graphMapper.selectPage(new Page<>(request.page(), request.size()), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), request.page(), request.size());
    }

    public AiKnowledgeGraph get(String id) {
        AiKnowledgeGraph graph = graphMapper.selectById(id);
        if (graph == null) {
            throw new ResourceNotFoundException("知识图谱不存在: " + id);
        }
        assertTenant(graph);
        return graph;
    }

    public List<AiKnowledgeGraphSegment> listSegments(String graphId) {
        AiKnowledgeGraph graph = get(graphId);
        return segmentMapper.selectList(new LambdaQueryWrapper<AiKnowledgeGraphSegment>()
                .eq(AiKnowledgeGraphSegment::getTenantId, CurrentUserContext.getTenantId())
                .eq(AiKnowledgeGraphSegment::getGraphId, graph.getId())
                .orderByDesc(AiKnowledgeGraphSegment::getCreatedTime));
    }

    @Transactional
    public AiKnowledgeGraph create(AiKnowledgeGraph graph) {
        if (graph.getName() == null || graph.getName().isBlank()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "name 不能为空");
        }
        if (graph.getId() == null || graph.getId().isBlank()) {
            graph.setId(IdGenerator.nextId());
        }
        graph.setTenantId(CurrentUserContext.getTenantId());
        graph.setCreatedBy(CurrentUserContext.getUserId());
        graph.setStatus(AiKnowledgeGraph.STATUS_DRAFT);
        if (graph.getEntityCount() == null) {
            graph.setEntityCount(0);
        }
        if (graph.getRelationCount() == null) {
            graph.setRelationCount(0);
        }
        graph.setVersion(0);
        graphMapper.insert(graph);
        return graph;
    }

    /**
     * DRAFT/FAILED → BUILDING，立即返回；后台用已启用 LLM 抽取实体关系。
     * 无知识库、无分块、无供应商时标记 FAILED，绝不写空图 READY。
     */
    public AiKnowledgeGraph build(String id) {
        AiKnowledgeGraph graph = get(id);
        if (!AiKnowledgeGraph.STATUS_DRAFT.equals(graph.getStatus())
                && !AiKnowledgeGraph.STATUS_FAILED.equals(graph.getStatus())) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "仅 DRAFT/FAILED 可构建，当前状态=" + graph.getStatus());
        }
        graph.setStatus(AiKnowledgeGraph.STATUS_BUILDING);
        graph.setUpdatedBy(CurrentUserContext.getUserId());
        graph.setRemark(null);
        int rows = graphMapper.updateById(graph);
        if (rows == 0) {
            throw new BusinessConflictException("数据已被他人修改，请刷新后重试");
        }

        String userId = CurrentUserContext.getUserId();
        String tenantId = CurrentUserContext.getTenantId();
        String username = CurrentUserContext.getUsername();
        String deptId = CurrentUserContext.getDeptId();
        String deptPath = CurrentUserContext.getDeptPath();
        DataScopeType dataScopeType = CurrentUserContext.getDataScopeType();
        String traceId = TraceContext.getTraceId();
        buildExecutor.execute(() -> {
            CurrentUserContext.set(userId, tenantId, username, deptId, deptPath, dataScopeType);
            TraceContext.setTraceId(traceId);
            try {
                runExtraction(id);
            } finally {
                CurrentUserContext.clear();
                TraceContext.clear();
            }
        });
        return graphMapper.selectById(id);
    }

    public AiKnowledgeGraph queryWithSegments(String id) {
        return get(id);
    }

    void runExtraction(String graphId) {
        AiKnowledgeGraph graph = graphMapper.selectById(graphId);
        if (graph == null || !AiKnowledgeGraph.STATUS_BUILDING.equals(graph.getStatus())) {
            return;
        }
        try {
            if (graph.getKbId() == null || graph.getKbId().isBlank()) {
                markFailed(graph, "未关联知识库，无法抽取实体关系");
                return;
            }
            var runtimeOpt = providerSelector.selectEnabledProvider();
            if (runtimeOpt.isEmpty()) {
                markFailed(graph, "未配置可用 LLM 供应商，无法抽取知识图谱");
                return;
            }
            List<AiDocumentChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<AiDocumentChunk>()
                    .eq(AiDocumentChunk::getTenantId, graph.getTenantId())
                    .eq(AiDocumentChunk::getKnowledgeBaseId, graph.getKbId())
                    .orderByAsc(AiDocumentChunk::getChunkNo)
                    .last("LIMIT " + MAX_CHUNKS));
            if (chunks.isEmpty()) {
                markFailed(graph, "知识库无分块，请先入库文档再构建图谱");
                return;
            }

            segmentMapper.delete(new LambdaQueryWrapper<AiKnowledgeGraphSegment>()
                    .eq(AiKnowledgeGraphSegment::getGraphId, graph.getId()));

            LlmProviderSelector.ProviderRuntime runtime = runtimeOpt.get();
            List<GraphExtractionParser.GraphSnapshot> parts = new ArrayList<>();
            for (AiDocumentChunk chunk : chunks) {
                String text = chunk.getChunkText() == null ? "" : chunk.getChunkText();
                if (text.isBlank()) {
                    continue;
                }
                if (text.length() > MAX_CHUNK_CHARS) {
                    text = text.substring(0, MAX_CHUNK_CHARS);
                }
                LlmRequest req = new LlmRequest(
                        runtime.defaultModel(),
                        List.of(LlmMessage.system(SYSTEM_PROMPT), LlmMessage.user("抽取以下文本：\n" + text)),
                        0.1, 1200, false, "KNOWLEDGE_GRAPH");
                LlmResponse resp = runtime.adapter().chat(req);
                if (resp.isError() || resp.content() == null || resp.content().isBlank()) {
                    String err = resp.error() == null ? "empty content" : resp.error().getMessage();
                    markFailed(graph, "LLM 抽取失败: " + err);
                    return;
                }
                GraphExtractionParser.GraphSnapshot snapshot = GraphExtractionParser.parse(resp.content());
                parts.add(snapshot);
                AiKnowledgeGraphSegment segment = new AiKnowledgeGraphSegment();
                segment.setId(IdGenerator.nextId());
                segment.setTenantId(graph.getTenantId());
                segment.setCreatedBy(graph.getCreatedBy());
                segment.setGraphId(graph.getId());
                segment.setSourceChunkId(chunk.getId());
                segment.setEntityJson(GraphExtractionParser.toGraphJson(
                        new GraphExtractionParser.GraphSnapshot(snapshot.nodes(), List.of())));
                segment.setRelationJson(GraphExtractionParser.toGraphJson(
                        new GraphExtractionParser.GraphSnapshot(List.of(), snapshot.edges())));
                segmentMapper.insert(segment);
            }

            GraphExtractionParser.GraphSnapshot merged = GraphExtractionParser.merge(parts);
            if (merged.nodes().isEmpty()) {
                markFailed(graph, "LLM 未抽出任何实体，请检查文档内容或更换模型");
                return;
            }
            graph.setGraphJson(GraphExtractionParser.toGraphJson(merged));
            graph.setEntityCount(merged.nodes().size());
            graph.setRelationCount(merged.edges().size());
            graph.setStatus(AiKnowledgeGraph.STATUS_READY);
            graph.setRemark("extracted by " + runtime.provider().getProviderCode() + "/" + runtime.defaultModel());
            graphMapper.updateById(graph);
            log.info("knowledge graph extracted: id={} nodes={} edges={}", graphId, merged.nodes().size(), merged.edges().size());
        } catch (Exception e) {
            log.warn("knowledge graph extract error: id={} err={}", graphId, e.toString());
            markFailed(graph, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private void markFailed(AiKnowledgeGraph graph, String reason) {
        graph.setStatus(AiKnowledgeGraph.STATUS_FAILED);
        graph.setRemark(reason);
        graphMapper.updateById(graph);
        log.warn("knowledge graph FAILED: id={} reason={}", graph.getId(), reason);
    }

    private void assertTenant(AiKnowledgeGraph graph) {
        String currentTenant = CurrentUserContext.getTenantId();
        if (currentTenant != null && !currentTenant.equals(graph.getTenantId())) {
            throw new ResourceNotFoundException("知识图谱不存在: " + graph.getId());
        }
    }

    private void applyDataScope(LambdaQueryWrapper<AiKnowledgeGraph> wrapper, DataScope scope) {
        if (scope == null) {
            return;
        }
        if (scope.scopeType() == DataScopeType.ALL || scope.scopeType() == DataScopeType.TENANT) {
            return;
        }
        String userId = scope.userId();
        if (userId == null || userId.isBlank()) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.eq(AiKnowledgeGraph::getCreatedBy, userId);
    }
}
