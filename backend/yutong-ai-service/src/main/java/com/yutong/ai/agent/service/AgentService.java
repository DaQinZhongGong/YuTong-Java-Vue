package com.yutong.ai.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.ai.agent.domain.AiAgent;
import com.yutong.ai.agent.mapper.AiAgentMapper;
import com.yutong.auth.DataScopeResolver;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.auth.DataScope;
import com.yutong.common.auth.DataScopeType;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessConflictException;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.id.IdGenerator;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Agent CRUD + publish。
 * 设计来源: V039 ai_agent / Phase 4 Agent
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    public static final String RESOURCE_CODE = "ai:agent";

    private static final Set<String> ALLOWED_TYPES = Set.of(
            AiAgent.TYPE_REACT, AiAgent.TYPE_SUPERVISOR, AiAgent.TYPE_SEQUENCE,
            AiAgent.TYPE_PARALLEL, AiAgent.TYPE_CONDITION);

    private final AiAgentMapper agentMapper;
    private final DataScopeResolver dataScopeResolver;

    public AgentService(AiAgentMapper agentMapper, DataScopeResolver dataScopeResolver) {
        this.agentMapper = agentMapper;
        this.dataScopeResolver = dataScopeResolver;
    }

    public PageResult<AiAgent> pageAgents(PageRequest request, String agentCode, String agentType, String status) {
        DataScope scope = dataScopeResolver.resolve(RESOURCE_CODE);
        LambdaQueryWrapper<AiAgent> wrapper = new LambdaQueryWrapper<AiAgent>()
                .eq(AiAgent::getTenantId, CurrentUserContext.getTenantId())
                .like(agentCode != null && !agentCode.isBlank(), AiAgent::getAgentCode, agentCode)
                .eq(agentType != null && !agentType.isBlank(), AiAgent::getAgentType, agentType)
                .eq(status != null && !status.isBlank(), AiAgent::getStatus, status)
                .orderByDesc(AiAgent::getCreatedTime);
        applyDataScope(wrapper, scope);
        Page<AiAgent> page = agentMapper.selectPage(new Page<>(request.page(), request.size()), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), request.page(), request.size());
    }

    public AiAgent getAgent(String id) {
        AiAgent agent = agentMapper.selectById(id);
        if (agent == null) throw new ResourceNotFoundException("Agent 不存在: " + id);
        return agent;
    }

    @Transactional
    public AiAgent saveAgent(AiAgent agent) {
        if (agent.getAgentCode() == null || agent.getAgentCode().isBlank())
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "agentCode 不能为空");
        if (agent.getAgentName() == null || agent.getAgentName().isBlank())
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "agentName 不能为空");
        if (agent.getAgentType() == null || agent.getAgentType().isBlank())
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "agentType 不能为空");
        if (!ALLOWED_TYPES.contains(agent.getAgentType()))
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "agentType 仅支持 react/supervisor/sequence/parallel/condition");
        if (agent.getToolIds() != null && agent.getToolIds().isBlank()) agent.setToolIds(null);
        if (agent.getSkillIds() != null && agent.getSkillIds().isBlank()) agent.setSkillIds(null);
        if (agent.getMcpServerIds() != null && agent.getMcpServerIds().isBlank()) agent.setMcpServerIds(null);
        if (agent.getMemoryConfigJson() != null && agent.getMemoryConfigJson().isBlank()) agent.setMemoryConfigJson(null);

        if (agent.getId() == null || agent.getId().isBlank()) {
            agent.setId(IdGenerator.nextId());
            agent.setTenantId(CurrentUserContext.getTenantId());
            agent.setCreatedBy(CurrentUserContext.getUserId());
            agent.setStatus(AiAgent.STATUS_DRAFT);
            agent.setVersion(0);
            try {
                agentMapper.insert(agent);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "Agent 编码已存在: " + agent.getAgentCode());
            }
            log.info("agent created: id={} code={} type={}", agent.getId(), agent.getAgentCode(), agent.getAgentType());
            return agent;
        }
        AiAgent existing = getAgent(agent.getId());
        checkVersion(agent.getVersion(), existing.getVersion());
        if (!AiAgent.STATUS_DRAFT.equals(existing.getStatus()))
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "仅 DRAFT 状态可编辑，当前状态=" + existing.getStatus());
        agent.setTenantId(existing.getTenantId());
        agent.setStatus(AiAgent.STATUS_DRAFT);
        agent.setUpdatedBy(CurrentUserContext.getUserId());
        int rows = agentMapper.updateById(agent);
        if (rows == 0) throw new BusinessConflictException("数据已被他人修改，请刷新后重试");
        return agent;
    }

    @Transactional
    public AiAgent publish(String id, Integer version) {
        AiAgent agent = getAgent(id);
        checkVersion(version, agent.getVersion());
        if (AiAgent.STATUS_PUBLISHED.equals(agent.getStatus()))
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "Agent 已发布，不可重复发布");
        if (!AiAgent.STATUS_DRAFT.equals(agent.getStatus()))
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "仅 DRAFT 可发布，当前状态=" + agent.getStatus());
        agent.setStatus(AiAgent.STATUS_PUBLISHED);
        agent.setUpdatedBy(CurrentUserContext.getUserId());
        int rows = agentMapper.updateById(agent);
        if (rows == 0) throw new BusinessConflictException("数据已被他人修改，请刷新后重试");
        log.info("agent published: id={} code={}", id, agent.getAgentCode());
        return agent;
    }

    @Transactional
    public void delete(String id, Integer version) {
        AiAgent agent = getAgent(id);
        checkVersion(version, agent.getVersion());
        agentMapper.deleteById(id);
        log.info("agent deleted: id={} code={}", id, agent.getAgentCode());
    }

    private void applyDataScope(LambdaQueryWrapper<AiAgent> wrapper, DataScope scope) {
        if (scope == null) return;
        if (scope.scopeType() == DataScopeType.ALL || scope.scopeType() == DataScopeType.TENANT) return;
        String userId = scope.userId();
        if (userId == null || userId.isBlank()) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.eq(AiAgent::getCreatedBy, userId);
    }

    private void checkVersion(Integer requestVersion, Integer currentVersion) {
        if (requestVersion == null || !requestVersion.equals(currentVersion))
            throw new BusinessConflictException("版本号不匹配，请求版本=" + requestVersion + ", 当前版本=" + currentVersion);
    }
}
