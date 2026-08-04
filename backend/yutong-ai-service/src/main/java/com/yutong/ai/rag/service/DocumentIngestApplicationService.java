package com.yutong.ai.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.ai.rag.domain.AiDocument;
import com.yutong.ai.rag.domain.AiDocumentChunk;
import com.yutong.ai.rag.domain.AiEmbedding;
import com.yutong.ai.rag.domain.AiKnowledgeBase;
import com.yutong.ai.rag.mapper.AiDocumentChunkMapper;
import com.yutong.ai.rag.mapper.AiDocumentMapper;
import com.yutong.ai.rag.mapper.AiEmbeddingMapper;
import com.yutong.ai.rag.mapper.AiKnowledgeBaseMapper;
import com.yutong.ai.rag.repository.VectorRepository;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.exception.BusinessConflictException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.id.IdGenerator;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 文档入库应用服务。设计来源: 13-AI能力设计 GA 基础包采用受控管理面入库。
 * <p>
 * 受控入库: 创建文档记录 + 分块 + 向量入库。
 * v0.9: 集成 EmbeddingService（本地哈希向量）+ VectorRepository（pgvector 原生 SQL），
 * 向量字段真实写入 ai_embedding.embedding vector(1536) 列。
 */
@Service
public class DocumentIngestApplicationService {

    private static final String DEFAULT_EMBEDDING_MODEL = "local-hash-bow-1536";
    private static final int EMBEDDING_DIMENSION = 1536;

    private final AiDocumentMapper documentMapper;
    private final AiDocumentChunkMapper chunkMapper;
    private final AiEmbeddingMapper embeddingMapper;
    private final AiKnowledgeBaseMapper kbMapper;
    private final RagChunkService ragChunkService;
    private final EmbeddingService embeddingService;
    private final VectorRepository vectorRepository;

    public DocumentIngestApplicationService(AiDocumentMapper documentMapper,
                                            AiDocumentChunkMapper chunkMapper,
                                            AiEmbeddingMapper embeddingMapper,
                                            AiKnowledgeBaseMapper kbMapper,
                                            RagChunkService ragChunkService,
                                            EmbeddingService embeddingService,
                                            VectorRepository vectorRepository) {
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.embeddingMapper = embeddingMapper;
        this.kbMapper = kbMapper;
        this.ragChunkService = ragChunkService;
        this.embeddingService = embeddingService;
        this.vectorRepository = vectorRepository;
    }

    /**
     * 受控入库: 创建文档记录 + 分块 + 真实向量写入。
     *
     * @param kbId             知识库 ID
     * @param docTitle         文档标题
     * @param sourceType       源类型
     * @param sourceUri        源地址
     * @param content          文档正文
     * @param visibility       可见性
     * @param sensitivityLevel 敏感等级
     * @param permissionCode   权限码
     * @return 入库后的文档记录
     */
    @Transactional
    public AiDocument ingest(String kbId, String docTitle, String sourceType,
                             String sourceUri, String content, String visibility,
                             String sensitivityLevel, String permissionCode) {
        // 1. 校验知识库存在且为 ACTIVE
        AiKnowledgeBase kb = kbMapper.selectById(kbId);
        if (kb == null) {
            throw new ResourceNotFoundException("知识库不存在: " + kbId);
        }
        if (!AiKnowledgeBase.STATUS_ACTIVE.equals(kb.getStatus())) {
            throw new BusinessConflictException(
                    "知识库状态[" + kb.getStatus() + "]不可入库，仅 ACTIVE 可入库");
        }

        // 2. 创建 AiDocument (status=PARSING)
        AiDocument document = new AiDocument();
        document.setId(IdGenerator.nextId());
        document.setTenantId(CurrentUserContext.getTenantId());
        document.setKbId(kbId);
        document.setDocTitle(docTitle);
        document.setSourceType(sourceType);
        document.setSourceUri(sourceUri);
        document.setVisibility(visibility);
        document.setSensitivityLevel(sensitivityLevel);
        document.setPermissionCode(permissionCode);
        document.setDocumentStatus(AiDocument.STATUS_PARSING);
        document.setChunkCount(0);
        document.setCreatedBy(CurrentUserContext.getUserId());
        documentMapper.insert(document);

        // 3. 分块 content
        List<String> chunkTexts = ragChunkService.splitIntoChunks(content);

        // 4. 状态变为 INDEXING
        document.setDocumentStatus(AiDocument.STATUS_INDEXING);
        documentMapper.updateById(document);

        // 5. 为每个 chunk 创建 AiDocumentChunk (chunk_hash 去重，已存在跳过)
        // 6. 为每个 chunk 创建 AiEmbedding 占位（不调用真实 embedding API）
        String embeddingModel = kb.getEmbeddingModel() != null && !kb.getEmbeddingModel().isBlank()
                ? kb.getEmbeddingModel() : DEFAULT_EMBEDDING_MODEL;
        int validChunkCount = 0;
        int chunkNo = 0;
        for (String chunkText : chunkTexts) {
            chunkNo++;
            String chunkHash = ragChunkService.computeChunkHash(chunkText);
            // 去重: 同一文档下已存在相同 hash 的分块则跳过
            Long existCount = chunkMapper.selectCount(new LambdaQueryWrapper<AiDocumentChunk>()
                    .eq(AiDocumentChunk::getDocumentId, document.getId())
                    .eq(AiDocumentChunk::getChunkHash, chunkHash));
            if (existCount != null && existCount > 0) {
                continue;
            }
            AiDocumentChunk chunk = new AiDocumentChunk();
            chunk.setId(IdGenerator.nextId());
            chunk.setTenantId(CurrentUserContext.getTenantId());
            chunk.setKnowledgeBaseId(kbId);
            chunk.setDocumentId(document.getId());
            chunk.setChunkNo(chunkNo);
            chunk.setChunkText(chunkText);
            chunk.setChunkHash(chunkHash);
            // tokenCount 简化估算: 取字符数
            chunk.setTokenCount(chunkText.length());
            chunk.setPermissionCode(permissionCode);
            chunk.setSensitivityLevel(sensitivityLevel);
            chunk.setCreatedBy(CurrentUserContext.getUserId());
            chunkMapper.insert(chunk);

            // v0.9: 真实向量入库 — EmbeddingService 生成向量 + VectorRepository 写入 pgvector
            AiEmbedding embedding = new AiEmbedding();
            embedding.setId(IdGenerator.nextId());
            embedding.setTenantId(CurrentUserContext.getTenantId());
            embedding.setChunkId(chunk.getId());
            embedding.setEmbeddingModel(embeddingModel);
            embedding.setEmbeddingDimension(EMBEDDING_DIMENSION);
            embedding.setEmbeddingHash(chunkHash);
            embedding.setCreatedBy(CurrentUserContext.getUserId());
            embeddingMapper.insert(embedding);

            // 生成向量并通过原生 SQL 写入 pgvector vector(1536) 列
            float[] vector = embeddingService.embed(chunkText);
            String vectorPgString = embeddingService.toPgVectorFormat(vector);
            vectorRepository.insertVector(embedding.getId(), vectorPgString);
            validChunkCount++;
        }

        // 7. 更新 document.chunkCount、documentStatus=ACTIVE、indexedTime=now
        document.setChunkCount(validChunkCount);
        document.setDocumentStatus(AiDocument.STATUS_ACTIVE);
        document.setIndexedTime(OffsetDateTime.now());
        document.setUpdatedBy(CurrentUserContext.getUserId());
        documentMapper.updateById(document);

        return document;
    }

    /**
     * 分页查询文档。
     *
     * @param kbId           知识库 ID，可为空
     * @param docTitle       文档标题（模糊匹配），可为空
     * @param documentStatus 文档状态，可为空
     */
    public PageResult<AiDocument> pageDocuments(PageRequest request, String kbId, String docTitle, String documentStatus) {
        LambdaQueryWrapper<AiDocument> wrapper = new LambdaQueryWrapper<AiDocument>()
                .eq(AiDocument::getTenantId, CurrentUserContext.getTenantId())
                .eq(kbId != null && !kbId.isBlank(), AiDocument::getKbId, kbId)
                .like(docTitle != null && !docTitle.isBlank(), AiDocument::getDocTitle, docTitle)
                .eq(documentStatus != null && !documentStatus.isBlank(), AiDocument::getDocumentStatus, documentStatus)
                .orderByDesc(AiDocument::getCreatedTime);
        Page<AiDocument> page = documentMapper.selectPage(
                new Page<>(request.page(), request.size()), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), request.page(), request.size());
    }

    /**
     * 查询文档详情。不存在抛 ResourceNotFoundException。
     */
    public AiDocument getDocument(String id) {
        AiDocument document = documentMapper.selectById(id);
        if (document == null) {
            throw new ResourceNotFoundException("文档不存在: " + id);
        }
        return document;
    }

    /**
     * 标记文档失效（权限变更/版本废弃时调用）。
     * ACTIVE → STALE，宁可拒答也不能召回已失效权限内容。
     */
    @Transactional
    public AiDocument markStale(String id, Integer version) {
        AiDocument document = getDocument(id);
        checkVersion(version, document.getVersion());
        if (!AiDocument.STATUS_ACTIVE.equals(document.getDocumentStatus())) {
            throw new BusinessConflictException(
                    "文档当前状态[" + document.getDocumentStatus() + "]不可标记失效，仅 ACTIVE 可标记");
        }
        document.setDocumentStatus(AiDocument.STATUS_STALE);
        document.setUpdatedBy(CurrentUserContext.getUserId());
        int rows = documentMapper.updateById(document);
        if (rows == 0) {
            throw new BusinessConflictException("数据已被他人修改，请刷新后重试");
        }
        return document;
    }

    private void checkVersion(Integer requestVersion, Integer currentVersion) {
        if (requestVersion == null || !requestVersion.equals(currentVersion)) {
            throw new BusinessConflictException(
                    "版本号不匹配，请求版本=" + requestVersion + ", 当前版本=" + currentVersion);
        }
    }
}
