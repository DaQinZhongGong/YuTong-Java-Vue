package com.yutong.ai.governance.service;

import com.yutong.ai.chat.service.llm.AiModelPriceResolver;
import com.yutong.ai.gateway.domain.AiCostLog;
import com.yutong.ai.gateway.domain.AiProvider;
import com.yutong.ai.gateway.mapper.AiCostLogMapper;
import com.yutong.ai.gateway.mapper.AiProviderMapper;
import com.yutong.ai.gateway.service.AiAuditService;
import com.yutong.ai.governance.domain.AiCostQuota;
import com.yutong.ai.governance.domain.AiCostQuotaUsage;
import com.yutong.ai.governance.mapper.AiCostQuotaMapper;
import com.yutong.ai.governance.mapper.AiCostQuotaUsageMapper;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.auth.DataScopeType;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AI 成本额度治理服务单元测试。设计来源: GA2-45 成本治理闭环
 */
class AiCostGovernanceServiceTest {

    private static final String TENANT_ID = "tenant-1";
    private static final String USER_ID = "user-1";
    private static final String SCENARIO = "CHAT";
    private static final String MODEL = "glm-4-flash";

    private AiCostQuotaMapper quotaMapper;
    private AiCostQuotaUsageMapper usageMapper;
    private AiProviderMapper providerMapper;
    private AiCostLogMapper costLogMapper;
    private AiAuditService auditService;
    private AiModelPriceResolver priceResolver;
    private AiCostGovernanceService service;

    @BeforeEach
    void setUp() {
        quotaMapper = Mockito.mock(AiCostQuotaMapper.class);
        usageMapper = Mockito.mock(AiCostQuotaUsageMapper.class);
        providerMapper = Mockito.mock(AiProviderMapper.class);
        costLogMapper = Mockito.mock(AiCostLogMapper.class);
        auditService = Mockito.mock(AiAuditService.class);
        priceResolver = Mockito.mock(AiModelPriceResolver.class);
        service = new AiCostGovernanceService(quotaMapper, usageMapper, providerMapper,
                costLogMapper, auditService, priceResolver);
        CurrentUserContext.set(USER_ID, TENANT_ID, "admin", "dept-1", "dept-1", DataScopeType.ALL);
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    @DisplayName("无额度限制时校验通过")
    void checkQuotaPassesWhenNoQuota() {
        when(quotaMapper.selectList(any())).thenReturn(List.of());

        assertDoesNotThrow(() ->
                service.checkQuotaBeforeCall(SCENARIO, "zhipu", MODEL, 1000));
    }

    @Test
    @DisplayName("单次调用 token 超限抛出 AIG_QUOTA_EXCEEDED")
    void checkQuotaThrowsWhenSingleCallLimitExceeded() {
        AiCostQuota quota = createQuota(AiCostQuota.SCOPE_TENANT, TENANT_ID, MODEL, null, 100);
        when(quotaMapper.selectList(any())).thenReturn(List.of(quota));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.checkQuotaBeforeCall(SCENARIO, "zhipu", MODEL, 101));

        assertEquals(ErrorCode.AIG_QUOTA_EXCEEDED, ex.errorCode());
    }

    @Test
    @DisplayName("日 token 额度不足抛出 AIG_QUOTA_EXCEEDED")
    void checkQuotaThrowsWhenDailyLimitExceeded() {
        AiCostQuota quota = createQuota(AiCostQuota.SCOPE_USER, USER_ID, MODEL, 100L, 1000);
        when(quotaMapper.selectList(any())).thenReturn(List.of(quota));
        when(usageMapper.sumTokenUsed(eq(TENANT_ID), eq(AiCostQuota.SCOPE_USER), eq(USER_ID),
                eq(MODEL), any(LocalDate.class))).thenReturn(50L);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.checkQuotaBeforeCall(SCENARIO, "zhipu", MODEL, 51));

        assertEquals(ErrorCode.AIG_QUOTA_EXCEEDED, ex.errorCode());
    }

    @Test
    @DisplayName("recordCost 计算成本并写入 TENANT/USER/SCENARIO 用量")
    void recordCostComputesAmountAndCreatesUsageRows() {
        AiProvider provider = new AiProvider();
        provider.setProviderCode("zhipu");
        provider.setModelListJson("[{\"code\":\"glm-4-flash\"}]");
        when(providerMapper.selectOne(any())).thenReturn(provider);
        when(priceResolver.resolvePrice(eq(MODEL), any())).thenReturn(
                new AiModelPriceResolver.Price(new BigDecimal("1.0000"), new BigDecimal("2.0000")));
        when(usageMapper.selectOne(any())).thenReturn(null);

        service.recordCostAfterCall(SCENARIO, "zhipu", MODEL, "conv-1", 1000, 500, 200, "SUCCESS");

        // 成本 = 1000 * 1 / 1000 + 500 * 2 / 1000 = 2.0000
        ArgumentCaptor<BigDecimal> costCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(auditService).recordCost(eq("zhipu"), eq(MODEL), eq(SCENARIO), eq(USER_ID), eq("conv-1"),
                eq(1000), eq(500), costCaptor.capture(), eq("CNY"), eq(200), eq("SUCCESS"));
        assertEquals(0, new BigDecimal("2.0000").compareTo(costCaptor.getValue()));

        ArgumentCaptor<AiCostQuotaUsage> usageCaptor = ArgumentCaptor.forClass(AiCostQuotaUsage.class);
        verify(usageMapper, times(3)).insert(usageCaptor.capture());
        List<AiCostQuotaUsage> usages = usageCaptor.getAllValues();
        assertEquals(3, usages.size());
        assertTrue(usages.stream().anyMatch(u -> AiCostQuota.SCOPE_TENANT.equals(u.getQuotaScope())));
        assertTrue(usages.stream().anyMatch(u -> AiCostQuota.SCOPE_USER.equals(u.getQuotaScope())));
        assertTrue(usages.stream().anyMatch(u -> AiCostQuota.SCOPE_SCENARIO.equals(u.getQuotaScope())));
        for (AiCostQuotaUsage usage : usages) {
            assertEquals(TENANT_ID, usage.getTenantId());
            assertEquals(MODEL, usage.getModelCode());
            assertEquals(1500L, usage.getTokenUsed());
            assertEquals(0, new BigDecimal("2.0000").compareTo(usage.getCostUsed()));
        }
    }

    private AiCostQuota createQuota(String scope, String scopeKey, String modelCode,
                                    Long dailyTokenLimit, Integer singleCallTokenLimit) {
        AiCostQuota quota = new AiCostQuota();
        quota.setQuotaScope(scope);
        quota.setScopeKey(scopeKey);
        quota.setModelCode(modelCode);
        quota.setDailyTokenLimit(dailyTokenLimit);
        quota.setSingleCallTokenLimit(singleCallTokenLimit);
        quota.setEnabled(true);
        return quota;
    }
}
