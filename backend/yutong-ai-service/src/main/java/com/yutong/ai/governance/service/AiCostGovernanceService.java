package com.yutong.ai.governance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.id.IdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * AI 成本额度治理服务。设计来源: GA2-45 成本治理闭环
 * <p>
 * 职责:
 * <ul>
 *   <li>调用前校验租户/用户/场景三维额度（单次 + 日 token 上限）</li>
 *   <li>调用后按实际 token 计算成本，写入审计日志并累计日用量</li>
 *   <li>提供当日成本统计供治理看板使用</li>
 * </ul>
 */
@Service
public class AiCostGovernanceService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final String CURRENCY_CNY = "CNY";

    private final AiCostQuotaMapper quotaMapper;
    private final AiCostQuotaUsageMapper usageMapper;
    private final AiProviderMapper providerMapper;
    private final AiCostLogMapper costLogMapper;
    private final AiAuditService auditService;
    private final AiModelPriceResolver priceResolver;

    public AiCostGovernanceService(AiCostQuotaMapper quotaMapper,
                                   AiCostQuotaUsageMapper usageMapper,
                                   AiProviderMapper providerMapper,
                                   AiCostLogMapper costLogMapper,
                                   AiAuditService auditService,
                                   AiModelPriceResolver priceResolver) {
        this.quotaMapper = quotaMapper;
        this.usageMapper = usageMapper;
        this.providerMapper = providerMapper;
        this.costLogMapper = costLogMapper;
        this.auditService = auditService;
        this.priceResolver = priceResolver;
    }

    /**
     * 调用前额度校验。
     *
     * @param scenario           使用场景
     * @param providerCode       供应商编码
     * @param modelCode          模型编码
     * @param estimatedTotalTokens 预估总 token 数
     * @throws BusinessException 额度不足时抛出 AIG_QUOTA_EXCEEDED
     */
    public void checkQuotaBeforeCall(String scenario, String providerCode, String modelCode,
                                     int estimatedTotalTokens) {
        String tenantId = CurrentUserContext.getTenantId();
        String userId = CurrentUserContext.getUserId();
        String actualScenario = normalizeScenario(scenario);
        LocalDate today = LocalDate.now(ZONE);

        LambdaQueryWrapper<AiCostQuota> wrapper = new LambdaQueryWrapper<AiCostQuota>()
                .eq(AiCostQuota::getTenantId, tenantId)
                .eq(AiCostQuota::getEnabled, true)
                .and(w -> w
                        .eq(AiCostQuota::getQuotaScope, AiCostQuota.SCOPE_TENANT)
                        .eq(AiCostQuota::getScopeKey, tenantId)
                        .or()
                        .eq(AiCostQuota::getQuotaScope, AiCostQuota.SCOPE_USER)
                        .eq(AiCostQuota::getScopeKey, userId)
                        .or()
                        .eq(AiCostQuota::getQuotaScope, AiCostQuota.SCOPE_SCENARIO)
                        .eq(AiCostQuota::getScopeKey, actualScenario));

        List<AiCostQuota> quotas = quotaMapper.selectList(wrapper);
        for (AiCostQuota quota : quotas) {
            if (!isApplicable(quota, modelCode)) {
                continue;
            }

            Integer singleCallLimit = quota.getSingleCallTokenLimit();
            if (singleCallLimit != null && estimatedTotalTokens > singleCallLimit) {
                throw new BusinessException(ErrorCode.AIG_QUOTA_EXCEEDED,
                        String.format("AI 调用单次 token 超限: scope=%s, key=%s, model=%s, limit=%d, estimated=%d",
                                quota.getQuotaScope(), quota.getScopeKey(), modelCode,
                                singleCallLimit, estimatedTotalTokens));
            }

            Long dailyLimit = quota.getDailyTokenLimit();
            if (dailyLimit != null) {
                Long used = usageMapper.sumTokenUsed(tenantId, quota.getQuotaScope(), quota.getScopeKey(),
                        quota.getModelCode(), today);
                if (used == null) {
                    used = 0L;
                }
                if (used + estimatedTotalTokens > dailyLimit) {
                    throw new BusinessException(ErrorCode.AIG_QUOTA_EXCEEDED,
                            String.format("AI 调用日 token 额度不足: scope=%s, key=%s, model=%s, limit=%d, used=%d, estimated=%d",
                                    quota.getQuotaScope(), quota.getScopeKey(), modelCode,
                                    dailyLimit, used, estimatedTotalTokens));
                }
            }
        }
    }

    private boolean isApplicable(AiCostQuota quota, String modelCode) {
        String quotaModel = quota.getModelCode();
        return quotaModel == null || quotaModel.isBlank() || quotaModel.equals(modelCode);
    }

    private String normalizeScenario(String scenario) {
        return (scenario == null || scenario.isBlank()) ? "DEFAULT" : scenario;
    }

    /**
     * 调用后成本记录与用量累计。
     *
     * @param scenario       使用场景
     * @param providerCode   供应商编码
     * @param modelCode      模型编码
     * @param conversationId 关联会话 ID
     * @param tokenInput     输入 token 数
     * @param tokenOutput    输出 token 数
     * @param latencyMs      响应延迟（毫秒）
     * @param result         调用结果
     */
    @Transactional
    public void recordCostAfterCall(String scenario, String providerCode, String modelCode,
                                    String conversationId, int tokenInput, int tokenOutput,
                                    int latencyMs, String result) {
        String tenantId = CurrentUserContext.getTenantId();
        String userId = CurrentUserContext.getUserId();
        String actualScenario = normalizeScenario(scenario);

        BigDecimal costAmount = computeCost(providerCode, modelCode, tokenInput, tokenOutput);

        auditService.recordCost(providerCode, modelCode, actualScenario, userId, conversationId,
                tokenInput, tokenOutput, costAmount, CURRENCY_CNY, latencyMs, result);

        LocalDate today = LocalDate.now(ZONE);
        long totalTokens = (long) tokenInput + (long) tokenOutput;
        incrementUsage(tenantId, AiCostQuota.SCOPE_TENANT, tenantId, modelCode, today, totalTokens, costAmount);
        incrementUsage(tenantId, AiCostQuota.SCOPE_USER, userId, modelCode, today, totalTokens, costAmount);
        incrementUsage(tenantId, AiCostQuota.SCOPE_SCENARIO, actualScenario, modelCode, today, totalTokens, costAmount);
    }

    private BigDecimal computeCost(String providerCode, String modelCode,
                                   int tokenInput, int tokenOutput) {
        String modelListJson = resolveModelListJson(providerCode);
        AiModelPriceResolver.Price price = priceResolver.resolvePrice(modelCode, modelListJson);

        BigDecimal inputCost = price.inputCnyPer1k()
                .multiply(BigDecimal.valueOf(tokenInput))
                .divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP);
        BigDecimal outputCost = price.outputCnyPer1k()
                .multiply(BigDecimal.valueOf(tokenOutput))
                .divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP);
        return inputCost.add(outputCost).setScale(4, RoundingMode.HALF_UP);
    }

    private String resolveModelListJson(String providerCode) {
        if (providerCode == null || providerCode.isBlank()) {
            return null;
        }
        AiProvider provider = providerMapper.selectOne(
                new LambdaQueryWrapper<AiProvider>()
                        .eq(AiProvider::getTenantId, CurrentUserContext.getTenantId())
                        .eq(AiProvider::getProviderCode, providerCode));
        return provider == null ? null : provider.getModelListJson();
    }

    private void incrementUsage(String tenantId, String quotaScope, String scopeKey,
                                String modelCode, LocalDate usageDate, long tokenDelta,
                                BigDecimal costDelta) {
        LambdaQueryWrapper<AiCostQuotaUsage> wrapper = new LambdaQueryWrapper<AiCostQuotaUsage>()
                .eq(AiCostQuotaUsage::getTenantId, tenantId)
                .eq(AiCostQuotaUsage::getQuotaScope, quotaScope)
                .eq(AiCostQuotaUsage::getScopeKey, scopeKey)
                .eq(AiCostQuotaUsage::getModelCode, modelCode)
                .eq(AiCostQuotaUsage::getUsageDate, usageDate);
        AiCostQuotaUsage usage = usageMapper.selectOne(wrapper);
        if (usage == null) {
            usage = new AiCostQuotaUsage();
            usage.setId(IdGenerator.nextId());
            usage.setTenantId(tenantId);
            usage.setQuotaScope(quotaScope);
            usage.setScopeKey(scopeKey);
            usage.setModelCode(modelCode);
            usage.setUsageDate(usageDate);
            usage.setTokenUsed(tokenDelta);
            usage.setCostUsed(costDelta);
            usageMapper.insert(usage);
        } else {
            usage.setTokenUsed(usage.getTokenUsed() + tokenDelta);
            usage.setCostUsed(usage.getCostUsed().add(costDelta));
            usageMapper.updateById(usage);
        }
    }

    /**
     * 获取租户当日成本统计。
     *
     * @param tenantId 租户 ID
     * @return 当日 token 总量、成本总额、调用次数
     */
    public TodayStats getTodayStats(String tenantId) {
        LocalDate today = LocalDate.now(ZONE);
        LambdaQueryWrapper<AiCostLog> wrapper = new LambdaQueryWrapper<AiCostLog>()
                .eq(AiCostLog::getTenantId, tenantId)
                .eq(AiCostLog::getResult, "SUCCESS")
                .ge(AiCostLog::getCreatedTime, today.atStartOfDay(ZONE).toOffsetDateTime())
                .lt(AiCostLog::getCreatedTime, today.plusDays(1).atStartOfDay(ZONE).toOffsetDateTime());
        wrapper.select(AiCostLog::getTokenInput, AiCostLog::getTokenOutput, AiCostLog::getCostAmount);

        List<AiCostLog> logs = costLogMapper.selectList(wrapper);
        long totalTokens = 0L;
        BigDecimal totalCost = BigDecimal.ZERO;
        for (AiCostLog log : logs) {
            totalTokens += (log.getTokenInput() == null ? 0 : log.getTokenInput())
                    + (log.getTokenOutput() == null ? 0 : log.getTokenOutput());
            totalCost = totalCost.add(log.getCostAmount() == null ? BigDecimal.ZERO : log.getCostAmount());
        }
        return new TodayStats(totalTokens, totalCost, logs.size());
    }

    /**
     * 当日成本统计。
     */
    public record TodayStats(long totalTokens, BigDecimal totalCost, long callCount) {
    }
}
