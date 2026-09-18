package com.yutong.ai.governance.service;

import com.yutong.ai.chat.service.llm.AiModelPriceResolver;
import com.yutong.ai.gateway.mapper.AiCostLogMapper;
import com.yutong.ai.gateway.mapper.AiProviderMapper;
import com.yutong.ai.gateway.service.AiAuditService;
import com.yutong.ai.governance.domain.AiCostQuota;
import com.yutong.ai.governance.domain.AiCostQuotaUsage;
import com.yutong.ai.governance.dto.AiUsageDailyVO;
import com.yutong.ai.governance.mapper.AiCostQuotaMapper;
import com.yutong.ai.governance.mapper.AiCostQuotaUsageMapper;
import com.yutong.common.auth.CurrentUserContext;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 用量日报查询单元测试。设计来源: P2-C Token 用量可视化 (M-2)。
 *
 * 覆盖:
 *  - 默认近 30 天 + TENANT 维度
 *  - 非法维度 / 起日晚于止日 / 跨度超 93 天失败关闭
 *  - 无租户上下文失败关闭
 *  - 明细映射 + 总量汇总数学正确
 */
class AiUsageQueryTest {

    private static final String TENANT_ID = "tenant-1";
    private static final String USER_ID = "user-1";

    private AiCostQuotaUsageMapper usageMapper;
    private AiCostGovernanceService service;

    @BeforeEach
    void setUp() {
        AiCostQuotaMapper quotaMapper = Mockito.mock(AiCostQuotaMapper.class);
        usageMapper = Mockito.mock(AiCostQuotaUsageMapper.class);
        AiProviderMapper providerMapper = Mockito.mock(AiProviderMapper.class);
        AiCostLogMapper costLogMapper = Mockito.mock(AiCostLogMapper.class);
        AiAuditService auditService = Mockito.mock(AiAuditService.class);
        AiModelPriceResolver priceResolver = Mockito.mock(AiModelPriceResolver.class);
        service = new AiCostGovernanceService(quotaMapper, usageMapper, providerMapper,
                costLogMapper, auditService, priceResolver);
        CurrentUserContext.set(USER_ID, TENANT_ID, "admin", "dept-1", "dept-1", DataScopeType.ALL);
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    private static AiCostQuotaUsage row(LocalDate date, String scope, String key,
                                        String model, long tokens, String cost) {
        AiCostQuotaUsage u = new AiCostQuotaUsage();
        u.setUsageDate(date);
        u.setQuotaScope(scope);
        u.setScopeKey(key);
        u.setModelCode(model);
        u.setTokenUsed(tokens);
        u.setCostUsed(new BigDecimal(cost));
        return u;
    }

    @Test
    @DisplayName("默认近 30 天 TENANT 明细映射与汇总正确")
    void defaultsAndSummary() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        when(usageMapper.selectList(any())).thenReturn(List.of(
                row(today.minusDays(1), AiCostQuota.SCOPE_TENANT, TENANT_ID, "glm-4-flash", 1000L, "0.5000"),
                row(today, AiCostQuota.SCOPE_TENANT, TENANT_ID, "glm-4-flash", 2000L, "1.0000")));

        AiUsageDailyVO vo = service.queryDailyUsage(null, null, null, null, null);

        assertNotNull(vo.getRows());
        assertEquals(2, vo.getRows().size());
        assertEquals(3000L, vo.getTotalTokens());
        assertEquals(0, new BigDecimal("1.5000").compareTo(vo.getTotalCost()));
        assertEquals(today.minusDays(29), vo.getStartDate());
        assertEquals(today, vo.getEndDate());
        assertEquals("glm-4-flash", vo.getRows().get(0).getModelCode());
    }

    @Test
    @DisplayName("非法维度失败关闭")
    void illegalScopeThrows() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.queryDailyUsage(null, null, "ORG", null, null));
        assertTrue(ex.getMessage().contains("用量维度非法"));
    }

    @Test
    @DisplayName("起日晚于止日失败关闭")
    void startAfterEndThrows() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.queryDailyUsage(today, today.minusDays(1), null, null, null));
        assertTrue(ex.getMessage().contains("不能晚于"));
    }

    @Test
    @DisplayName("跨度超 93 天失败关闭")
    void rangeOver93DaysThrows() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.queryDailyUsage(today.minusDays(93), today, null, null, null));
        assertTrue(ex.getMessage().contains("93"));
    }

    @Test
    @DisplayName("无租户上下文失败关闭")
    void blankTenantThrows() {
        CurrentUserContext.clear();
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.queryDailyUsage(null, null, null, null, null));
        assertTrue(ex.getMessage().contains("租户上下文"));
    }
}
