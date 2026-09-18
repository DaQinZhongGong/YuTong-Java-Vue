package com.yutong.system.oss.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.id.IdGenerator;
import com.yutong.system.oss.domain.SysOssConfig;
import com.yutong.system.oss.dto.SaveOssConfigRequest;
import com.yutong.system.oss.mapper.SysOssConfigMapper;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

/**
 * OSS 配置服务 — 多桶对象存储配置管理。
 * 落点: 业界同类实现 SysOssConfigService + ADR 0005 P1-D。
 *
 * <p>生产语义 (失败关闭):
 * <ul>
 *   <li>configKey 创建后不可改 (API 引用键);</li>
 *   <li>同一时刻仅一个 is_default=true (设置新默认时自动取消旧默认);</li>
 *   <li>默认配置禁止删除/停用 (必须先切换默认到其他配置);</li>
 *   <li>secretKey 列表接口脱敏返回 (****), 详情接口完整返回;</li>
 *   <li>test-connection 真实探测 MinIO bucketExists, 失败关闭。</li>
 * </ul>
 */
@Service
public class OssConfigService {

    private static final Logger log = LoggerFactory.getLogger(OssConfigService.class);

    private static final String MASK = "****";

    private final SysOssConfigMapper mapper;

    public OssConfigService(SysOssConfigMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 分页查询 (脱敏返回 secretKey)。
     */
    public Page<SysOssConfig> page(int pageNo, int pageSize, String status, String keyword) {
        QueryWrapper<SysOssConfig> w = new QueryWrapper<>();
        if (status != null && !status.isBlank()) w.eq("status", status);
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like("config_key", keyword)
                    .or().like("config_name", keyword)
                    .or().like("bucket_name", keyword));
        }
        w.orderByDesc("is_default").orderByDesc("updated_time");
        Page<SysOssConfig> page = mapper.selectPage(Page.of(pageNo, pageSize), w);
        page.getRecords().forEach(this::maskSecret);
        return page;
    }

    /**
     * 查询全部启用配置 (脱敏)。
     */
    public List<SysOssConfig> listEnabled() {
        List<SysOssConfig> list = mapper.selectList(
                new QueryWrapper<SysOssConfig>().eq("status", SysOssConfig.STATUS_ENABLED)
                        .orderByDesc("is_default").orderByAsc("config_key"));
        list.forEach(this::maskSecret);
        return list;
    }

    /**
     * 按 ID 查询 (完整返回, 含 secretKey)。
     */
    public SysOssConfig getById(String id) {
        SysOssConfig c = mapper.selectById(id);
        if (c == null) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "OSS 配置不存在: " + id);
        }
        return c;
    }

    /**
     * 按配置键查询 (完整返回)。
     */
    public SysOssConfig getByKey(String configKey) {
        SysOssConfig c = mapper.selectOne(
                new QueryWrapper<SysOssConfig>().eq("config_key", configKey).last("LIMIT 1"));
        if (c == null) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "OSS 配置不存在: " + configKey);
        }
        return c;
    }

    /**
     * 获取默认配置 (完整返回)。
     */
    public SysOssConfig getDefault() {
        SysOssConfig c = mapper.selectOne(
                new QueryWrapper<SysOssConfig>().eq("is_default", true)
                        .eq("status", SysOssConfig.STATUS_ENABLED).last("LIMIT 1"));
        if (c == null) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "无可用默认 OSS 配置");
        }
        return c;
    }

    /**
     * 保存 (创建/更新)。更新时 configKey 不可改。
     */
    @Transactional
    public String save(SaveOssConfigRequest req) {
        SysOssConfig c;
        boolean isCreate = (req.getId() == null || req.getId().isBlank());
        if (isCreate) {
            if (isKeyTaken(req.getConfigKey(), null)) {
                throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                        "配置键已被占用: " + req.getConfigKey());
            }
            c = new SysOssConfig();
            c.setId(IdGenerator.nextId());
            c.setConfigKey(req.getConfigKey());
        } else {
            c = getById(req.getId());
            // configKey 创建后不可改
        }
        c.setConfigName(req.getConfigName());
        c.setStorageType(req.getStorageType());
        c.setEndpoint(req.getEndpoint());
        c.setAccessKey(req.getAccessKey());
        c.setSecretKey(req.getSecretKey());
        c.setBucketName(req.getBucketName());
        c.setDomain(req.getDomain());
        c.setRegion(req.getRegion());
        c.setIsHttps(req.getIsHttps() != null ? req.getIsHttps() : false);
        c.setRemark(req.getRemark());

        boolean makeDefault = Boolean.TRUE.equals(req.getIsDefault());
        if (makeDefault) {
            clearDefaultFlag(c.getId());
            c.setIsDefault(true);
            c.setStatus(SysOssConfig.STATUS_ENABLED);
        } else if (isCreate) {
            c.setIsDefault(false);
            c.setStatus(SysOssConfig.STATUS_ENABLED);
        }

        if (isCreate) {
            mapper.insert(c);
        } else {
            mapper.updateById(c);
        }
        return c.getId();
    }

    /**
     * 启用配置。
     */
    @Transactional
    public void enable(String id) {
        SysOssConfig c = getById(id);
        c.setStatus(SysOssConfig.STATUS_ENABLED);
        mapper.updateById(c);
    }

    /**
     * 停用配置 (默认配置禁止停用)。
     */
    @Transactional
    public void disable(String id) {
        SysOssConfig c = getById(id);
        if (Boolean.TRUE.equals(c.getIsDefault())) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    "默认配置禁止停用, 请先将其他配置设为默认");
        }
        c.setStatus(SysOssConfig.STATUS_DISABLED);
        mapper.updateById(c);
    }

    /**
     * 设为默认 (自动取消旧默认)。
     */
    @Transactional
    public void setDefault(String id) {
        SysOssConfig c = getById(id);
        if (!SysOssConfig.STATUS_ENABLED.equals(c.getStatus())) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "仅启用状态的配置可设为默认");
        }
        clearDefaultFlag(id);
        c.setIsDefault(true);
        mapper.updateById(c);
    }

    /**
     * 删除配置 (默认配置禁止删除)。
     */
    @Transactional
    public void delete(String id) {
        SysOssConfig c = getById(id);
        if (Boolean.TRUE.equals(c.getIsDefault())) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    "默认配置禁止删除, 请先将其他配置设为默认");
        }
        mapper.deleteById(id);
    }

    /**
     * 测试连接 (真实探测 MinIO bucketExists)。
     * 仅支持 MINIO/S3 类型; LOCAL 类型直接返回成功。
     */
    public boolean testConnection(String id) {
        SysOssConfig c = getById(id);
        if (SysOssConfig.STORAGE_LOCAL.equals(c.getStorageType())) {
            return true;
        }
        try {
            MinioClient client = MinioClient.builder()
                    .endpoint(c.getEndpoint())
                    .credentials(c.getAccessKey(), c.getSecretKey())
                    .build();
            boolean exists = client.bucketExists(
                    BucketExistsArgs.builder().bucket(c.getBucketName()).build());
            log.info("[OssConfig] testConnection key={}, bucket={}, exists={}",
                    c.getConfigKey(), c.getBucketName(), exists);
            return exists;
        } catch (Exception e) {
            log.warn("[OssConfig] testConnection failed key={}, error={}",
                    c.getConfigKey(), e.getMessage());
            return false;
        }
    }

    // ==================== 内部方法 ====================

    private boolean isKeyTaken(String configKey, String excludeId) {
        QueryWrapper<SysOssConfig> w = new QueryWrapper<SysOssConfig>()
                .eq("config_key", configKey);
        if (excludeId != null) w.ne("id", excludeId);
        return mapper.selectCount(w) > 0;
    }

    private void clearDefaultFlag(String excludeId) {
        mapper.update(null, new UpdateWrapper<SysOssConfig>()
                .set("is_default", false)
                .eq("is_default", true)
                .ne("id", excludeId));
    }

    private void maskSecret(SysOssConfig c) {
        if (c.getSecretKey() != null && !c.getSecretKey().isBlank()) {
            c.setSecretKey(MASK);
        }
    }
}
