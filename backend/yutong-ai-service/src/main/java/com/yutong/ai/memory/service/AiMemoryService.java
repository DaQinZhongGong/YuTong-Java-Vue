package com.yutong.ai.memory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.ai.memory.domain.AiMemory;
import com.yutong.ai.memory.dto.SaveMemoryRequest;
import com.yutong.ai.memory.mapper.AiMemoryMapper;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.id.IdGenerator;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 跨会话记忆 CRUD + 召回。短期记忆仍由 AgentMemoryService 窗口承担。
 */
@Service
public class AiMemoryService {

    private static final Set<String> OWNER_TYPES = Set.of(
            AiMemory.OWNER_GLOBAL, AiMemory.OWNER_TENANT, AiMemory.OWNER_USER, AiMemory.OWNER_AGENT);
    private static final Set<String> KINDS = Set.of(
            AiMemory.KIND_LONG_TERM, AiMemory.KIND_USER, AiMemory.KIND_GLOBAL);
    private static final Set<String> SOURCES = Set.of(
            AiMemory.SOURCE_MANUAL, AiMemory.SOURCE_CHAT, AiMemory.SOURCE_KNOWLEDGE,
            AiMemory.SOURCE_TOOL, AiMemory.SOURCE_AGENT);
    private static final int MAX_CONTENT = 4000;

    private final AiMemoryMapper mapper;

    public AiMemoryService(AiMemoryMapper mapper) {
        this.mapper = mapper;
    }

    public PageResult<AiMemory> page(PageRequest request, String ownerType, String ownerId, String memoryKind) {
        String tenantId = CurrentUserContext.getTenantId();
        LambdaQueryWrapper<AiMemory> wrapper = new LambdaQueryWrapper<AiMemory>()
                .eq(AiMemory::getTenantId, tenantId)
                .eq(ownerType != null && !ownerType.isBlank(), AiMemory::getOwnerType, ownerType)
                .eq(ownerId != null && !ownerId.isBlank(), AiMemory::getOwnerId, ownerId)
                .eq(memoryKind != null && !memoryKind.isBlank(), AiMemory::getMemoryKind, memoryKind)
                .and(w -> w.isNull(AiMemory::getExpiresAt).or().gt(AiMemory::getExpiresAt, OffsetDateTime.now()))
                .orderByDesc(AiMemory::getCreatedTime);
        Page<AiMemory> page = mapper.selectPage(new Page<>(request.page(), request.size()), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), request.page(), request.size());
    }

    public AiMemory get(String id) {
        AiMemory memory = mapper.selectById(id);
        if (memory == null) {
            throw new ResourceNotFoundException("记忆不存在: " + id);
        }
        assertTenant(memory);
        return memory;
    }

    @Transactional
    public AiMemory save(SaveMemoryRequest request) {
        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (content.isBlank()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "content 不能为空");
        }
        if (containsSensitive(content)) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "记忆内容疑似包含敏感信息，禁止写入");
        }
        if (content.length() > MAX_CONTENT) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "content 超过 " + MAX_CONTENT + " 字符");
        }
        String ownerType = normalize(request.getOwnerType(), AiMemory.OWNER_USER, OWNER_TYPES, "ownerType");
        String kind = normalize(request.getMemoryKind(), defaultKind(ownerType), KINDS, "memoryKind");
        String source = normalize(request.getSource(), AiMemory.SOURCE_MANUAL, SOURCES, "source");
        String ownerId = resolveOwnerId(ownerType, request.getOwnerId());
        BigDecimal confidence = request.getConfidence() == null ? BigDecimal.ONE : request.getConfidence();
        if (confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "confidence 必须在 0-1");
        }

        AiMemory memory = new AiMemory();
        memory.setId(IdGenerator.nextId());
        memory.setTenantId(CurrentUserContext.getTenantId());
        memory.setCreatedBy(CurrentUserContext.getUserId());
        memory.setOwnerType(ownerType);
        memory.setOwnerId(ownerId);
        memory.setMemoryKind(kind);
        memory.setContent(content);
        memory.setSource(source);
        memory.setConfidence(confidence);
        memory.setExpiresAt(request.getExpiresAt());
        memory.setRemark(request.getRemark());
        memory.setVersion(0);
        mapper.insert(memory);
        return memory;
    }

    @Transactional
    public void delete(String id) {
        AiMemory memory = get(id);
        mapper.deleteById(memory.getId());
    }

    /**
     * 为对话组装可注入上下文：全局 + 租户 + 当前用户，最多 12 条。
     */
    public String recallForChat(String userId) {
        String tenantId = CurrentUserContext.getTenantId();
        String uid = userId == null || userId.isBlank() ? CurrentUserContext.getUserId() : userId;
        LambdaQueryWrapper<AiMemory> wrapper = new LambdaQueryWrapper<AiMemory>()
                .eq(AiMemory::getTenantId, tenantId)
                .and(w -> {
                    w.eq(AiMemory::getOwnerType, AiMemory.OWNER_GLOBAL)
                            .or()
                            .eq(AiMemory::getOwnerType, AiMemory.OWNER_TENANT)
                            .eq(AiMemory::getOwnerId, tenantId);
                    if (uid != null && !uid.isBlank()) {
                        w.or()
                                .eq(AiMemory::getOwnerType, AiMemory.OWNER_USER)
                                .eq(AiMemory::getOwnerId, uid);
                    }
                })
                .and(w -> w.isNull(AiMemory::getExpiresAt).or().gt(AiMemory::getExpiresAt, OffsetDateTime.now()))
                .orderByDesc(AiMemory::getCreatedTime)
                .last("LIMIT 12");
        List<AiMemory> list = mapper.selectList(wrapper);
        if (list.isEmpty()) {
            return "";
        }
        return list.stream()
                .map(m -> "- [" + m.getMemoryKind() + "/" + m.getOwnerType() + "] " + m.getContent())
                .collect(Collectors.joining("\n", "[Persistent memory]\n", "\n"));
    }

    private void assertTenant(AiMemory memory) {
        String tenantId = CurrentUserContext.getTenantId();
        if (tenantId != null && memory.getTenantId() != null && !tenantId.equals(memory.getTenantId())) {
            throw new ResourceNotFoundException("记忆不存在: " + memory.getId());
        }
    }

    private String resolveOwnerId(String ownerType, String requested) {
        return switch (ownerType) {
            case AiMemory.OWNER_GLOBAL -> "*";
            case AiMemory.OWNER_TENANT -> CurrentUserContext.getTenantId();
            case AiMemory.OWNER_USER -> requested == null || requested.isBlank()
                    ? CurrentUserContext.getUserId() : requested.trim();
            case AiMemory.OWNER_AGENT -> {
                if (requested == null || requested.isBlank()) {
                    throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "AGENT 记忆必须提供 ownerId");
                }
                yield requested.trim();
            }
            default -> throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "非法 ownerType");
        };
    }

    private String defaultKind(String ownerType) {
        return switch (ownerType) {
            case AiMemory.OWNER_GLOBAL -> AiMemory.KIND_GLOBAL;
            case AiMemory.OWNER_USER -> AiMemory.KIND_USER;
            default -> AiMemory.KIND_LONG_TERM;
        };
    }

    private String normalize(String value, String fallback, Set<String> allowed, String field) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String v = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(v)) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, field + " 非法: " + value);
        }
        return v;
    }

    private boolean containsSensitive(String content) {
        String lower = content.toLowerCase(Locale.ROOT);
        return lower.contains("password")
                || lower.contains("api_key")
                || lower.contains("apikey")
                || lower.contains("secret")
                || content.contains("密码")
                || content.matches("(?s).*\\b\\d{15,19}\\b.*");
    }
}
