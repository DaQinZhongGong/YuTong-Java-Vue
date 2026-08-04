package com.yutong.ai.ops.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.ai.ops.domain.KbConversationLog;
import com.yutong.ai.ops.domain.KbStatsDaily;
import com.yutong.ai.ops.dto.AskQuestionRequest;
import com.yutong.ai.ops.dto.AskQuestionVO;
import com.yutong.ai.ops.dto.KbStatsVO;
import com.yutong.ai.ops.mapper.KbConversationLogMapper;
import com.yutong.ai.ops.mapper.KbStatsDailyMapper;
import com.yutong.ai.rag.domain.AiDocument;
import com.yutong.ai.rag.domain.AiDocumentChunk;
import com.yutong.ai.rag.domain.AiEmbedding;
import com.yutong.ai.rag.domain.AiKnowledgeBase;
import com.yutong.ai.rag.mapper.AiDocumentChunkMapper;
import com.yutong.ai.rag.mapper.AiDocumentMapper;
import com.yutong.ai.rag.mapper.AiEmbeddingMapper;
import com.yutong.ai.rag.mapper.AiKnowledgeBaseMapper;
import com.yutong.ai.rag.repository.VectorRepository;
import com.yutong.ai.rag.service.DocumentIngestApplicationService;
import com.yutong.ai.rag.service.EmbeddingService;
import com.yutong.ai.rag.service.RagRetrievalService;
import com.yutong.auth.DataScopeResolver;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.auth.DataScope;
import com.yutong.common.auth.DataScopeType;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.id.IdGenerator;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 知识库运营应用服务。设计来源: 35-样例业务矩阵扩展设计 P2 知识库运营。
 *
 * <p>验证 6 项核心能力:
 * <ol>
 *   <li>文档导入: 复用 DocumentIngestApplicationService.ingest</li>
 *   <li>分块和向量化: 复用 RagChunkService + EmbeddingService</li>
 *   <li>权限过滤: 复用 RagAclService (在 RagRetrievalService 内部)</li>
 *   <li>问答引用: 调用 RagRetrievalService.retrieve 获取引用</li>
 *   <li>命中率统计: kb_conversation_log 记录 + kb_stats_daily 汇总</li>
 *   <li>低置信度拒答: maxScore < REFUSE_THRESHOLD 时拒答</li>
 * </ol>
 */
@Service
public class KnowledgeOpsApplicationService {

    public static final String RESOURCE_CODE = "ai:knowledge-ops";

    private static final Logger log = LoggerFactory.getLogger(KnowledgeOpsApplicationService.class);
    private static final double REFUSE_THRESHOLD = 0.30;
    private static final int DEFAULT_TOP_K = 5;
    private static final int ANSWER_CHUNK_LIMIT = 3;
    private static final int ANSWER_CHUNK_TEXT_LENGTH = 500;
    private static final int CITATION_PREVIEW_LENGTH = 200;
    private static final DateTimeFormatter CONV_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final KbConversationLogMapper conversationLogMapper;
    private final KbStatsDailyMapper statsDailyMapper;
    private final AiKnowledgeBaseMapper kbMapper;
    private final AiDocumentMapper documentMapper;
    private final AiDocumentChunkMapper chunkMapper;
    private final AiEmbeddingMapper embeddingMapper;
    private final RagRetrievalService ragRetrievalService;
    private final DocumentIngestApplicationService documentIngestService;
    private final EmbeddingService embeddingService;
    private final VectorRepository vectorRepository;
    private final ObjectMapper objectMapper;
    private final DataScopeResolver dataScopeResolver;

    public KnowledgeOpsApplicationService(KbConversationLogMapper conversationLogMapper,
                                          KbStatsDailyMapper statsDailyMapper,
                                          AiKnowledgeBaseMapper kbMapper,
                                          AiDocumentMapper documentMapper,
                                          AiDocumentChunkMapper chunkMapper,
                                          AiEmbeddingMapper embeddingMapper,
                                          RagRetrievalService ragRetrievalService,
                                          DocumentIngestApplicationService documentIngestService,
                                          EmbeddingService embeddingService,
                                          VectorRepository vectorRepository,
                                          ObjectMapper objectMapper,
                                          DataScopeResolver dataScopeResolver) {
        this.conversationLogMapper = conversationLogMapper;
        this.statsDailyMapper = statsDailyMapper;
        this.kbMapper = kbMapper;
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.embeddingMapper = embeddingMapper;
        this.ragRetrievalService = ragRetrievalService;
        this.documentIngestService = documentIngestService;
        this.embeddingService = embeddingService;
        this.vectorRepository = vectorRepository;
        this.objectMapper = objectMapper;
        this.dataScopeResolver = dataScopeResolver;
    }

    /**
     * 知识库问答。设计来源: 35-样例业务矩阵扩展设计 P2 知识库运营。
     * 流程: 检索 → 计算分数 → 低置信度拒答判断 → 生成答案 → 记录日志。
     */
    @Transactional
    public AskQuestionVO askQuestion(AskQuestionRequest request) {
        long startMs = System.currentTimeMillis();
        String tenantId = CurrentUserContext.getTenantId();
        String userId = CurrentUserContext.getUserId();

        // 1. 校验知识库存在
        AiKnowledgeBase kb = kbMapper.selectById(request.kbId());
        if (kb == null) {
            throw new ResourceNotFoundException(ErrorCode.KB_NOT_FOUND);
        }
        // 2. 校验知识库状态
        if (!AiKnowledgeBase.STATUS_ACTIVE.equals(kb.getStatus())) {
            // 知识库已禁用, 拒答
            return recordRefusedAnswer(request, kb, startMs, KbConversationLog.REFUSE_KB_DISABLED,
                    "知识库当前状态[" + kb.getStatus() + "]已禁用, 暂无法问答");
        }

        // 3. 调用 RAG 检索 (内部已含 ACL 过滤)
        int topK = request.topK() != null && request.topK() > 0 ? request.topK() : DEFAULT_TOP_K;
        List<RagRetrievalService.RetrievalResult> results = ragRetrievalService.retrieve(
                tenantId, request.kbId(), request.question(), userId, topK);

        // 4. 无命中 → 拒答
        if (results.isEmpty()) {
            return recordRefusedAnswer(request, kb, startMs, KbConversationLog.REFUSE_NO_HITS,
                    "抱歉, 知识库中未找到与您问题相关的内容, 请尝试换一种问法或联系管理员补充文档。");
        }

        // 5. 计算分数统计
        double maxScore = results.stream().mapToDouble(RagRetrievalService.RetrievalResult::score).max().orElse(0.0);
        double minScore = results.stream().mapToDouble(RagRetrievalService.RetrievalResult::score).min().orElse(0.0);
        double avgScore = results.stream().mapToDouble(RagRetrievalService.RetrievalResult::score).average().orElse(0.0);

        // 6. 低置信度拒答: 最高分 < REFUSE_THRESHOLD
        if (maxScore < REFUSE_THRESHOLD) {
            return recordRefusedAnswerWithScores(request, kb, startMs, KbConversationLog.REFUSE_LOW_CONFIDENCE,
                    "抱歉, 知识库中未找到与您问题高度相关的内容 (最高相关度 " + String.format("%.2f%%", maxScore * 100) + "), 请尝试换一种问法或联系管理员补充文档。",
                    results.size(), maxScore, minScore, avgScore, results);
        }

        // 7. 生成答案: 拼接 top N 分块文本
        List<RagRetrievalService.RetrievalResult> topResults = results.stream()
                .sorted(Comparator.comparingDouble(RagRetrievalService.RetrievalResult::score).reversed())
                .limit(ANSWER_CHUNK_LIMIT)
                .toList();
        StringBuilder answer = new StringBuilder("根据知识库内容:\n\n");
        for (int i = 0; i < topResults.size(); i++) {
            RagRetrievalService.RetrievalResult r = topResults.get(i);
            String chunkText = r.chunkText();
            if (chunkText != null && chunkText.length() > ANSWER_CHUNK_TEXT_LENGTH) {
                chunkText = chunkText.substring(0, ANSWER_CHUNK_TEXT_LENGTH) + "...";
            }
            answer.append("[").append(i + 1).append("] ").append(chunkText).append("\n\n");
        }

        // 8. 构造引用列表
        List<AskQuestionVO.Citation> citations = results.stream()
                .sorted(Comparator.comparingDouble(RagRetrievalService.RetrievalResult::score).reversed())
                .map(r -> new AskQuestionVO.Citation(
                        r.documentId(),
                        r.docTitle(),
                        r.chunkId(),
                        r.sectionPath(),
                        r.sourceType(),
                        r.score(),
                        truncate(r.chunkText(), CITATION_PREVIEW_LENGTH)))
                .toList();

        long latencyMs = System.currentTimeMillis() - startMs;
        String conversationNo = generateConversationNo();

        // 9. 记录问答日志
        KbConversationLog logEntry = new KbConversationLog();
        logEntry.setId(IdGenerator.nextId());
        logEntry.setConversationNo(conversationNo);
        logEntry.setKbId(request.kbId());
        logEntry.setUserId(userId);
        logEntry.setQuestion(request.question());
        logEntry.setAnswer(answer.toString());
        logEntry.setHitChunkCount(results.size());
        logEntry.setMaxScore(maxScore);
        logEntry.setMinScore(minScore);
        logEntry.setAvgScore(avgScore);
        logEntry.setIsRefused(false);
        logEntry.setRefuseReason(null);
        logEntry.setCitedDocuments(toJson(citations));
        logEntry.setCitedChunkIds(toJson(results.stream().map(RagRetrievalService.RetrievalResult::chunkId).toList()));
        logEntry.setLatencyMs(latencyMs);
        conversationLogMapper.insert(logEntry);

        log.info("askQuestion: kb={} conv={} hits={} maxScore={} latency={}ms",
                kb.getKbCode(), conversationNo, results.size(), String.format("%.4f", maxScore), latencyMs);

        return new AskQuestionVO(
                conversationNo,
                request.question(),
                answer.toString(),
                false,
                null,
                results.size(),
                maxScore,
                minScore,
                avgScore,
                latencyMs,
                citations
        );
    }

    /**
     * 知识库运营统计。5 项核心指标: 文档数/分块数/今日问答/命中率/平均分。
     */
    public KbStatsVO getStats(String kbId) {
        AiKnowledgeBase kb = kbMapper.selectById(kbId);
        if (kb == null) {
            throw new ResourceNotFoundException(ErrorCode.KB_NOT_FOUND);
        }

        // 文档数 (ACTIVE 状态)
        long documentCount = documentMapper.selectCount(new LambdaQueryWrapper<AiDocument>()
                .eq(AiDocument::getKbId, kbId)
                .eq(AiDocument::getDocumentStatus, AiDocument.STATUS_ACTIVE));
        // 分块数
        long chunkCount = chunkMapper.selectCount(new LambdaQueryWrapper<AiDocumentChunk>()
                .eq(AiDocumentChunk::getKnowledgeBaseId, kbId));
        // 向量数
        List<AiDocumentChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<AiDocumentChunk>()
                .eq(AiDocumentChunk::getKnowledgeBaseId, kbId));
        long embeddingCount = 0;
        if (!chunks.isEmpty()) {
            List<String> chunkIds = chunks.stream().map(AiDocumentChunk::getId).toList();
            embeddingCount = embeddingMapper.selectCount(new LambdaQueryWrapper<AiEmbedding>()
                    .in(AiEmbedding::getChunkId, chunkIds));
        }

        // 今日问答统计
        OffsetDateTime todayStart = LocalDate.now().atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
        OffsetDateTime now = OffsetDateTime.now();
        List<KbConversationLog> todayConversations = conversationLogMapper.selectList(new LambdaQueryWrapper<KbConversationLog>()
                .eq(KbConversationLog::getKbId, kbId)
                .ge(KbConversationLog::getCreatedTime, todayStart)
                .le(KbConversationLog::getCreatedTime, now));
        long todayConvCount = todayConversations.size();
        long todayHitCount = todayConversations.stream().filter(c -> !Boolean.TRUE.equals(c.getIsRefused())).count();
        long todayRefusedCount = todayConversations.stream().filter(c -> Boolean.TRUE.equals(c.getIsRefused())).count();
        double todayHitRate = todayConvCount > 0 ? (double) todayHitCount / todayConvCount : 0.0;
        Double todayAvgMaxScore = todayConversations.stream()
                .filter(c -> c.getMaxScore() != null)
                .mapToDouble(KbConversationLog::getMaxScore)
                .average().orElse(0.0);
        if (todayConversations.stream().noneMatch(c -> c.getMaxScore() != null)) {
            todayAvgMaxScore = null;
        }
        Long todayAvgLatencyMs = todayConversations.stream()
                .filter(c -> c.getLatencyMs() != null)
                .mapToLong(KbConversationLog::getLatencyMs)
                .average().stream().findFirst().isPresent()
                ? (long) todayConversations.stream().filter(c -> c.getLatencyMs() != null).mapToLong(KbConversationLog::getLatencyMs).average().orElse(0.0)
                : null;

        // 历史总数
        long totalConvCount = conversationLogMapper.selectCount(new LambdaQueryWrapper<KbConversationLog>()
                .eq(KbConversationLog::getKbId, kbId));
        long totalHitCount = conversationLogMapper.selectCount(new LambdaQueryWrapper<KbConversationLog>()
                .eq(KbConversationLog::getKbId, kbId)
                .eq(KbConversationLog::getIsRefused, false));
        long totalRefusedCount = conversationLogMapper.selectCount(new LambdaQueryWrapper<KbConversationLog>()
                .eq(KbConversationLog::getKbId, kbId)
                .eq(KbConversationLog::getIsRefused, true));

        return new KbStatsVO(
                kbId, kb.getKbName(), kb.getStatus(),
                documentCount, chunkCount, embeddingCount,
                todayConvCount, todayHitCount, todayRefusedCount,
                todayHitRate, todayAvgMaxScore, todayAvgLatencyMs,
                totalConvCount, totalHitCount, totalRefusedCount
        );
    }

    /**
     * 分页查询问答历史。
     */
    public PageResult<KbConversationLog> pageConversations(PageRequest request, String kbId, String userId, Boolean refused) {
        DataScope scope = dataScopeResolver.resolve(RESOURCE_CODE);
        LambdaQueryWrapper<KbConversationLog> wrapper = new LambdaQueryWrapper<KbConversationLog>()
                .eq(KbConversationLog::getTenantId, CurrentUserContext.getTenantId())
                .eq(kbId != null && !kbId.isBlank(), KbConversationLog::getKbId, kbId)
                .eq(userId != null && !userId.isBlank(), KbConversationLog::getUserId, userId)
                .eq(refused != null, KbConversationLog::getIsRefused, refused)
                .orderByDesc(KbConversationLog::getCreatedTime);
        applyDataScope(wrapper, scope);
        Page<KbConversationLog> page = conversationLogMapper.selectPage(
                new Page<>(request.page(), request.size()), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), request.page(), request.size());
    }

    private void applyDataScope(LambdaQueryWrapper<KbConversationLog> wrapper, DataScope scope) {
        if (scope == null) return;
        if (scope.scopeType() == DataScopeType.ALL || scope.scopeType() == DataScopeType.TENANT) return;
        String userId = scope.userId();
        if (userId == null || userId.isBlank()) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.eq(KbConversationLog::getCreatedBy, userId);
    }

    /**
     * 查询问答详情。
     */
    public KbConversationLog getConversation(String id) {
        KbConversationLog log = conversationLogMapper.selectById(id);
        if (log == null) {
            throw new ResourceNotFoundException(ErrorCode.KB_CONVERSATION_NOT_FOUND);
        }
        return log;
    }

    /**
     * 重新索引文档 (权限变更/内容更新后触发)。
     * <p>
     * 实现: 查询文档下所有 chunk → 对每个 chunk 重新生成向量 → 通过 VectorRepository 写入 pgvector。
     * 用于 V015 种子数据向量占位修复、文档内容更新后向量化重生成。
     */
    @Transactional
    public AiDocument reindexDocument(String docId) {
        AiDocument doc = documentIngestService.getDocument(docId);

        // 查询该文档下所有未删除 chunk
        List<AiDocumentChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<AiDocumentChunk>()
                .eq(AiDocumentChunk::getDocumentId, docId));
        if (chunks.isEmpty()) {
            log.warn("reindexDocument: docId={} no chunks found", docId);
            return doc;
        }

        int reindexed = 0;
        for (AiDocumentChunk chunk : chunks) {
            // 查找对应的 embedding 记录
            AiEmbedding embedding = embeddingMapper.selectOne(new LambdaQueryWrapper<AiEmbedding>()
                    .eq(AiEmbedding::getChunkId, chunk.getId())
                    .last("LIMIT 1"));
            if (embedding == null) {
                log.warn("reindexDocument: chunkId={} embedding record missing, skip", chunk.getId());
                continue;
            }
            // 重新生成向量并写入 pgvector
            float[] vector = embeddingService.embed(chunk.getChunkText());
            String vectorPgString = embeddingService.toPgVectorFormat(vector);
            vectorRepository.insertVector(embedding.getId(), vectorPgString);
            reindexed++;
        }

        log.info("reindexDocument: docId={} status={} chunkCount={} reindexed={}",
                docId, doc.getDocumentStatus(), doc.getChunkCount(), reindexed);
        return doc;
    }

    // ==================== 私有方法 ====================

    private AskQuestionVO recordRefusedAnswer(AskQuestionRequest request, AiKnowledgeBase kb,
                                              long startMs, String refuseReason, String answer) {
        return recordRefusedAnswerWithScores(request, kb, startMs, refuseReason, answer,
                0, 0.0, 0.0, 0.0, List.of());
    }

    private AskQuestionVO recordRefusedAnswerWithScores(AskQuestionRequest request, AiKnowledgeBase kb,
                                                        long startMs, String refuseReason, String answer,
                                                        int hitCount, double maxScore, double minScore, double avgScore,
                                                        List<RagRetrievalService.RetrievalResult> results) {
        long latencyMs = System.currentTimeMillis() - startMs;
        String conversationNo = generateConversationNo();

        KbConversationLog logEntry = new KbConversationLog();
        logEntry.setId(IdGenerator.nextId());
        logEntry.setConversationNo(conversationNo);
        logEntry.setKbId(request.kbId());
        logEntry.setUserId(CurrentUserContext.getUserId());
        logEntry.setQuestion(request.question());
        logEntry.setAnswer(answer);
        logEntry.setHitChunkCount(hitCount);
        logEntry.setMaxScore(hitCount > 0 ? maxScore : null);
        logEntry.setMinScore(hitCount > 0 ? minScore : null);
        logEntry.setAvgScore(hitCount > 0 ? avgScore : null);
        logEntry.setIsRefused(true);
        logEntry.setRefuseReason(refuseReason);
        logEntry.setCitedDocuments("[]");
        logEntry.setCitedChunkIds("[]");
        logEntry.setLatencyMs(latencyMs);
        conversationLogMapper.insert(logEntry);

        log.info("askQuestion refused: kb={} conv={} reason={} latency={}ms",
                kb.getKbCode(), conversationNo, refuseReason, latencyMs);

        List<AskQuestionVO.Citation> citations = results.stream()
                .map(r -> new AskQuestionVO.Citation(
                        r.documentId(), r.docTitle(), r.chunkId(), r.sectionPath(),
                        r.sourceType(), r.score(), truncate(r.chunkText(), CITATION_PREVIEW_LENGTH)))
                .toList();

        return new AskQuestionVO(
                conversationNo, request.question(), answer,
                true, refuseReason,
                hitCount, maxScore, minScore, avgScore,
                latencyMs, citations
        );
    }

    private String generateConversationNo() {
        return "KB-CONV-" + OffsetDateTime.now().format(CONV_NO_FORMATTER) + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("toJson failed: {}", e.getMessage());
            return "[]";
        }
    }
}
