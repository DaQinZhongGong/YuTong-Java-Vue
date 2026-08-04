package com.yutong.system.dict.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.id.IdGenerator;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.system.dict.domain.DictItem;
import com.yutong.system.dict.domain.DictType;
import com.yutong.system.dict.mapper.DictItemMapper;
import com.yutong.system.dict.mapper.DictTypeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 字典服务。设计来源: 17-平台基础能力、98-后端实现蓝图系统基础接口补齐规则
 * 更新字典后必须清理 Redis 缓存 yutong:dict:{dictType}
 *
 * <p>GA2-14 落地: listByType 真正实现 Redis 缓存读写（读穿+回写+TTL 60min），
 * 设计来源: 17 号文档"按 dict_type 缓存到 Redis，key：yutong:dict:{dictType}"、
 * 72 号文档"字典慢 → Redis 缓存、本地短缓存"，P95<100ms 目标。
 */
@Service
public class DictService {

    private static final Logger log = LoggerFactory.getLogger(DictService.class);

    private static final String CACHE_PREFIX = "yutong:dict:";
    private static final long CACHE_TTL_MINUTES = 60;

    private final DictTypeMapper dictTypeMapper;
    private final DictItemMapper dictItemMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public DictService(DictTypeMapper dictTypeMapper, DictItemMapper dictItemMapper,
                       StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.dictTypeMapper = dictTypeMapper;
        this.dictItemMapper = dictItemMapper;
        this.redis = redis;
        // GA2-14: 注入 Spring 容器的 ObjectMapper，已自动注册 JavaTimeModule，
        // 支持 BaseEntity 中 OffsetDateTime 字段的序列化/反序列化。
        // 之前使用 new ObjectMapper() 会导致序列化 OffsetDateTime 失败被静默吞掉，
        // 缓存键无法写入 Redis。
        this.objectMapper = objectMapper;
    }

    public PageResult<DictType> pageDictTypes(PageRequest request, String keyword) {
        LambdaQueryWrapper<DictType> wrapper = new LambdaQueryWrapper<DictType>()
                .eq(DictType::getTenantId, CurrentUserContext.getTenantId())
                .like(keyword != null && !keyword.isBlank(), DictType::getDictName, keyword)
                .orderByAsc(DictType::getSortNo);
        Page<DictType> page = dictTypeMapper.selectPage(
                new Page<>(request.page(), request.size()), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), request.page(), request.size());
    }

    @Transactional
    public DictType createDictType(DictType type) {
        // GA2-L185: 编码唯一校验 (租户内 tenant_id + dict_type), 对齐 CT-createDictType 契约测试
        LambdaQueryWrapper<DictType> dupCheck = new LambdaQueryWrapper<DictType>()
                .eq(DictType::getTenantId, CurrentUserContext.getTenantId())
                .eq(DictType::getDictType, type.getDictType());
        Long count = dictTypeMapper.selectCount(dupCheck);
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.SYS_BUSINESS_CONFLICT,
                    "字典类型编码已存在: " + type.getDictType());
        }
        // GA2-15: BaseEntity.id 无 @TableField(fill=INSERT), strictInsertFill 不会自动填充, 必须显式生成 ULID。
        if (type.getId() == null || type.getId().isBlank()) {
            type.setId(IdGenerator.nextId());
        }
        dictTypeMapper.insert(type);
        return type;
    }

    @Transactional
    public DictType updateDictType(String id, DictType type) {
        DictType existing = dictTypeMapper.selectById(id);
        if (existing == null) throw new ResourceNotFoundException("字典类型不存在: " + id);
        type.setId(id);
        type.setTenantId(existing.getTenantId());
        // GA2-L185: dictType 变更时校验租户内唯一, 对齐 CT-updateDictType 契约测试
        if (type.getDictType() != null && !type.getDictType().equals(existing.getDictType())) {
            LambdaQueryWrapper<DictType> dupCheck = new LambdaQueryWrapper<DictType>()
                    .eq(DictType::getTenantId, existing.getTenantId())
                    .eq(DictType::getDictType, type.getDictType())
                    .ne(DictType::getId, id);
            Long count = dictTypeMapper.selectCount(dupCheck);
            if (count != null && count > 0) {
                throw new BusinessException(ErrorCode.SYS_BUSINESS_CONFLICT,
                        "字典类型编码已存在: " + type.getDictType());
            }
        }
        // GA2-L185: 乐观锁校验 (version 不匹配 → 0 行 → SYS-409001)
        int rows = dictTypeMapper.updateById(type);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.SYS_OPTIMISTIC_LOCK,
                    "字典类型已被其他操作更新，请刷新后重试: " + id);
        }
        // GA2-14: 字典类型名称变更不影响 dictType 编码，无需清缓存
        return type;
    }

    @Transactional
    public void deleteDictType(String id) {
        DictType existing = dictTypeMapper.selectById(id);
        if (existing == null) throw new ResourceNotFoundException("字典类型不存在: " + id);
        // 删除字典类型及其关联字典项
        dictItemMapper.delete(new LambdaQueryWrapper<DictItem>()
                .eq(DictItem::getTenantId, CurrentUserContext.getTenantId())
                .eq(DictItem::getDictType, existing.getDictType()));
        dictTypeMapper.deleteById(id);
        // GA2-14: 字典类型删除后必须失效关联字典项缓存
        evictCache(existing.getDictType());
    }

    public PageResult<DictItem> pageDictItems(PageRequest request, String dictType) {
        LambdaQueryWrapper<DictItem> wrapper = new LambdaQueryWrapper<DictItem>()
                .eq(DictItem::getTenantId, CurrentUserContext.getTenantId())
                .eq(dictType != null && !dictType.isBlank(), DictItem::getDictType, dictType)
                .orderByAsc(DictItem::getSortNo);
        Page<DictItem> page = dictItemMapper.selectPage(
                new Page<>(request.page(), request.size()), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), request.page(), request.size());
    }

    public DictItem getDictItem(String id) {
        DictItem item = dictItemMapper.selectById(id);
        if (item == null) throw new ResourceNotFoundException("字典项不存在: " + id);
        return item;
    }

    @Transactional
    public DictItem createDictItem(DictItem item) {
        // GA2-15: BaseEntity.id 无 @TableField(fill=INSERT), strictInsertFill 不会自动填充, 必须显式生成 ULID。
        if (item.getId() == null || item.getId().isBlank()) {
            item.setId(IdGenerator.nextId());
        }
        dictItemMapper.insert(item);
        evictCache(item.getDictType());
        return item;
    }

    @Transactional
    public DictItem updateDictItem(String id, DictItem item) {
        DictItem existing = getDictItem(id);
        item.setId(id);
        item.setTenantId(existing.getTenantId());
        dictItemMapper.updateById(item);
        evictCache(existing.getDictType());
        if (item.getDictType() != null && !item.getDictType().equals(existing.getDictType())) {
            evictCache(item.getDictType());
        }
        return item;
    }

    @Transactional
    public void deleteDictItem(String id) {
        DictItem existing = getDictItem(id);
        dictItemMapper.deleteById(id);
        evictCache(existing.getDictType());
    }

    /**
     * 按字典类型查询启用字典项 (带 Redis 缓存)。
     *
     * <p>GA2-14 落地: 读穿模式（cache-aside）+ 回写 + TTL 60min。
     * 设计来源: 17 号文档"按 dict_type 缓存到 Redis，key：yutong:dict:{dictType}"。
     * 缓存格式: JSON 序列化的 List<DictItem>，key=yutong:dict:{dictType}。
     * 缓存未命中查库后回写，反序列化失败回源查库，缓存写入失败不影响主流程。
     */
    public List<DictItem> listByType(String dictType) {
        String cacheKey = CACHE_PREFIX + dictType;
        // 1. 读缓存
        String cached = redis.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, new TypeReference<List<DictItem>>() {});
            } catch (Exception e) {
                // 反序列化失败则回源查库；记录 WARN 便于诊断缓存格式异常
                log.warn("字典缓存反序列化失败 dictType={} - {}, 回源查库", dictType, e.getMessage());
            }
        }
        // 2. 缓存未命中，查库
        LambdaQueryWrapper<DictItem> wrapper = new LambdaQueryWrapper<DictItem>()
                .eq(DictItem::getTenantId, CurrentUserContext.getTenantId())
                .eq(DictItem::getDictType, dictType)
                .eq(DictItem::getStatus, "ENABLED")
                .orderByAsc(DictItem::getSortNo);
        List<DictItem> items = dictItemMapper.selectList(wrapper);
        if (CollectionUtils.isEmpty(items)) {
            throw new ResourceNotFoundException("字典类型不存在或无启用项: " + dictType);
        }
        // 3. 回写缓存 (带 TTL 60min)
        try {
            redis.opsForValue().set(cacheKey, objectMapper.writeValueAsString(items),
                    CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            // 缓存写入失败不影响主流程，但记录 WARN 便于诊断（如序列化失败、Redis 连接异常）
            log.warn("字典缓存写入失败 dictType={} - {}", dictType, e.getMessage(), e);
        }
        return items;
    }

    private void evictCache(String dictType) {
        if (dictType != null) {
            redis.delete(CACHE_PREFIX + dictType);
        }
    }
}
