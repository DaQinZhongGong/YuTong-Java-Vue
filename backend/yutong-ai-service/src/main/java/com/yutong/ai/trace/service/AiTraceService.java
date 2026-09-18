package com.yutong.ai.trace.service;

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
import com.yutong.ai.trace.domain.AiTraceNode;
import com.yutong.ai.trace.domain.AiTraceRun;
import com.yutong.ai.trace.dto.AiTraceDashboardVO;
import com.yutong.ai.trace.mapper.AiTraceNodeMapper;
import com.yutong.ai.trace.mapper.AiTraceRunMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 链路追踪服务。设计来源: V043 ai_trace_run/ai_trace_node — P0 平价能力
 * <p>职责: startRun / addNode / finishRun / query，租户隔离 + DataScope 意识。
 */
@Service
public class AiTraceService {

    private static final Logger log = LoggerFactory.getLogger(AiTraceService.class);
    public static final String RESOURCE_CODE = "ai:trace";

    private final AiTraceRunMapper runMapper;
    private final AiTraceNodeMapper nodeMapper;
    private final DataScopeResolver dataScopeResolver;

    public AiTraceService(AiTraceRunMapper runMapper,
                          AiTraceNodeMapper nodeMapper,
                          DataScopeResolver dataScopeResolver) {
        this.runMapper = runMapper;
        this.nodeMapper = nodeMapper;
        this.dataScopeResolver = dataScopeResolver;
    }

    @Transactional
    public AiTraceRun startRun(AiTraceRun run) {
        if (run.getTraceType() == null || run.getTraceType().isBlank()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "traceType 不能为空");
        }
        if (run.getId() == null || run.getId().isBlank()) {
            run.setId(IdGenerator.nextId());
        }
        run.setTenantId(CurrentUserContext.getTenantId());
        run.setCreatedBy(CurrentUserContext.getUserId());
        run.setStatus(AiTraceRun.STATUS_RUNNING);
        run.setVersion(0);
        if (run.getInputJson() != null && run.getInputJson().isBlank()) run.setInputJson(null);
        if (run.getOutputJson() != null && run.getOutputJson().isBlank()) run.setOutputJson(null);
        runMapper.insert(run);
        log.info("trace run started: id={} type={} tenantId={}", run.getId(), run.getTraceType(), run.getTenantId());
        return run;
    }

    @Transactional
    public AiTraceNode addNode(AiTraceNode node) {
        if (node.getRunId() == null || node.getRunId().isBlank()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "runId 不能为空");
        }
        if (node.getNodeType() == null || node.getNodeType().isBlank()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "nodeType 不能为空");
        }
        AiTraceRun run = runMapper.selectById(node.getRunId());
        if (run == null) {
            throw new ResourceNotFoundException("追踪主表不存在: " + node.getRunId());
        }
        if (!run.getTenantId().equals(CurrentUserContext.getTenantId())) {
            throw new ResourceNotFoundException("追踪主表不存在: " + node.getRunId());
        }
        if (node.getId() == null || node.getId().isBlank()) {
            node.setId(IdGenerator.nextId());
        }
        node.setTenantId(CurrentUserContext.getTenantId());
        node.setCreatedBy(CurrentUserContext.getUserId());
        if (node.getStatus() == null || node.getStatus().isBlank()) {
            node.setStatus(AiTraceNode.STATUS_RUNNING);
        }
        node.setVersion(0);
        if (node.getInputJson() != null && node.getInputJson().isBlank()) node.setInputJson(null);
        if (node.getOutputJson() != null && node.getOutputJson().isBlank()) node.setOutputJson(null);
        nodeMapper.insert(node);
        log.debug("trace node added: id={} runId={} nodeType={} status={}", node.getId(), node.getRunId(), node.getNodeType(), node.getStatus());
        return node;
    }

    @Transactional
    public AiTraceRun finishRun(String runId, String status, String outputJson, Integer latencyMs, String errorMessage) {
        AiTraceRun run = runMapper.selectById(runId);
        if (run == null) {
            throw new ResourceNotFoundException("追踪主表不存在: " + runId);
        }
        if (!run.getTenantId().equals(CurrentUserContext.getTenantId())) {
            throw new ResourceNotFoundException("追踪主表不存在: " + runId);
        }
        if (status != null && !status.isBlank()) {
            run.setStatus(status);
        } else {
            run.setStatus(AiTraceRun.STATUS_SUCCESS);
        }
        if (outputJson != null) run.setOutputJson(outputJson.isBlank() ? null : outputJson);
        if (latencyMs != null) run.setLatencyMs(latencyMs);
        if (errorMessage != null) run.setErrorMessage(errorMessage);
        run.setUpdatedBy(CurrentUserContext.getUserId());
        runMapper.updateById(run);
        log.info("trace run finished: id={} status={} latencyMs={}", runId, run.getStatus(), latencyMs);
        return run;
    }

    public PageResult<AiTraceRun> queryRuns(PageRequest request, String traceType, String status) {
        DataScope scope = dataScopeResolver.resolve(RESOURCE_CODE);
        LambdaQueryWrapper<AiTraceRun> wrapper = new LambdaQueryWrapper<AiTraceRun>()
                .eq(AiTraceRun::getTenantId, CurrentUserContext.getTenantId())
                .eq(traceType != null && !traceType.isBlank(), AiTraceRun::getTraceType, traceType)
                .eq(status != null && !status.isBlank(), AiTraceRun::getStatus, status)
                .orderByDesc(AiTraceRun::getCreatedTime);
        applyDataScope(wrapper, scope);
        Page<AiTraceRun> page = runMapper.selectPage(new Page<>(request.page(), request.size()), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), request.page(), request.size());
    }

    public AiTraceRun getRun(String id) {
        AiTraceRun run = runMapper.selectById(id);
        if (run == null) {
            throw new ResourceNotFoundException("追踪主表不存在: " + id);
        }
        if (!run.getTenantId().equals(CurrentUserContext.getTenantId())) {
            throw new ResourceNotFoundException("追踪主表不存在: " + id);
        }
        return run;
    }

    public List<AiTraceNode> listNodes(String runId) {
        AiTraceRun run = getRun(runId);
        return nodeMapper.selectList(new LambdaQueryWrapper<AiTraceNode>()
                .eq(AiTraceNode::getTenantId, CurrentUserContext.getTenantId())
                .eq(AiTraceNode::getRunId, run.getId())
                .orderByAsc(AiTraceNode::getCreatedTime));
    }

    public AiTraceNode getNode(String nodeId) {
        AiTraceNode node = nodeMapper.selectById(nodeId);
        if (node == null) {
            throw new ResourceNotFoundException("追踪节点不存在: " + nodeId);
        }
        if (!node.getTenantId().equals(CurrentUserContext.getTenantId())) {
            throw new ResourceNotFoundException("追踪节点不存在: " + nodeId);
        }
        return node;
    }

    /**
     * 监控大盘聚合。设计来源: P2-F 链路追踪监控大盘 (M-3)。
     *
     * <p>口径: SQL 侧 GROUP BY 聚合 (不拉全量行); 趋势按日起点补零;
     * 数据权限与 {@link #queryRuns} 同规则 (ALL/TENANT 全量, 其余收敛到本人,
     * 无用户时空结果); 日期按 DB 时区 DATE() 截断。
     *
     * <p>失败关闭: days 非空时须 1~93, 否则拒绝; 无租户上下文拒绝。
     *
     * @param days      近 N 天 (可空默认 7, 上限 93)
     * @param traceType 链路类型过滤 (可空 = 全部)
     */
    public AiTraceDashboardVO getDashboard(Integer days, String traceType) {
        int actualDays = (days == null) ? 7 : days;
        if (actualDays < 1 || actualDays > 93) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    "查询天数非法 (1~93): " + days);
        }
        String tenantId = CurrentUserContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "缺失租户上下文, 无法查询大盘");
        }
        String type = (traceType == null || traceType.isBlank()) ? null : traceType.trim();
        String createdBy = resolveDashboardUser();
        LocalDate endDate = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        LocalDate startDate = endDate.minusDays(actualDays - 1);

        AiTraceDashboardVO vo = new AiTraceDashboardVO();
        vo.setStartDate(startDate);
        vo.setEndDate(endDate);
        if (createdBy != null && createdBy.isBlank()) {
            // 数据权限收敛到本人但无用户 (与 queryRuns 的 1=0 同语义): 空结果
            vo.setSummary(new AiTraceDashboardVO.Summary());
            vo.setDaily(fillDaily(startDate, endDate, List.of()));
            vo.setByType(List.of());
            vo.setRecentErrors(List.of());
            return vo;
        }

        var startTs = startDate.atStartOfDay(ZoneId.of("Asia/Shanghai")).toOffsetDateTime();
        var endTs = endDate.plusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toOffsetDateTime();
        List<AiTraceDashboardVO.DailyAggRow> dailyRows =
                runMapper.statDaily(tenantId, startTs, endTs, type, createdBy);
        List<AiTraceDashboardVO.TypeAggRow> typeRows =
                runMapper.statByType(tenantId, startTs, endTs, type, createdBy);

        vo.setDaily(fillDaily(startDate, endDate, dailyRows));
        vo.setByType(toTypeStats(typeRows));
        vo.setSummary(toSummary(dailyRows));
        vo.setRecentErrors(runMapper.recentErrors(tenantId, type, createdBy, 10));
        return vo;
    }

    /**
     * 数据权限用户收敛: ALL/TENANT (或空) → null 不过滤; 其余 → 本人 userId。
     * 与 {@link #applyDataScope} 同规则 (queryRuns 把 DEPT 等同样收敛到 createdBy)。
     */
    private String resolveDashboardUser() {
        DataScope scope = dataScopeResolver.resolve(RESOURCE_CODE);
        if (scope == null) return null;
        if (scope.scopeType() == DataScopeType.ALL || scope.scopeType() == DataScopeType.TENANT) return null;
        return scope.userId();
    }

    private static List<AiTraceDashboardVO.DailyPoint> fillDaily(LocalDate start, LocalDate end,
                                                                  List<AiTraceDashboardVO.DailyAggRow> rows) {
        Map<LocalDate, Map<String, AiTraceDashboardVO.DailyAggRow>> byDayStatus = new LinkedHashMap<>();
        for (AiTraceDashboardVO.DailyAggRow r : rows) {
            if (r.getDay() == null) continue;
            byDayStatus.computeIfAbsent(r.getDay(), k -> new LinkedHashMap<>())
                    .put(r.getStatus(), r);
        }
        List<AiTraceDashboardVO.DailyPoint> out = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            Map<String, AiTraceDashboardVO.DailyAggRow> m =
                    byDayStatus.getOrDefault(d, Map.of());
            AiTraceDashboardVO.DailyPoint p = new AiTraceDashboardVO.DailyPoint();
            p.setDay(d);
            long total = 0L;
            long success = 0L;
            long failed = 0L;
            long latencySum = 0L;
            long latencyRuns = 0L;
            for (Map.Entry<String, AiTraceDashboardVO.DailyAggRow> e : m.entrySet()) {
                AiTraceDashboardVO.DailyAggRow r = e.getValue();
                long cnt = r.getCnt() == null ? 0L : r.getCnt();
                total += cnt;
                if (AiTraceRun.STATUS_SUCCESS.equals(e.getKey())) success += cnt;
                if (AiTraceRun.STATUS_FAILED.equals(e.getKey())) failed += cnt;
                latencySum += (r.getSumLatency() == null ? 0L : r.getSumLatency());
                latencyRuns += (r.getLatencyRuns() == null ? 0L : r.getLatencyRuns());
            }
            p.setTotal(total);
            p.setSuccess(success);
            p.setFailed(failed);
            p.setAvgLatencyMs(latencyRuns > 0 ? latencySum / latencyRuns : null);
            out.add(p);
        }
        return out;
    }

    private static List<AiTraceDashboardVO.TypeStat> toTypeStats(List<AiTraceDashboardVO.TypeAggRow> rows) {
        List<AiTraceDashboardVO.TypeStat> out = new ArrayList<>(rows.size());
        for (AiTraceDashboardVO.TypeAggRow r : rows) {
            AiTraceDashboardVO.TypeStat s = new AiTraceDashboardVO.TypeStat();
            s.setTraceType(r.getTraceType());
            long total = r.getTotal() == null ? 0L : r.getTotal();
            long failed = r.getFailed() == null ? 0L : r.getFailed();
            s.setTotal(total);
            s.setFailed(failed);
            s.setErrorRate(total > 0
                    ? BigDecimal.valueOf(failed * 100.0 / total).setScale(1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);
            s.setAvgLatencyMs(r.getAvgLatency() == null
                    ? null : r.getAvgLatency().setScale(0, RoundingMode.HALF_UP).longValue());
            out.add(s);
        }
        return out;
    }

    private static AiTraceDashboardVO.Summary toSummary(List<AiTraceDashboardVO.DailyAggRow> rows) {
        AiTraceDashboardVO.Summary s = new AiTraceDashboardVO.Summary();
        long total = 0L;
        long success = 0L;
        long failed = 0L;
        long running = 0L;
        long latencySum = 0L;
        long latencyRuns = 0L;
        Integer maxLatency = null;
        for (AiTraceDashboardVO.DailyAggRow r : rows) {
            long cnt = r.getCnt() == null ? 0L : r.getCnt();
            total += cnt;
            if (AiTraceRun.STATUS_SUCCESS.equals(r.getStatus())) success += cnt;
            else if (AiTraceRun.STATUS_FAILED.equals(r.getStatus())) failed += cnt;
            else if (AiTraceRun.STATUS_RUNNING.equals(r.getStatus())) running += cnt;
            latencySum += (r.getSumLatency() == null ? 0L : r.getSumLatency());
            latencyRuns += (r.getLatencyRuns() == null ? 0L : r.getLatencyRuns());
            if (r.getMaxLatency() != null && (maxLatency == null || r.getMaxLatency() > maxLatency)) {
                maxLatency = r.getMaxLatency();
            }
        }
        s.setTotalRuns(total);
        s.setSuccessRuns(success);
        s.setFailedRuns(failed);
        s.setRunningRuns(running);
        s.setSuccessRate(total > 0
                ? BigDecimal.valueOf(success * 100.0 / total).setScale(1, RoundingMode.HALF_UP)
                : null);
        s.setAvgLatencyMs(latencyRuns > 0 ? latencySum / latencyRuns : null);
        s.setMaxLatencyMs(maxLatency);
        return s;
    }

    private void applyDataScope(LambdaQueryWrapper<AiTraceRun> wrapper, DataScope scope) {        if (scope == null) return;
        if (scope.scopeType() == DataScopeType.ALL || scope.scopeType() == DataScopeType.TENANT) return;
        String userId = scope.userId();
        if (userId == null || userId.isBlank()) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.eq(AiTraceRun::getCreatedBy, userId);
    }
}
