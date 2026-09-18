package com.yutong.ai.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.ai.rag.domain.AiDocument;
import com.yutong.ai.rag.domain.AiDocumentChunk;
import com.yutong.ai.rag.domain.AiEmbedding;
import com.yutong.ai.rag.domain.AiKnowledgeBase;
import com.yutong.ai.rag.loader.DocumentExtractException;
import com.yutong.ai.rag.loader.DocumentLoaderFactory;
import com.yutong.ai.rag.mapper.AiDocumentChunkMapper;
import com.yutong.ai.rag.mapper.AiDocumentMapper;
import com.yutong.ai.rag.mapper.AiEmbeddingMapper;
import com.yutong.ai.rag.mapper.AiKnowledgeBaseMapper;
import com.yutong.ai.rag.repository.VectorRepository;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.exception.BusinessConflictException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.id.IdGenerator;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.system.file.domain.SysFile;
import com.yutong.system.file.mapper.SysFileMapper;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 文档入库应用服务。设计来源: 13-AI能力设计 GA 基础包采用受控管理面入库。
 * <p>
 * 受控入库: 创建文档记录 + 分块 + 向量入库。
 * v0.9: 集成 EmbeddingService（本地哈希向量）+ VectorRepository（pgvector 原生 SQL），
 * 向量字段真实写入 ai_embedding.embedding vector(1536) 列。
 * <p>
 * P0-1 RAG loader parity: 新增基于 MinIO fileId 的真文档解析入库能力，
 * 支持 pdf/docx/xlsx/csv/md/txt 6 种装载器，复用现有分片与向量化链路。
 * MinIO 流式抽取 → DocumentLoaderFactory 路由 → 分片 → embedding。
 */
@Service
public class DocumentIngestApplicationService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestApplicationService.class);

    private static final String DEFAULT_EMBEDDING_MODEL = "local-hash-bow-1536";
    private static final int EMBEDDING_DIMENSION = 1536;

    private final AiDocumentMapper documentMapper;
    private final AiDocumentChunkMapper chunkMapper;
    private final AiEmbeddingMapper embeddingMapper;
    private final AiKnowledgeBaseMapper kbMapper;
    private final RagChunkService ragChunkService;
    private final EmbeddingService embeddingService;
    private final VectorRepository vectorRepository;

    // P0-1 新增依赖
    private final DocumentLoaderFactory loaderFactory;
    private final SysFileMapper sysFileMapper;
    private final MinioClient minioClient;
    private final String bucket;

    public DocumentIngestApplicationService(AiDocumentMapper documentMapper,
                                            AiDocumentChunkMapper chunkMapper,
                                            AiEmbeddingMapper embeddingMapper,
                                            AiKnowledgeBaseMapper kbMapper,
                                            RagChunkService ragChunkService,
                                            EmbeddingService embeddingService,
                                            VectorRepository vectorRepository,
                                            DocumentLoaderFactory loaderFactory,
                                            SysFileMapper sysFileMapper,
                                            MinioClient minioClient,
                                            @Qualifier("minioBucketName") String bucket) {
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.embeddingMapper = embeddingMapper;
        this.kbMapper = kbMapper;
        this.ragChunkService = ragChunkService;
        this.embeddingService = embeddingService;
        this.vectorRepository = vectorRepository;
        this.loaderFactory = loaderFactory;
        this.sysFileMapper = sysFileMapper;
        this.minioClient = minioClient;
        this.bucket = bucket;
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
        return ingestInternal(kbId, docTitle, sourceType, sourceUri, content, visibility,
                sensitivityLevel, permissionCode, null, null);
    }

    /**
     * P0-1 新增: 基于文件入库 — 根据 fileId 从 MinIO 拉取原文件，按 loaderType/后缀自动路由抽取文本。
     *
     * @param kbId             知识库 ID
     * @param docTitle         文档标题（为空则取文件名）
     * @param fileId           文件 ID (sys_file.id)
     * @param loaderType       装载器类型覆盖（可为空，按后缀自动推断）
     * @param sourceType       源类型
     * @param visibility       可见性
     * @param sensitivityLevel 敏感等级
     * @param permissionCode   权限码
     * @return 入库后的文档记录
     */
    @Transactional
    public AiDocument ingestFromFile(String kbId, String docTitle, String fileId, String loaderType,
                                     String sourceType, String visibility,
                                     String sensitivityLevel, String permissionCode) {
        if (fileId == null || fileId.isBlank()) {
            throw new BusinessException(ErrorCode.KB_REQUEST_INVALID, "fileId 不能为空");
        }
        // 1. 校验知识库存在且为 ACTIVE
        AiKnowledgeBase kb = kbMapper.selectById(kbId);
        if (kb == null) {
            throw new ResourceNotFoundException("知识库不存在: " + kbId);
        }
        if (!AiKnowledgeBase.STATUS_ACTIVE.equals(kb.getStatus())) {
            throw new BusinessConflictException(
                    "知识库状态[" + kb.getStatus() + "]不可入库，仅 ACTIVE 可入库");
        }

        // 2. 查询文件元数据（跨租户防护在 SysFile 层已有，此处再校验 tenant）
        SysFile sysFile = sysFileMapper.selectById(fileId);
        if (sysFile == null) {
            throw new BusinessException(ErrorCode.KB_FILE_NOT_FOUND, "关联文件不存在: " + fileId);
        }
        String currentTenant = CurrentUserContext.getTenantId();
        if (currentTenant != null && !currentTenant.equals(sysFile.getTenantId())) {
            throw new ResourceNotFoundException("文件不存在: " + fileId);
        }
        if (!"SUCCESS".equals(sysFile.getUploadStatus())) {
            throw new BusinessException(ErrorCode.KB_FILE_NOT_FOUND, "文件未上传成功，无法解析: " + fileId);
        }

        String filename = sysFile.getFileName();
        String effectiveLoaderType = (loaderType != null && !loaderType.isBlank()) ? loaderType : sysFile.getFileExt();
        // 文件名回退标题
        String effectiveTitle = (docTitle != null && !docTitle.isBlank()) ? docTitle : filename;
        String effectiveSourceType = (sourceType != null && !sourceType.isBlank()) ? sourceType : AiDocument.SOURCE_DESIGN_DOC;

        // 3. 创建 AiDocument (PARSING)，记录 fileId 与 loader_type
        AiDocument document = new AiDocument();
        document.setId(IdGenerator.nextId());
        document.setTenantId(CurrentUserContext.getTenantId());
        document.setKbId(kbId);
        document.setFileId(fileId);
        document.setDocTitle(effectiveTitle);
        document.setSourceType(effectiveSourceType);
        document.setSourceUri("/files/" + fileId);
        document.setVisibility(visibility);
        document.setSensitivityLevel(sensitivityLevel);
        document.setPermissionCode(permissionCode);
        document.setDocumentStatus(AiDocument.STATUS_PARSING);
        document.setChunkCount(0);
        document.setLoaderType(effectiveLoaderType);
        document.setCreatedBy(CurrentUserContext.getUserId());
        documentMapper.insert(document);

        String content;
        try {
            content = extractTextFromMinio(sysFile, effectiveLoaderType);
        } catch (DocumentExtractException dee) {
            // 解析失败 → 标记 FAILED，记录 errorMessage，抛业务异常（保留文档记录供排查）
            String errMsg = dee.getMessage();
            if (errMsg != null && errMsg.length() > 900) {
                errMsg = errMsg.substring(0, 900);
            }
            document.setDocumentStatus(AiDocument.STATUS_FAILED);
            document.setErrorMessage(errMsg);
            document.setUpdatedBy(CurrentUserContext.getUserId());
            documentMapper.updateById(document);
            log.error("文档解析失败 documentId={}, fileId={}, fileName={}, loaderType={}, error={}",
                    document.getId(), fileId, filename, effectiveLoaderType, dee.getMessage(), dee);
            // 按 ErrorCode 抛对应异常（DocumentExtractException 已是 BusinessException，直接透传）
            throw dee;
        } catch (Exception e) {
            String errMsg = "文档解析异常: " + e.getMessage();
            if (errMsg.length() > 900) {
                errMsg = errMsg.substring(0, 900);
            }
            document.setDocumentStatus(AiDocument.STATUS_FAILED);
            document.setErrorMessage(errMsg);
            document.setUpdatedBy(CurrentUserContext.getUserId());
            documentMapper.updateById(document);
            log.error("文档解析异常 documentId={}, fileId={}, fileName={}, loaderType={}",
                    document.getId(), fileId, filename, effectiveLoaderType, e);
            throw new BusinessException(ErrorCode.KB_DOCUMENT_PARSE_FAILED, errMsg, e);
        }

        if (content == null || content.isBlank()) {
            document.setDocumentStatus(AiDocument.STATUS_FAILED);
            document.setErrorMessage("文档内容为空，无法入库");
            document.setUpdatedBy(CurrentUserContext.getUserId());
            documentMapper.updateById(document);
            throw new BusinessException(ErrorCode.KB_DOCUMENT_EMPTY, "文档内容为空，无法入库: " + filename);
        }

        // 4. 状态 → INDEXING
        document.setDocumentStatus(AiDocument.STATUS_INDEXING);
        documentMapper.updateById(document);

        // 5. 复用分块与向量化流水线
        int validChunkCount = doChunkAndEmbed(kb, document, content, permissionCode, sensitivityLevel);

        // 6. 更新 ACTIVE
        document.setChunkCount(validChunkCount);
        document.setDocumentStatus(AiDocument.STATUS_ACTIVE);
        document.setIndexedTime(OffsetDateTime.now());
        document.setUpdatedBy(CurrentUserContext.getUserId());
        documentMapper.updateById(document);

        log.info("文档文件入库成功 documentId={}, fileId={}, chunks={}, chars={}",
                document.getId(), fileId, validChunkCount, content.length());
        return document;
    }

    // ============= 私有流水线 =============

    private AiDocument ingestInternal(String kbId, String docTitle, String sourceType,
                                      String sourceUri, String content, String visibility,
                                      String sensitivityLevel, String permissionCode,
                                      String fileId, String loaderType) {
        AiKnowledgeBase kb = kbMapper.selectById(kbId);
        if (kb == null) {
            throw new ResourceNotFoundException("知识库不存在: " + kbId);
        }
        if (!AiKnowledgeBase.STATUS_ACTIVE.equals(kb.getStatus())) {
            throw new BusinessConflictException(
                    "知识库状态[" + kb.getStatus() + "]不可入库，仅 ACTIVE 可入库");
        }
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.KB_DOCUMENT_EMPTY, "文档内容不能为空");
        }
        AiDocument document = new AiDocument();
        document.setId(IdGenerator.nextId());
        document.setTenantId(CurrentUserContext.getTenantId());
        document.setKbId(kbId);
        document.setFileId(fileId);
        document.setDocTitle(docTitle);
        document.setSourceType(sourceType);
        document.setSourceUri(sourceUri);
        document.setVisibility(visibility);
        document.setSensitivityLevel(sensitivityLevel);
        document.setPermissionCode(permissionCode);
        document.setDocumentStatus(AiDocument.STATUS_PARSING);
        document.setChunkCount(0);
        document.setLoaderType(loaderType);
        document.setCreatedBy(CurrentUserContext.getUserId());
        documentMapper.insert(document);

        document.setDocumentStatus(AiDocument.STATUS_INDEXING);
        documentMapper.updateById(document);

        int validChunkCount = doChunkAndEmbed(kb, document, content, permissionCode, sensitivityLevel);

        document.setChunkCount(validChunkCount);
        document.setDocumentStatus(AiDocument.STATUS_ACTIVE);
        document.setIndexedTime(OffsetDateTime.now());
        document.setUpdatedBy(CurrentUserContext.getUserId());
        documentMapper.updateById(document);
        return document;
    }

    private int doChunkAndEmbed(AiKnowledgeBase kb, AiDocument document, String content,
                                String permissionCode, String sensitivityLevel) {
        List<String> chunkTexts = ragChunkService.splitIntoChunks(content);
        String embeddingModel = kb.getEmbeddingModel() != null && !kb.getEmbeddingModel().isBlank()
                ? kb.getEmbeddingModel() : DEFAULT_EMBEDDING_MODEL;
        int validChunkCount = 0;
        int chunkNo = 0;
        for (String chunkText : chunkTexts) {
            chunkNo++;
            String chunkHash = ragChunkService.computeChunkHash(chunkText);
            Long existCount = chunkMapper.selectCount(new LambdaQueryWrapper<AiDocumentChunk>()
                    .eq(AiDocumentChunk::getDocumentId, document.getId())
                    .eq(AiDocumentChunk::getChunkHash, chunkHash));
            if (existCount != null && existCount > 0) {
                continue;
            }
            AiDocumentChunk chunk = new AiDocumentChunk();
            chunk.setId(IdGenerator.nextId());
            chunk.setTenantId(CurrentUserContext.getTenantId());
            chunk.setKnowledgeBaseId(document.getKbId());
            chunk.setDocumentId(document.getId());
            chunk.setChunkNo(chunkNo);
            chunk.setChunkText(chunkText);
            chunk.setChunkHash(chunkHash);
            chunk.setTokenCount(chunkText.length());
            chunk.setPermissionCode(permissionCode);
            chunk.setSensitivityLevel(sensitivityLevel);
            chunk.setCreatedBy(CurrentUserContext.getUserId());
            chunkMapper.insert(chunk);

            AiEmbedding embedding = new AiEmbedding();
            embedding.setId(IdGenerator.nextId());
            embedding.setTenantId(CurrentUserContext.getTenantId());
            embedding.setChunkId(chunk.getId());
            embedding.setEmbeddingModel(embeddingModel);
            embedding.setEmbeddingDimension(EMBEDDING_DIMENSION);
            embedding.setEmbeddingHash(chunkHash);
            embedding.setModality("text");
            embedding.setCreatedBy(CurrentUserContext.getUserId());
            embeddingMapper.insert(embedding);

            float[] vector = embeddingService.embed(chunkText, kb);
            String vectorPgString = embeddingService.toPgVectorFormat(vector);
            vectorRepository.insertVector(embedding.getId(), vectorPgString);
            validChunkCount++;
        }
        return validChunkCount;
    }

    /**
     * 从 MinIO 拉取对象并通过工厂抽取文本。
     * 生产级: 流式读取，异常分类，日志脱敏（不打印文件内容）。
     */
    private String extractTextFromMinio(SysFile sysFile, String loaderTypeHint) {
        String fileKey = sysFile.getFileKey();
        String filename = sysFile.getFileName();
        log.info("开始抽取文件 fileId={}, fileKey={}, filename={}, loaderTypeHint={}, bucket={}",
                sysFile.getId(), fileKey, filename, loaderTypeHint, bucket);
        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucket).object(fileKey).build())) {
            if (stream == null) {
                throw new BusinessException(ErrorCode.KB_FILE_NOT_FOUND, "无法读取文件流: " + filename);
            }
            String text = loaderFactory.extract(stream, filename, loaderTypeHint);
            if (text == null) {
                text = "";
            }
            // 截断超大文本防止分块 OOM：当前限制 5MB 文本（约 500 万字符）
            if (text.length() > 5_000_000) {
                log.warn("文档文本超大已截断 fileId={}, originalChars={}, truncatedTo=5000000", sysFile.getId(), text.length());
                text = text.substring(0, 5_000_000) + "\n[truncated at 5,000,000 chars]";
            }
            return text;
        } catch (DocumentExtractException dee) {
            throw dee;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.KB_DOCUMENT_PARSE_FAILED,
                    "读取对象存储文件失败: " + filename + " — " + e.getMessage(), e);
        }
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
