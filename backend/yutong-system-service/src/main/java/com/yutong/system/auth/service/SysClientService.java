package com.yutong.system.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.id.IdGenerator;
import com.yutong.system.auth.domain.SysClient;
import com.yutong.system.auth.mapper.SysClientMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 客户端服务 — 创建/启用/禁用
 * 落点: 08-API 契约设计 + ADR 0004 P2-F 批次 6-C
 *
 * 工作流:
 *   1. save: 创建/更新客户端, 首次创建自动生成 clientId + clientSecret
 *   2. enable: ENABLE (默认新建状态)
 *   3. disable: DISABLE
 *   4. getByClientId: 按 clientId 查询 (token 颁发时)
 *   5. resetSecret: 重置 clientSecret (管理员操作)
 */
@Service
public class SysClientService {

    private final SysClientMapper mapper;

    public SysClientService(SysClientMapper mapper) {
        this.mapper = mapper;
    }

    public Page<SysClient> page(int pageNo, int pageSize, String status) {
        QueryWrapper<SysClient> w = new QueryWrapper<>();
        if (status != null && !status.isBlank()) w.eq("status", status);
        w.orderByDesc("created_time");
        return mapper.selectPage(Page.of(pageNo, pageSize), w);
    }

    public SysClient getById(String id) {
        SysClient c = mapper.selectById(id);
        if (c == null) throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "客户端不存在: " + id);
        return c;
    }

    /** 按 clientId 查询 (OAuth2 token 颁发用) */
    public SysClient getByClientId(String clientId) {
        SysClient c = mapper.selectOne(
                new QueryWrapper<SysClient>().eq("client_id", clientId).last("LIMIT 1"));
        if (c == null) throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "客户端不存在: " + clientId);
        return c;
    }

    @Transactional
    public String save(SysClient c) {
        if (c.getId() == null || c.getId().isBlank()) {
            // 创建: BaseEntity.id 无 fill 注解, 必须显式生成 ULID
            c.setId(IdGenerator.nextId());
            if (c.getClientId() == null || c.getClientId().isBlank()) {
                c.setClientId(generateClientId());
            }
            if (c.getClientSecret() == null || c.getClientSecret().isBlank()) {
                c.setClientSecret(generateClientSecret());
            }
            if (c.getStatus() == null) c.setStatus(SysClient.STATUS_ENABLE);
            mapper.insert(c);
        } else {
            // 更新: 不允许改 clientId/clientSecret
            SysClient old = getById(c.getId());
            c.setClientId(old.getClientId());
            c.setClientSecret(old.getClientSecret());
            mapper.updateById(c);
        }
        return c.getId();
    }

    @Transactional
    public void enable(String id) {
        SysClient c = getById(id);
        c.setStatus(SysClient.STATUS_ENABLE);
        mapper.updateById(c);
    }

    @Transactional
    public void disable(String id) {
        SysClient c = getById(id);
        c.setStatus(SysClient.STATUS_DISABLE);
        mapper.updateById(c);
    }

    @Transactional
    public String resetSecret(String id) {
        SysClient c = getById(id);
        String newSecret = generateClientSecret();
        c.setClientSecret(newSecret);
        mapper.updateById(c);
        return newSecret;
    }

    private String generateClientId() {
        return "cli_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String generateClientSecret() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String currentUserId() {
        try {
            String id = CurrentUserContext.getUserId();
            return id == null || id.isBlank() ? "system" : id;
        } catch (Exception e) {
            return "system";
        }
    }
}
