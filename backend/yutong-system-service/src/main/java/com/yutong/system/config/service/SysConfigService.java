package com.yutong.system.config.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.common.auth.CurrentUserContext;

import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.id.IdGenerator;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.system.config.domain.SysConfig;
import com.yutong.system.config.mapper.SysConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 系统参数配置服务。设计来源: 17-平台基础能力、98-后端实现蓝图系统基础接口补齐规则
 * 更新配置后必须清理 Redis 缓存 yutong:config:{configKey}
 */
@Service
public class SysConfigService {

    private static final Logger log = LoggerFactory.getLogger(SysConfigService.class);

    private static final String CACHE_PREFIX = "yutong:config:";
    private static final long CACHE_TTL_MINUTES = 60;
    private static final String MASKED_VALUE = "******";

    private final SysConfigMapper sysConfigMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public SysConfigService(SysConfigMapper sysConfigMapper, StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.sysConfigMapper = sysConfigMapper;
        this.redis = redis;
        // GA2-14: 注入 Spring 容器的 ObjectMapper，已自动注册 JavaTimeModule，
        // 支持 BaseEntity 中 OffsetDateTime 字段的序列化/反序列化。
        this.objectMapper = objectMapper;
    }

    public PageResult<SysConfig> pageConfigs(PageRequest request, String keyword, String configGroup) {
        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getTenantId, CurrentUserContext.getTenantId())
                .like(keyword != null && !keyword.isBlank(), SysConfig::getConfigKey, keyword)
                .eq(configGroup != null && !configGroup.isBlank(), SysConfig::getConfigGroup, configGroup)
                .orderByAsc(SysConfig::getConfigGroup)
                .orderByAsc(SysConfig::getConfigKey);
        Page<SysConfig> page = sysConfigMapper.selectPage(
                new Page<>(request.page(), request.size()), wrapper);
        List<SysConfig> records = page.getRecords();
        // 敏感配置在列表返回时脱敏
        for (SysConfig config : records) {
            if (Boolean.TRUE.equals(config.getSensitive())) {
                config.setConfigValue(MASKED_VALUE);
            }
        }
        return PageResult.of(records, page.getTotal(), request.page(), request.size());
    }

    @Transactional
    public SysConfig createConfig(SysConfig config) {
        // GA2-L187: configKey 唯一校验 (租户内 tenant_id + config_key), 对齐 CT-createConfig 契约测试
        LambdaQueryWrapper<SysConfig> dupCheck = new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getTenantId, CurrentUserContext.getTenantId())
                .eq(SysConfig::getConfigKey, config.getConfigKey());
        Long count = sysConfigMapper.selectCount(dupCheck);
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.SYS_BUSINESS_CONFLICT,
                    "参数配置键已存在: " + config.getConfigKey());
        }
        // GA2-15: BaseEntity.id 标注 @TableId(type=INPUT) 但无 @TableField(fill=INSERT)，
        // strictInsertFill 不会自动填充 id，必须显式生成 ULID (参照 ProductService/CustomerService 等范式)。
        if (config.getId() == null || config.getId().isBlank()) {
            config.setId(IdGenerator.nextId());
        }
        // GA2-15-6b: sys_config.status NOT NULL 无 DB 默认值，未指定时默认 'ENABLED'
        // (对齐 R__seed_config.sql 种子数据 status='ENABLED' 惯例)
        if (config.getStatus() == null || config.getStatus().isBlank()) {
            config.setStatus("ENABLED");
        }
        sysConfigMapper.insert(config);
        evictCache(config.getConfigKey());
        return config;
    }

    @Transactional
    public SysConfig updateConfig(String id, SysConfig config) {
        SysConfig existing = sysConfigMapper.selectById(id);
        if (existing == null) {
            throw new ResourceNotFoundException("参数配置不存在: " + id);
        }
        config.setId(id);
        config.setTenantId(existing.getTenantId());
        // 不允许修改 configKey，强制沿用原值
        config.setConfigKey(existing.getConfigKey());
        // GA2-L187: 乐观锁校验 (version 不匹配 → 0 行 → SYS-409001)
        int rows = sysConfigMapper.updateById(config);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.SYS_OPTIMISTIC_LOCK,
                    "参数配置已被其他操作更新，请刷新后重试: " + id);
        }
        evictCache(existing.getConfigKey());
        return config;
    }

    /**
     * 查询参数配置详情（按主键 ID，敏感配置脱敏返回）。
     * GA2-15 落地: 补齐管理端"参数配置详情"接口，设计来源 91-Web基础后台逐页交互详设。
     */
    public SysConfig getConfig(String id) {
        SysConfig config = sysConfigMapper.selectById(id);
        if (config == null) {
            throw new ResourceNotFoundException("参数配置不存在: " + id);
        }
        // 敏感配置详情也脱敏，避免敏感值通过详情接口泄漏
        if (Boolean.TRUE.equals(config.getSensitive())) {
            config.setConfigValue(MASKED_VALUE);
        }
        return config;
    }

    /**
     * 删除参数配置（按主键 ID），删除后清理 Redis 缓存。
     * GA2-15 落地: 补齐管理端"参数配置删除"接口，设计来源 17-平台基础能力/91-Web基础后台逐页交互详设。
     * 设计文档 17 要求"修改后必须清缓存"，删除属于变更同样需要失效缓存。
     */
    @Transactional
    public void deleteConfig(String id) {
        SysConfig existing = sysConfigMapper.selectById(id);
        if (existing == null) {
            throw new ResourceNotFoundException("参数配置不存在: " + id);
        }
        sysConfigMapper.deleteById(id);
        evictCache(existing.getConfigKey());
    }

    /** 清理所有 yutong:config:* Redis 缓存。 */
    public void refreshConfigCache() {
        String pattern = CACHE_PREFIX + "*";
        Set<String> keys = new HashSet<>();
        redis.execute((RedisCallback<Void>) connection -> {
            Cursor<byte[]> cursor = connection.scan(
                    ScanOptions.scanOptions().match(pattern).count(1000).build());
            while (cursor.hasNext()) {
                keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
            }
            try {
                cursor.close();
            } catch (Exception e) {
                // 关闭游标失败可忽略，连接由模板自动释放
            }
            return null;
        });
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    /** 按 key 查询配置 (带 Redis 缓存，内部方法供其他服务调用)。 */
    public SysConfig getConfigByKey(String configKey) {
        String cacheKey = CACHE_PREFIX + configKey;
        String cached = redis.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, SysConfig.class);
            } catch (Exception e) {
                // 反序列化失败则回源查库；记录 WARN 便于诊断
                log.warn("配置缓存反序列化失败 configKey={} - {}, 回源查库", configKey, e.getMessage());
            }
        }
        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getTenantId, CurrentUserContext.getTenantId())
                .eq(SysConfig::getConfigKey, configKey);
        SysConfig config = sysConfigMapper.selectOne(wrapper);
        if (config == null) {
            throw new ResourceNotFoundException("参数配置不存在: " + configKey);
        }
        try {
            redis.opsForValue().set(cacheKey, objectMapper.writeValueAsString(config),
                    CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            // 缓存写入失败不影响主流程，记录 WARN 便于诊断
            log.warn("配置缓存写入失败 configKey={} - {}", configKey, e.getMessage(), e);
        }
        return config;
    }

    private void evictCache(String configKey) {
        if (configKey != null) {
            redis.delete(CACHE_PREFIX + configKey);
        }
    }
}
