package com.yutong.system.url.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.id.IdGenerator;
import com.yutong.system.url.domain.SysUrl;
import com.yutong.system.url.mapper.SysUrlMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * URL 白名单服务。设计来源: 业界同类实现 SysUrlService + ADR 0005 P3。
 *
 * <p>安全: 失败关闭 — urlPattern 唯一性校验; 状态仅 ENABLED/DISABLED。
 */
@Service
public class SysUrlService {

    private final SysUrlMapper mapper;

    public SysUrlService(SysUrlMapper mapper) {
        this.mapper = mapper;
    }

    public Page<SysUrl> page(int pageNo, int pageSize, String status, String keyword) {
        QueryWrapper<SysUrl> w = new QueryWrapper<>();
        if (status != null && !status.isBlank()) w.eq("status", status);
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like("url_pattern", keyword).or().like("description", keyword));
        }
        w.orderByDesc("updated_time");
        return mapper.selectPage(Page.of(pageNo, pageSize), w);
    }

    public SysUrl getById(String id) {
        SysUrl u = mapper.selectById(id);
        if (u == null) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "URL 配置不存在: " + id);
        }
        return u;
    }

    public List<SysUrl> listEnabled() {
        return mapper.selectList(new QueryWrapper<SysUrl>()
                .eq("status", SysUrl.STATUS_ENABLED)
                .orderByAsc("url_pattern"));
    }

    @Transactional
    public String save(SysUrl url) {
        boolean isCreate = (url.getId() == null || url.getId().isBlank());
        if (isCreate) {
            // 唯一性校验
            Long count = mapper.selectCount(new QueryWrapper<SysUrl>()
                    .eq("url_pattern", url.getUrlPattern()));
            if (count > 0) {
                throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                        "URL 模式已存在: " + url.getUrlPattern());
            }
            url.setId(IdGenerator.nextId());
            url.setStatus(url.getStatus() != null ? url.getStatus() : SysUrl.STATUS_ENABLED);
            mapper.insert(url);
        } else {
            mapper.updateById(url);
        }
        return url.getId();
    }

    @Transactional
    public void enable(String id) {
        SysUrl u = getById(id);
        u.setStatus(SysUrl.STATUS_ENABLED);
        mapper.updateById(u);
    }

    @Transactional
    public void disable(String id) {
        SysUrl u = getById(id);
        u.setStatus(SysUrl.STATUS_DISABLED);
        mapper.updateById(u);
    }

    @Transactional
    public void delete(String id) {
        mapper.deleteById(id);
    }
}
