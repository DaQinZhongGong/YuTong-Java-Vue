package com.yutong.ai.media.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.ai.media.domain.MediaJob;
import com.yutong.ai.media.dto.CreateMediaRequest;
import com.yutong.ai.media.mapper.MediaJobMapper;
import com.yutong.ai.media.provider.MediaProvider;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.id.IdGenerator;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 多模态媒体服务 — 统一管理 ai_media_job 生命周期。
 * 设计来源: V041 ai_media_job, Phase 7 /media/* mock async
 * 异步通过 Executor 调用真实 MediaProvider；禁止假 URL。
 */
@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);
    private static final Set<String> ALLOWED_TYPES = Set.of(
            MediaJob.TYPE_IMAGE, MediaJob.TYPE_VIDEO, MediaJob.TYPE_AUDIO, MediaJob.TYPE_PPT);

    private final MediaJobMapper mapper;
    private final List<MediaProvider> providers;
    private final Executor executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "media-job");
        t.setDaemon(true);
        return t;
    });

    public MediaService(MediaJobMapper mapper, List<MediaProvider> providers) {
        this.mapper = mapper;
        this.providers = providers;
    }

    public static void validateMediaType(String type) {
        if (type == null || !ALLOWED_TYPES.contains(type.toLowerCase())) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "不支持的 mediaType: " + type + "，仅支持 image/video/audio/ppt");
        }
    }

    @Transactional
    public MediaJob createJob(String mediaType, CreateMediaRequest request) {
        validateMediaType(mediaType);
        String type = mediaType.toLowerCase();
        MediaProvider provider = resolveProvider(type, request.getProviderCode());

        MediaJob job = new MediaJob();
        job.setMediaType(type);
        job.setPrompt(request.getPrompt());
        job.setProviderCode(provider.providerCode());
        job.setModelCode(request.getModelCode());
        job.setStatus(MediaJob.STATUS_PENDING);
        job.setInputJson(request.getInputJson());
        job.setRemark(request.getRemark());
        // BaseEntity.id 无 fill 注解, MetaObjectHandler 不自动填充, 必须显式生成 ULID
        job.setId(IdGenerator.nextId());
        mapper.insert(job);

        String jobId = job.getId();
        executor.execute(() -> completeAsync(jobId, provider));

        return job;
    }

    /**
     * 同步生成（短剧画面等编排场景）。失败抛业务异常，不返回假 URL。
     */
    @Transactional
    public MediaJob generateSync(String mediaType, String prompt) {
        validateMediaType(mediaType);
        if (prompt == null || prompt.isBlank()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "prompt 不能为空");
        }
        String type = mediaType.toLowerCase();
        MediaProvider provider = resolveProvider(type, null);
        MediaJob job = new MediaJob();
        job.setMediaType(type);
        job.setPrompt(prompt.length() > 2000 ? prompt.substring(0, 2000) : prompt);
        job.setProviderCode(provider.providerCode());
        job.setStatus(MediaJob.STATUS_RUNNING);
        // BaseEntity.id 无 fill 注解, MetaObjectHandler 不自动填充, 必须显式生成 ULID
        job.setId(IdGenerator.nextId());
        mapper.insert(job);
        try {
            MediaProvider.GenerateResult result = provider.generate(job);
            job.setOutputUrl(result.outputUrl());
            job.setOutputJson(result.outputJson());
            job.setCost(result.cost());
            job.setStatus(MediaJob.STATUS_SUCCESS);
            mapper.updateById(job);
            return mapper.selectById(job.getId());
        } catch (Exception e) {
            job.setStatus(MediaJob.STATUS_FAILED);
            String msg = e.getMessage() == null ? "unknown" : e.getMessage().replace("\"", "'");
            job.setOutputJson("{\"error\":\"" + msg + "\"}");
            mapper.updateById(job);
            if (e instanceof BusinessException be) {
                throw be;
            }
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "媒体生成失败: " + e.getMessage());
        }
    }

    private void completeAsync(String jobId, MediaProvider provider) {
        try {
            MediaJob running = mapper.selectById(jobId);
            if (running == null || !MediaJob.STATUS_PENDING.equals(running.getStatus())) return;
            running.setStatus(MediaJob.STATUS_RUNNING);
            mapper.updateById(running);

            MediaJob job = mapper.selectById(jobId);
            if (job == null) return;
            MediaProvider.GenerateResult result = provider.generate(job);
            job.setOutputUrl(result.outputUrl());
            job.setOutputJson(result.outputJson());
            job.setCost(result.cost());
            job.setStatus(MediaJob.STATUS_SUCCESS);
            mapper.updateById(job);
        } catch (Exception e) {
            log.error("media job failed: {}", jobId, e);
            try {
                MediaJob job = mapper.selectById(jobId);
                if (job != null) {
                    job.setStatus(MediaJob.STATUS_FAILED);
                    job.setOutputJson("{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
                    mapper.updateById(job);
                }
            } catch (Exception ex) {
                log.error("failed to mark media job FAILED: {}", jobId, ex);
            }
        }
    }

    public MediaJob getJob(String id) {
        MediaJob job = mapper.selectById(id);
        if (job == null) throw new ResourceNotFoundException("媒体任务不存在: " + id);
        return job;
    }

    public PageResult<MediaJob> pageJobs(PageRequest request, String mediaType, String status) {
        if (mediaType != null && !mediaType.isBlank()) validateMediaType(mediaType);
        String tenantId = CurrentUserContext.getTenantId();
        LambdaQueryWrapper<MediaJob> wrapper = new LambdaQueryWrapper<MediaJob>()
                .eq(tenantId != null, MediaJob::getTenantId, tenantId)
                .eq(mediaType != null && !mediaType.isBlank(), MediaJob::getMediaType, mediaType.toLowerCase())
                .eq(status != null && !status.isBlank(), MediaJob::getStatus, status.toUpperCase())
                .orderByDesc(MediaJob::getCreatedTime);
        Page<MediaJob> page = mapper.selectPage(new Page<>(request.page(), request.size()), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), request.page(), request.size());
    }

    private MediaProvider resolveProvider(String mediaType, String requestedCode) {
        if (requestedCode != null && !requestedCode.isBlank()) {
            for (MediaProvider p : providers) {
                if (p.providerCode().equalsIgnoreCase(requestedCode) && p.supports(mediaType)) return p;
            }
            // requested code 不支持该类型，回落到任意支持该类型的 provider
        }
        for (MediaProvider p : providers) {
            if (p.supports(mediaType)) return p;
        }
        throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "无可用 provider 支持 mediaType: " + mediaType);
    }
}
