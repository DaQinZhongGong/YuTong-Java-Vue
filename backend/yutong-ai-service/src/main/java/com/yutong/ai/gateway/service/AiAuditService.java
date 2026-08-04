package com.yutong.ai.gateway.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.ai.gateway.domain.AiCostLog;
import com.yutong.ai.gateway.domain.AiToolCallLog;
import com.yutong.ai.gateway.mapper.AiCostLogMapper;
import com.yutong.ai.gateway.mapper.AiToolCallLogMapper;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.id.IdGenerator;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * AI 审计日志服务。设计来源: 13-AI能力设计、62-可观测性指标日志链路详设
 * 职责: 记录工具调用审计、记录成本日志、分页查询审计记录。
 */
@Service
public class AiAuditService {

    private final AiToolCallLogMapper toolCallLogMapper;
    private final AiCostLogMapper costLogMapper;

    public AiAuditService(AiToolCallLogMapper toolCallLogMapper, AiCostLogMapper costLogMapper) {
        this.toolCallLogMapper = toolCallLogMapper;
        this.costLogMapper = costLogMapper;
    }

    /**
     * 记录工具调用审计日志。
     *
     * @param toolName         工具名称
     * @param riskLevel        风险等级
     * @param userId           调用用户 ID
     * @param inputSummary     输入摘要（脱敏）
     * @param outputSummary    输出摘要（脱敏）
     * @param dataScopeSummary 数据范围摘要
     * @param result           调用结果: SUCCESS / FAILED / DENIED
     * @param errorCode        错误码，可为空
     * @param latencyMs        响应延迟（毫秒）
     * @param traceId          链路追踪 ID
     */
    @Transactional
    public void recordToolCall(String toolName, String riskLevel, String userId,
                               String inputSummary, String outputSummary, String dataScopeSummary,
                               String result, String errorCode, Integer latencyMs, String traceId) {
        AiToolCallLog log = new AiToolCallLog();
        log.setId(IdGenerator.nextId());
        log.setTenantId(CurrentUserContext.getTenantId());
        log.setCreatedBy(CurrentUserContext.getUserId());
        log.setToolName(toolName);
        log.setRiskLevel(riskLevel);
        log.setUserId(userId);
        log.setInputSummary(inputSummary);
        log.setOutputSummary(outputSummary);
        log.setDataScopeSummary(dataScopeSummary);
        log.setResult(result);
        log.setErrorCode(errorCode);
        log.setLatencyMs(latencyMs);
        log.setTraceId(traceId);
        toolCallLogMapper.insert(log);
    }

    /**
     * 记录 AI 调用成本日志。
     *
     * @param providerCode   供应商编码
     * @param modelCode      模型编码
     * @param scenario       使用场景
     * @param userId         调用用户 ID
     * @param conversationId 关联会话 ID
     * @param tokenInput     输入 token 数
     * @param tokenOutput    输出 token 数
     * @param costAmount     成本金额
     * @param currency       币种
     * @param latencyMs      响应延迟（毫秒）
     * @param result         调用结果
     */
    @Transactional
    public void recordCost(String providerCode, String modelCode, String scenario, String userId,
                           String conversationId, Integer tokenInput, Integer tokenOutput,
                           BigDecimal costAmount, String currency, Integer latencyMs, String result) {
        AiCostLog log = new AiCostLog();
        log.setId(IdGenerator.nextId());
        log.setTenantId(CurrentUserContext.getTenantId());
        log.setCreatedBy(CurrentUserContext.getUserId());
        log.setProviderCode(providerCode);
        log.setModelCode(modelCode);
        log.setScenario(scenario);
        log.setUserId(userId);
        log.setConversationId(conversationId);
        log.setTokenInput(tokenInput);
        log.setTokenOutput(tokenOutput);
        log.setCostAmount(costAmount);
        log.setCurrency(currency);
        log.setLatencyMs(latencyMs);
        log.setResult(result);
        costLogMapper.insert(log);
    }

    /**
     * 分页查询工具调用审计日志。
     *
     * @param userId   用户 ID，可为空
     * @param toolName 工具名称，可为空
     * @param result   调用结果，可为空
     */
    public PageResult<AiToolCallLog> pageToolCallLogs(PageRequest request, String userId, String toolName, String result) {
        LambdaQueryWrapper<AiToolCallLog> wrapper = new LambdaQueryWrapper<AiToolCallLog>()
                .eq(AiToolCallLog::getTenantId, CurrentUserContext.getTenantId())
                .eq(userId != null && !userId.isBlank(), AiToolCallLog::getUserId, userId)
                .eq(toolName != null && !toolName.isBlank(), AiToolCallLog::getToolName, toolName)
                .eq(result != null && !result.isBlank(), AiToolCallLog::getResult, result)
                .orderByDesc(AiToolCallLog::getCreatedTime);
        Page<AiToolCallLog> page = toolCallLogMapper.selectPage(
                new Page<>(request.page(), request.size()), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), request.page(), request.size());
    }
}
