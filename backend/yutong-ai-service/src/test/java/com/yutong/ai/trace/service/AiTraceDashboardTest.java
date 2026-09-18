package com.yutong.ai.trace.service;

import com.yutong.ai.trace.domain.AiTraceRun;
import com.yutong.ai.trace.dto.AiTraceDashboardVO;
import com.yutong.ai.trace.mapper.AiTraceNodeMapper;
import com.yutong.ai.trace.mapper.AiTraceRunMapper;
import com.yutong.auth.DataScopeResolver;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.auth.DataScope;
import com.yutong.common.auth.DataScopeType;
import com.yutong.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * 链路追踪监控大盘单元测试。设计来源: P2-F 链路追踪监控大盘 (M-3)。
 *
 * 覆盖:
 *  - 默认 7 天 + 趋势补零连续
 *  - 汇总数学 (成功率/平均耗时/最大耗时)
 *  - 分类型出错率
 *  - days 非法 / 无租户失败关闭
 *  - SELF 数据权限收敛到本人
 */
class AiTraceDashboardTest {

    private static final String TENANT_ID = "tenant-1";
    private static final String USER_ID = "user-1";

    private AiTraceRunMapper runMapper;
    private DataScopeResolver dataScopeResolver;
    private AiTraceService service;

    @BeforeEach
    void setUp() {
        runMapper = Mockito.mock(AiTraceRunMapper.class);
        AiTraceNodeMapper nodeMapper = Mockito.mock(AiTraceNodeMapper.class);
        dataScopeResolver = Mockito.mock(DataScopeResolver.class);
        service = new AiTraceService(runMapper, nodeMapper, dataScopeResolver);
        CurrentUserContext.set(USER_ID, TENANT_ID, "admin", "dept-1", "dept-1", DataScopeType.ALL);
        when(dataScopeResolver.resolve(any())).thenReturn(DataScope.all(USER_ID, TENANT_ID, "ai:trace"));
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    private static AiTraceDashboardVO.DailyAggRow dailyRow(LocalDate day, String status, long cnt,
                                                           long sumLatency, long latencyRuns, int max) {
        AiTraceDashboardVO.DailyAggRow r = new AiTraceDashboardVO.DailyAggRow();
        r.setDay(day);
        r.setStatus(status);
        r.setCnt(cnt);
        r.setAvgLatency(BigDecimal.valueOf((double) sumLatency / latencyRuns));
        r.setSumLatency(sumLatency);
        r.setLatencyRuns(latencyRuns);
        r.setMaxLatency(max);
        return r;
    }

    private static AiTraceDashboardVO.TypeAggRow typeRow(String type, long total, long failed, double avg) {
        AiTraceDashboardVO.TypeAggRow r = new AiTraceDashboardVO.TypeAggRow();
        r.setTraceType(type);
        r.setTotal(total);
        r.setFailed(failed);
        r.setAvgLatency(BigDecimal.valueOf(avg));
        return r;
    }

    @Test
    @DisplayName("默认 7 天趋势连续补零 + 汇总数学正确")
    void defaultsAndMath() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        when(runMapper.statDaily(any(), any(), any(), isNull(), isNull())).thenReturn(List.of(
                dailyRow(today.minusDays(1), AiTraceRun.STATUS_SUCCESS, 3L, 3000L, 3L, 1500),
                dailyRow(today.minusDays(1), AiTraceRun.STATUS_FAILED, 1L, 4000L, 1L, 4000),
                dailyRow(today, AiTraceRun.STATUS_SUCCESS, 2L, 1000L, 2L, 600)));
        when(runMapper.statByType(any(), any(), any(), isNull(), isNull())).thenReturn(List.of(
                typeRow(AiTraceRun.TYPE_LLM, 4L, 1L, 1500.0)));
        when(runMapper.recentErrors(any(), isNull(), isNull(), anyInt())).thenReturn(List.of());

        AiTraceDashboardVO vo = service.getDashboard(null, null);

        assertNotNull(vo.getDaily());
        assertEquals(7, vo.getDaily().size());
        assertEquals(today.minusDays(6), vo.getDaily().get(0).getDay());
        assertEquals(today, vo.getDaily().get(6).getDay());
        // 有数据的两天
        assertEquals(4L, vo.getDaily().get(5).getTotal());
        assertEquals(3L, vo.getDaily().get(5).getSuccess());
        assertEquals(1L, vo.getDaily().get(5).getFailed());
        // 补零天
        assertEquals(0L, vo.getDaily().get(0).getTotal());
        assertNull(vo.getDaily().get(0).getAvgLatencyMs());
        // 汇总: 6 次 / 成功 5 / 失败 1 / 成功率 83.3 / 平均 (3000+4000+1000)/6=1333 / 最大 4000
        assertEquals(6L, vo.getSummary().getTotalRuns());
        assertEquals(5L, vo.getSummary().getSuccessRuns());
        assertEquals(1L, vo.getSummary().getFailedRuns());
        assertEquals(0, new BigDecimal("83.3").compareTo(vo.getSummary().getSuccessRate()));
        assertEquals(1333L, vo.getSummary().getAvgLatencyMs());
        assertEquals(4000, vo.getSummary().getMaxLatencyMs());
        // 分类型
        assertEquals(1, vo.getByType().size());
        assertEquals(0, new BigDecimal("25.0").compareTo(vo.getByType().get(0).getErrorRate()));
    }

    @Test
    @DisplayName("空结果汇总为零 + 成功率为 null")
    void emptySummary() {
        when(runMapper.statDaily(any(), any(), any(), isNull(), isNull())).thenReturn(List.of());
        when(runMapper.statByType(any(), any(), any(), isNull(), isNull())).thenReturn(List.of());
        when(runMapper.recentErrors(any(), isNull(), isNull(), anyInt())).thenReturn(List.of());

        AiTraceDashboardVO vo = service.getDashboard(7, null);

        assertEquals(0L, vo.getSummary().getTotalRuns());
        assertNull(vo.getSummary().getSuccessRate());
        assertNull(vo.getSummary().getAvgLatencyMs());
        assertEquals(7, vo.getDaily().size());
    }

    @Test
    @DisplayName("days 非法失败关闭")
    void illegalDaysThrows() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.getDashboard(0, null));
        assertTrue(ex.getMessage().contains("1~93"));
        BusinessException ex2 = assertThrows(BusinessException.class, () -> service.getDashboard(94, null));
        assertTrue(ex2.getMessage().contains("1~93"));
    }

    @Test
    @DisplayName("无租户上下文失败关闭")
    void blankTenantThrows() {
        CurrentUserContext.clear();
        BusinessException ex = assertThrows(BusinessException.class, () -> service.getDashboard(null, null));
        assertTrue(ex.getMessage().contains("租户上下文"));
    }

    @Test
    @DisplayName("SELF 数据权限收敛到本人 userId")
    void selfScopeFiltersByUser() {
        when(dataScopeResolver.resolve(any()))
                .thenReturn(DataScope.self(USER_ID, TENANT_ID, "ai:trace"));
        when(runMapper.statDaily(any(), any(), any(), isNull(), org.mockito.ArgumentMatchers.eq(USER_ID)))
                .thenReturn(List.of());
        when(runMapper.statByType(any(), any(), any(), isNull(), org.mockito.ArgumentMatchers.eq(USER_ID)))
                .thenReturn(List.of());
        when(runMapper.recentErrors(any(), isNull(), org.mockito.ArgumentMatchers.eq(USER_ID), anyInt()))
                .thenReturn(List.of());

        AiTraceDashboardVO vo = service.getDashboard(7, null);

        assertEquals(0L, vo.getSummary().getTotalRuns());
        Mockito.verify(runMapper).statDaily(any(), any(), any(), isNull(),
                org.mockito.ArgumentMatchers.eq(USER_ID));
    }
}
