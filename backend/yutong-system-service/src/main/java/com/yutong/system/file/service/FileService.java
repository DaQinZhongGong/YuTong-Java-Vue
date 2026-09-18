package com.yutong.system.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.auth.DataScopeResolver;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.auth.DataScope;
import com.yutong.common.auth.DataScopeType;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.id.IdGenerator;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.system.file.domain.BizFileRel;
import com.yutong.system.file.domain.SysFile;
import com.yutong.system.file.mapper.BizFileRelMapper;
import com.yutong.system.file.mapper.SysFileMapper;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 文件服务。设计来源: 17-平台基础能力、57-完整DDL清单、98-后端实现蓝图
 * 职责: MinIO 上传、文件元数据管理、业务对象绑定、预签名下载链接。
 *
 * 约束:
 * - 文件大小上限 100MB (与 application.yml multipart.max-file-size 对齐)
 * - fileKey 格式: {tenantId}/{yyyy/MM/dd}/{ULID}.{ext}，租户隔离 + 日期分目录
 * - 上传成功: upload_status=SUCCESS；上传失败: upload_status=FAILED (独立事务记录)
 * - MinIO 上传成功但 DB 写入失败时，需回滚清理已上传的临时对象
 * - 预签名下载链接有效期 30 分钟
 */
@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    /** 文件大小上限: 100MB */
    private static final long MAX_FILE_SIZE = 100L * 1024 * 1024;

    /** 预签名 URL 有效期: 30 分钟 */
    private static final int DOWNLOAD_URL_EXPIRY_MINUTES = 30;

    /** 允许上传的文件扩展名白名单。设计来源: 64-安全威胁模型 TC-SEC-FILE-001、34-安全合规专项设计 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            // 图片
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "ico",
            // 文档
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            // 文本
            "txt", "csv", "json", "xml", "md", "log",
            // 压缩包（第一版允许，后续可按需收紧）
            "zip", "tar", "gz",
            // 语音消息（移动端录音上传）
            "mp3", "wav", "m4a", "aac", "webm", "ogg"
    );

    /** 日期路径格式 */
    private static final DateTimeFormatter DATE_PATH_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private static final String STORAGE_TYPE_MINIO = "MINIO";
    private static final String UPLOAD_STATUS_SUCCESS = "SUCCESS";
    private static final String UPLOAD_STATUS_FAILED = "FAILED";

    /** 文件资源编码，对齐 permissions.yaml sys:file:* 命名。 */
    public static final String RESOURCE_CODE = "sys:file";

    private final SysFileMapper sysFileMapper;
    private final BizFileRelMapper bizFileRelMapper;
    private final MinioClient minioClient;
    private final String bucket;
    /** 自注入代理，用于在独立事务中记录失败上传 */
    private final FileService self;
    private final DataScopeResolver dataScopeResolver;

    public FileService(SysFileMapper sysFileMapper,
                       BizFileRelMapper bizFileRelMapper,
                       MinioClient minioClient,
                       @Qualifier("minioBucketName") String bucket,
                       @Lazy FileService self,
                       DataScopeResolver dataScopeResolver) {
        this.sysFileMapper = sysFileMapper;
        this.bizFileRelMapper = bizFileRelMapper;
        this.minioClient = minioClient;
        this.bucket = bucket;
        this.self = self;
        this.dataScopeResolver = dataScopeResolver;
    }

    // ==================== 上传 ====================

    /**
     * 上传文件到 MinIO 并写入元数据。
     * 流程: 校验 -> 生成 fileKey -> 上传 MinIO -> 写 sys_file(SUCCESS)。
     * MinIO 成功但 DB 失败时清理临时对象；MinIO 失败时以独立事务记录 FAILED。
     *
     * @param file 上传的文件
     * @return 文件元数据
     */
    @Transactional
    public SysFile upload(MultipartFile file) {
        validateFile(file);

        String fileName = file.getOriginalFilename();
        String contentType = StringUtils.hasText(file.getContentType()) ? file.getContentType() : "application/octet-stream";
        String fileExt = extractExtension(fileName);
        long fileSize = file.getSize();

        String tenantId = CurrentUserContext.getTenantId() != null ? CurrentUserContext.getTenantId() : "default";
        String userId = CurrentUserContext.getUserId() != null ? CurrentUserContext.getUserId() : "system";
        String ulid = IdGenerator.nextId();
        String datePath = LocalDate.now().format(DATE_PATH_FORMAT);
        String fileKey = buildFileKey(tenantId, datePath, ulid, fileExt);

        // 计算 SHA-256 校验和
        String checksum = computeSha256(file);

        // 尝试上传到 MinIO
        try {
            ensureBucketExists();
            try (InputStream uploadStream = file.getInputStream()) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(fileKey)
                        .stream(uploadStream, fileSize, -1)
                        .contentType(contentType)
                        .build());
            }
        } catch (Exception e) {
            // MinIO 上传失败: 以独立事务记录 FAILED，然后抛出异常
            SysFile failedRecord = buildSysFile(fileName, fileKey, fileSize, contentType, fileExt, checksum, UPLOAD_STATUS_FAILED, tenantId, userId);
            self.recordFailedUpload(failedRecord);
            throw new BusinessException(ErrorCode.SYS_INTERNAL_ERROR, "文件上传失败: " + fileName, e);
        }

        // MinIO 上传成功: 写入元数据
        SysFile sysFile = buildSysFile(fileName, fileKey, fileSize, contentType, fileExt, checksum, UPLOAD_STATUS_SUCCESS, tenantId, userId);
        try {
            sysFileMapper.insert(sysFile);
        } catch (Exception dbEx) {
            // DB 写入失败: 清理已上传的 MinIO 临时对象
            silentRemoveObject(fileKey);
            throw new BusinessException(ErrorCode.SYS_INTERNAL_ERROR, "文件元数据写入失败: " + fileName, dbEx);
        }

        return sysFile;
    }

    /**
     * 以独立事务记录上传失败的文件元数据。
     * 使用 REQUIRES_NEW 避免被外层事务回滚。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SysFile recordFailedUpload(SysFile record) {
        sysFileMapper.insert(record);
        return record;
    }

    /**
     * 上传内存字节内容到 MinIO (用于导入导出异步任务中上传生成的 CSV / 错误报告等)。
     * 内部包装为 {@link ByteArrayMultipartFile} 后复用 {@link #upload(MultipartFile)}。
     *
     * <p>设计来源: 52-后端服务分工与接口实现详设 — 导入导出异步任务的导出文件 / 错误报告
     * 在内存中生成，需上传至 MinIO 并记录 sys_file 元数据。
     *
     * @param fileName    原始文件名 (含扩展名，用于类型校验与 fileKey 生成)
     * @param content     文件字节内容
     * @param contentType MIME 类型 (为空时按 octet-stream 处理)
     * @return 文件元数据
     */
    @Transactional
    public SysFile uploadBytes(String fileName, byte[] content, String contentType) {
        String ct = StringUtils.hasText(contentType) ? contentType : "application/octet-stream";
        return upload(new ByteArrayMultipartFile("file", fileName, ct, content));
    }

    // ==================== 业务绑定 ====================

    /**
     * 将文件绑定到业务对象。
     * 校验: fileId 存在；同一 (bizType, bizId, fileId) 不可重复绑定。
     *
     * @param bizType 业务类型
     * @param bizId   业务对象 ID
     * @param fileId  文件 ID
     * @param relType 关系类型
     * @param sortNo  排序号 (null 默认 0)
     * @return 绑定记录
     */
    @Transactional
    public BizFileRel bind(String bizType, String bizId, String fileId, String relType, Integer sortNo) {
        // 校验文件存在
        SysFile file = sysFileMapper.selectById(fileId);
        if (file == null) {
            throw new ResourceNotFoundException("文件不存在: " + fileId);
        }

        // 检查重复绑定 (tenant_id + biz_type + biz_id + file_id)
        Long count = bizFileRelMapper.selectCount(
                new LambdaQueryWrapper<BizFileRel>()
                        .eq(BizFileRel::getTenantId, CurrentUserContext.getTenantId())
                        .eq(BizFileRel::getBizType, bizType)
                        .eq(BizFileRel::getBizId, bizId)
                        .eq(BizFileRel::getFileId, fileId));
        if (count > 0) {
            throw new BusinessException(ErrorCode.SYS_BUSINESS_CONFLICT, "文件已绑定到该业务对象");
        }

        BizFileRel rel = new BizFileRel();
        rel.setId(IdGenerator.nextId());
        rel.setBizType(bizType);
        rel.setBizId(bizId);
        rel.setFileId(fileId);
        rel.setRelType(relType);
        rel.setSortNo(sortNo != null ? sortNo : 0);
        bizFileRelMapper.insert(rel);
        return rel;
    }

    /**
     * 按业务对象查询绑定文件列表。
     *
     * @param bizType 业务类型
     * @param bizId   业务对象 ID
     * @return 绑定记录列表 (按 sortNo 升序)
     */
    public List<BizFileRel> listByBiz(String bizType, String bizId) {
        return bizFileRelMapper.selectList(
                new LambdaQueryWrapper<BizFileRel>()
                        .eq(BizFileRel::getTenantId, CurrentUserContext.getTenantId())
                        .eq(BizFileRel::getBizType, bizType)
                        .eq(BizFileRel::getBizId, bizId)
                        .orderByAsc(BizFileRel::getSortNo));
    }

    // ==================== 查询 ====================

    /**
     * 分页查询文件列表 (仅 SUCCESS 上传记录, 按当前租户隔离)。
     * 设计来源: routes.yaml listFiles operationId 权威基线。
     *
     * @param request 分页请求
     * @param fileName 文件名模糊查询 (可选)
     * @return 分页结果
     */
    public PageResult<SysFile> pageFiles(PageRequest request, String fileName) {
        LambdaQueryWrapper<SysFile> wrapper = new LambdaQueryWrapper<SysFile>()
                .eq(SysFile::getTenantId, CurrentUserContext.getTenantId())
                .eq(SysFile::getUploadStatus, UPLOAD_STATUS_SUCCESS)
                .like(fileName != null && !fileName.isBlank(), SysFile::getFileName, fileName)
                .orderByDesc(SysFile::getCreatedTime);
        // GA2-DS: 接入 DataScope 过滤，admin(ALL/TENANT) 读全部，非 admin 按 created_by(uploader_id) 过滤
        applyDataScope(wrapper, dataScopeResolver.resolve(RESOURCE_CODE));
        Page<SysFile> page = sysFileMapper.selectPage(
                new Page<>(request.page(), request.size()), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), request.page(), request.size());
    }

    /**
     * GA2-DS: 对 LambdaQueryWrapper 追加 DataScope 过滤条件。
     * - ALL/TENANT: 无附加条件 (admin 读全部)
     * - 其它(SELF/DEPT/CUSTOM/NONE 等): created_by = currentUserId (uploader_id)，userId 缺失时安全降级 1=0
     */
    private void applyDataScope(LambdaQueryWrapper<SysFile> wrapper, DataScope scope) {
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
        wrapper.apply("created_by = {0}", userId);
    }

    // ==================== 下载 ====================

    /**
     * 生成文件预签名下载 URL。
     *
     * @param fileId 文件 ID
     * @return 预签名 URL (有效期 30 分钟)
     */
    public String getDownloadUrl(String fileId) {
        SysFile file = getFile(fileId);
        try {
            int expirySeconds = (int) Duration.ofMinutes(DOWNLOAD_URL_EXPIRY_MINUTES).getSeconds();
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(file.getFileKey())
                            .expiry(expirySeconds, TimeUnit.SECONDS)
                            .build());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYS_INTERNAL_ERROR, "生成下载链接失败: " + fileId, e);
        }
    }

    // ==================== 查询 ====================

    /**
     * 查询文件详情。不存在抛 ResourceNotFoundException；跨租户访问抛 ResourceNotFoundException（不泄露存在性）。
     * 安全约束: 校验文件 tenant_id 与当前用户 tenant_id 一致，防止跨租户越权下载 (TC-SEC-FILE-002)。
     *
     * @param id 文件 ID
     * @return 文件元数据
     */
    public SysFile getFile(String id) {
        SysFile file = sysFileMapper.selectById(id);
        if (file == null) {
            throw new ResourceNotFoundException("文件不存在: " + id);
        }
        // 跨租户越权防护 (TC-SEC-FILE-002): 文件 tenant_id 必须与当前用户一致
        String currentTenant = CurrentUserContext.getTenantId();
        if (currentTenant != null && !currentTenant.equals(file.getTenantId())) {
            // 不抛 403 而抛 404，避免泄露文件存在性
            throw new ResourceNotFoundException("文件不存在: " + id);
        }
        return file;
    }

    @Transactional
    public void deleteFile(String id) {
        SysFile file = getFile(id);
        // 删除 MinIO 对象
        silentRemoveObject(file.getFileKey());
        // 删除元数据
        sysFileMapper.deleteById(id);
    }

    // ==================== 内部工具 ====================

    /**
     * 校验文件: 非空、大小不超过 100MB、扩展名在白名单内。
     * 设计来源: 64-安全威胁模型 TC-SEC-FILE-001、34-安全合规专项设计
     * 安全约束: 脚本/可执行文件（sh/bat/cmd/exe/js/php/sql/jar 等）一律拒绝，抛 FILE-400001。
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    "文件大小超过上限 100MB: " + file.getSize());
        }
        // 文件类型白名单校验 (TC-SEC-FILE-001)
        String fileName = file.getOriginalFilename();
        String ext = extractExtension(fileName);
        if (ext == null || !ALLOWED_EXTENSIONS.contains(ext.toLowerCase())) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED,
                    "不允许上传该文件类型: " + (ext != null ? ext : "无扩展名"));
        }
    }

    /**
     * 从文件名提取扩展名 (不含点)。无扩展名返回 null。
     * 例: "test.txt" -> "txt"；"archive.tar.gz" -> "gz"；"noext" -> null。
     */
    private String extractExtension(String fileName) {
        if (fileName == null) {
            return null;
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dotIndex + 1);
    }

    /** 构建 fileKey: {tenantId}/{yyyy/MM/dd}/{ULID}.{ext} */
    private String buildFileKey(String tenantId, String datePath, String ulid, String fileExt) {
        StringBuilder key = new StringBuilder()
                .append(tenantId)
                .append('/')
                .append(datePath)
                .append('/')
                .append(ulid);
        if (fileExt != null) {
            key.append('.').append(fileExt);
        }
        return key.toString();
    }

    /** 计算 SHA-256 校验和 (十六进制字符串)。 */
    private String computeSha256(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            log.warn("计算文件校验和失败: {}", e.getMessage());
            return null;
        }
    }

    /** 构建 SysFile 实体 (含必填字段)。 */
    private SysFile buildSysFile(String fileName, String fileKey, long fileSize,
                                 String contentType, String fileExt,
                                 String checksum, String uploadStatus,
                                 String tenantId, String createdBy) {
        SysFile sysFile = new SysFile();
        sysFile.setId(IdGenerator.nextId());
        sysFile.setTenantId(tenantId);
        sysFile.setCreatedBy(createdBy);
        sysFile.setCreatedTime(OffsetDateTime.now());
        sysFile.setFileName(fileName);
        sysFile.setFileKey(fileKey);
        sysFile.setFileSize(fileSize);
        sysFile.setContentType(contentType);
        sysFile.setFileExt(fileExt);
        sysFile.setStorageType(STORAGE_TYPE_MINIO);
        sysFile.setChecksum(checksum);
        sysFile.setUploadStatus(uploadStatus);
        return sysFile;
    }

    /** 确保存储桶存在，不存在则创建。 */
    private void ensureBucketExists() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("已创建 MinIO 存储桶: {}", bucket);
            }
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYS_INTERNAL_ERROR, "存储桶初始化失败: " + bucket, e);
        }
    }

    /** 静默删除 MinIO 对象 (用于清理临时对象，异常仅记录日志)。 */
    private void silentRemoveObject(String fileKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(fileKey)
                    .build());
            log.info("已清理 MinIO 临时对象: {}", fileKey);
        } catch (Exception e) {
            log.warn("清理 MinIO 临时对象失败: fileKey={}, error={}", fileKey, e.getMessage());
        }
    }
}
