package com.yutong.system.event.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.id.IdGenerator;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.system.event.domain.ClientEvent;
import com.yutong.system.event.mapper.ClientEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 端侧埋点事件服务。设计来源: 94-端侧埋点与体验监控详设
 *
 * <p>提供批量上报和分页查询能力。批量上报为 PublicEndpoint（支持未登录页面如登录页白屏检测），
 * service 层从 {@link CurrentUserContext} 获取 tenantId/userId，未登录时 tenantId 兜底为 "public"。
 *
 * <p>隐私约束: 后端只存储端侧 SDK 已脱敏的 userIdHash/tenantIdHash/bizIdHash，
 * 不解析也不采集明文业务数据。
 */
@Service
public class ClientEventService {

    private static final Logger log = LoggerFactory.getLogger(ClientEventService.class);

    /** 单次批量上报上限，防止恶意大请求体 */
    private static final int MAX_BATCH_SIZE = 100;

    /** 未登录时兜底租户标识 */
    private static final String PUBLIC_TENANT = "public";

    private final ClientEventMapper clientEventMapper;

    public ClientEventService(ClientEventMapper clientEventMapper) {
        this.clientEventMapper = clientEventMapper;
    }

    /**
     * 批量上报端侧埋点事件。
     *
     * <p>设计来源: 94 号文档"上报接口建议为 POST /api/v1/client-events/batch，生产环境默认批量上报"。
     * 单次上限 {@value #MAX_BATCH_SIZE} 条，超出截断并记录 WARN。
     * 每条事件分配 ULID 主键；tenantId 优先从 CurrentUserContext 获取，未登录兜底 "public"。
     *
     * @param events 端侧上报的事件列表（eventId/eventName/occurredTime/sessionId/route/platform/appVersion 必填）
     * @return 实际入库条数
     */
    @Transactional
    public int batchInsert(List<ClientEvent> events) {
        if (events == null || events.isEmpty()) {
            return 0;
        }

        String tenantId = resolveTenantId();
        String userId = CurrentUserContext.getUserId();

        if (events.size() > MAX_BATCH_SIZE) {
            log.warn("批量上报事件数 {} 超过上限 {}，截断处理。tenantId={}",
                    events.size(), MAX_BATCH_SIZE, tenantId);
            events = events.subList(0, MAX_BATCH_SIZE);
        }

        List<ClientEvent> toInsert = new ArrayList<>(events.size());
        for (ClientEvent event : events) {
            // 必填字段校验：eventName/sessionId/route/platform/appVersion
            if (event.getEventName() == null || event.getEventName().isBlank()
                    || event.getSessionId() == null || event.getSessionId().isBlank()
                    || event.getRoute() == null || event.getRoute().isBlank()
                    || event.getPlatform() == null || event.getPlatform().isBlank()
                    || event.getAppVersion() == null || event.getAppVersion().isBlank()) {
                log.warn("丢弃必填字段缺失的埋点事件: eventName={}, tenantId={}",
                        event.getEventName(), tenantId);
                continue;
            }
            event.setId(IdGenerator.nextId());
            event.setTenantId(tenantId);
            if (userId != null && !userId.isBlank()) {
                event.setCreatedBy(userId);
            }
            toInsert.add(event);
        }

        if (toInsert.isEmpty()) {
            return 0;
        }

        // MyBatis-Plus 无直接批量插入，逐条 insert（第一版数据量小，后续可优化为 batch executor）
        for (ClientEvent event : toInsert) {
            clientEventMapper.insert(event);
        }
        log.debug("批量上报埋点事件入库 {} 条，tenantId={}", toInsert.size(), tenantId);
        return toInsert.size();
    }

    /**
     * 分页查询端侧埋点事件（用于验证和看板数据源）。
     * 支持按 eventName/route/platform/result 过滤。
     *
     * @param request   分页参数
     * @param eventName 事件名过滤（精确匹配，可选）
     * @param route     路由过滤（模糊匹配，可选）
     * @param platform  平台过滤（精确匹配，可选）
     * @param result    结果过滤（精确匹配，可选）
     */
    public PageResult<ClientEvent> pageEvents(PageRequest request, String eventName,
                                               String route, String platform, String result) {
        String tenantId = resolveTenantId();
        LambdaQueryWrapper<ClientEvent> wrapper = new LambdaQueryWrapper<ClientEvent>()
                .eq(ClientEvent::getTenantId, tenantId)
                .eq(eventName != null && !eventName.isBlank(), ClientEvent::getEventName, eventName)
                .like(route != null && !route.isBlank(), ClientEvent::getRoute, route)
                .eq(platform != null && !platform.isBlank(), ClientEvent::getPlatform, platform)
                .eq(result != null && !result.isBlank(), ClientEvent::getResult, result)
                .orderByDesc(ClientEvent::getOccurredTime);
        Page<ClientEvent> page = clientEventMapper.selectPage(
                new Page<>(request.page(), request.size()), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), request.page(), request.size());
    }

    /**
     * 解析租户 ID：优先从 CurrentUserContext 获取，未登录兜底 "public"。
     * 设计来源: 94 号文档要求支持未登录页面（如登录页白屏）的埋点上报。
     */
    private String resolveTenantId() {
        String tenantId = CurrentUserContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            return PUBLIC_TENANT;
        }
        return tenantId;
    }
}
