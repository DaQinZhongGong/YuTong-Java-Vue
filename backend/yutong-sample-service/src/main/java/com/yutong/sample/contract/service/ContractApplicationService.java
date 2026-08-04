package com.yutong.sample.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import com.yutong.sample.contract.domain.Contract;
import com.yutong.sample.contract.domain.ContractApproval;
import com.yutong.sample.contract.domain.ContractTag;
import com.yutong.sample.contract.domain.ContractVersion;
import com.yutong.sample.contract.dto.ContractActionRequest;
import com.yutong.sample.contract.dto.ContractDetailVO;
import com.yutong.sample.contract.dto.SaveContractRequest;
import com.yutong.sample.contract.mapper.ContractApprovalMapper;
import com.yutong.sample.contract.mapper.ContractMapper;
import com.yutong.sample.contract.mapper.ContractTagMapper;
import com.yutong.sample.contract.mapper.ContractVersionMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 合同应用服务。设计来源: 35-样例业务矩阵扩展设计 P1 合同档案。
 *
 * <p>事务编排:
 * <ul>
 *   <li>分页查询 + 全文检索</li>
 *   <li>详情查询 + 版本/审批/标签聚合</li>
 *   <li>创建草稿 + 自动生成合同号 + 初始化版本 0</li>
 *   <li>状态流转 SUBMIT/APPROVE/REJECT/RESUBMIT/SIGN/ARCHIVE/CANCEL</li>
 *   <li>敏感字段脱敏（基于 DataScope.includeSensitive）</li>
 * </ul>
 *
 * <p>resourceCode = "biz:contract"，由后端决定，不接受前端传入。
 */
@Service
public class ContractApplicationService {

    /** 资源编码，对齐 67 号文档 DataScope resourceCode */
    private static final String RESOURCE_CODE = "biz:contract";

    /** 敏感字段脱敏掩码 */
    private static final String SENSITIVE_MASK = "***";

    private final ContractMapper contractMapper;
    private final ContractVersionMapper versionMapper;
    private final ContractApprovalMapper approvalMapper;
    private final ContractTagMapper tagMapper;
    private final ContractSequenceService sequenceService;
    private final ContractDomainService domainService;
    private final DataScopeResolver dataScopeResolver;

    public ContractApplicationService(ContractMapper contractMapper,
                                       ContractVersionMapper versionMapper,
                                       ContractApprovalMapper approvalMapper,
                                       ContractTagMapper tagMapper,
                                       ContractSequenceService sequenceService,
                                       ContractDomainService domainService,
                                       DataScopeResolver dataScopeResolver) {
        this.contractMapper = contractMapper;
        this.versionMapper = versionMapper;
        this.approvalMapper = approvalMapper;
        this.tagMapper = tagMapper;
        this.sequenceService = sequenceService;
        this.domainService = domainService;
        this.dataScopeResolver = dataScopeResolver;
    }

    // ==================== 查询 ====================

    public PageResult<Contract> pageContracts(PageRequest request, String contractNo, String title,
                                                String status, String contractType, String partyB) {
        DataScope scope = dataScopeResolver.resolve(RESOURCE_CODE);
        QueryWrapper<Contract> qw = new QueryWrapper<>();
        qw.eq("tenant_id", CurrentUserContext.getTenantId());
        if (contractNo != null && !contractNo.isBlank()) qw.like("contract_no", contractNo);
        if (title != null && !title.isBlank()) qw.like("title", title);
        if (status != null && !status.isBlank()) qw.eq("status", status);
        if (contractType != null && !contractType.isBlank()) qw.eq("contract_type", contractType);
        if (partyB != null && !partyB.isBlank()) qw.like("party_b", partyB);
        qw.orderByDesc("created_time");
        // GA2-DS: 接入 DataScope 过滤，admin(ALL/TENANT) 放行，非 admin 按 created_by 过滤
        applyDataScope(qw, scope);
        Page<Contract> page = contractMapper.selectPage(
                new Page<>(request.page(), request.size()), qw);
        // 列表页同样对 amount 脱敏
        if (scope == null || !scope.canViewSensitive()) {
            page.getRecords().forEach(c -> c.setAmount(null));
        }
        return PageResult.of(page.getRecords(), page.getTotal(), request.page(), request.size());
    }

    /**
     * GA2-DS: 对 QueryWrapper 追加 DataScope 过滤条件。
     * - ALL/TENANT: 无附加条件 (admin)
     * - 其它(SELF/DEPT/CUSTOM/NONE 等): created_by = currentUserId，userId 缺失时安全降级 1=0
     */
    private void applyDataScope(QueryWrapper<Contract> qw, DataScope scope) {
        if (scope == null) {
            return;
        }
        if (scope.scopeType() == DataScopeType.ALL || scope.scopeType() == DataScopeType.TENANT) {
            return;
        }
        String userId = scope.userId();
        if (userId == null || userId.isBlank()) {
            qw.apply("1 = 0");
            return;
        }
        qw.eq("created_by", userId);
    }

    /**
     * 全文检索查询合同（PG tsvector + GIN 索引）。
     *
     * @param query 查询字符串（ plainto_tsquery 语法，如 "技术服务" 或 "采购 & 合同"）
     * @param limit 返回条数上限（默认 20）
     */
    public List<Contract> searchContracts(String query, int limit) {
        if (limit <= 0 || limit > 100) {
            limit = 20;
        }
        return contractMapper.searchByFullText(CurrentUserContext.getTenantId(), query, limit);
    }

    public ContractDetailVO getContractDetail(String id) {
        Contract contract = contractMapper.selectById(id);
        if (contract == null) {
            throw new ResourceNotFoundException(ErrorCode.CTR_CONTRACT_NOT_FOUND, "合同不存在: " + id);
        }

        List<ContractVersion> versions = versionMapper.selectList(new LambdaQueryWrapper<ContractVersion>()
                .eq(ContractVersion::getContractId, id)
                .orderByAsc(ContractVersion::getVersionNo));

        List<ContractApproval> approvals = approvalMapper.selectList(new LambdaQueryWrapper<ContractApproval>()
                .eq(ContractApproval::getContractId, id)
                .orderByAsc(ContractApproval::getCreatedTime));

        List<ContractTag> tags = tagMapper.selectList(new LambdaQueryWrapper<ContractTag>()
                .eq(ContractTag::getContractId, id)
                .orderByAsc(ContractTag::getTagName));

        ContractDetailVO vo = new ContractDetailVO();
        BeanUtils.copyProperties(contract, vo);
        vo.setVersions(versions);
        vo.setApprovals(approvals);
        vo.setTags(tags);
        vo.setEditable(domainService.isEditable(contract.getStatus()));

        // 脱敏: viewer 角色不可见金额；非 APPROVED 以上状态对 party_b 部分脱敏
        DataScope scope = dataScopeResolver.resolve(RESOURCE_CODE);
        if (scope == null || !scope.canViewSensitive()) {
            vo.setAmount(null);
            if (vo.getPartyB() != null && !isApprovedOrAbove(contract.getStatus())) {
                vo.setPartyB(SENSITIVE_MASK);
            }
            if (vo.getContentSummary() != null) {
                vo.setContentSummary(SENSITIVE_MASK);
            }
        }
        return vo;
    }

    // ==================== 创建/更新 ====================

    @Transactional
    public Contract createContract(SaveContractRequest request) {
        Contract contract = new Contract();
        contract.setId(IdGenerator.nextId());
        contract.setContractNo(sequenceService.nextContractNo());
        contract.setTitle(request.getTitle());
        contract.setContractType(request.getContractType() != null
                ? request.getContractType() : Contract.TYPE_GENERAL);
        contract.setPartyA(request.getPartyA());
        contract.setPartyB(request.getPartyB());
        contract.setSignedDate(request.getSignedDate());
        contract.setEffectiveDate(request.getEffectiveDate());
        contract.setExpireDate(request.getExpireDate());
        contract.setAmount(request.getAmount());
        contract.setCurrency(request.getCurrency() != null ? request.getCurrency() : "CNY");
        contract.setContentSummary(request.getContentSummary());
        contract.setStatus(Contract.STATUS_DRAFT);
        contract.setCurrentVersionNo(0);
        contract.setOwnerUserId(CurrentUserContext.getUserId());
        contract.setOwnerDeptId(CurrentUserContext.getDeptId());
        contract.setOwnerDeptPath(CurrentUserContext.getDeptPath());
        contractMapper.insert(contract);

        // 自动写入创建审批记录
        writeApproval(contract.getId(), "CREATE", null, Contract.STATUS_DRAFT, "创建合同草稿");
        // 维护全文检索向量
        updateSearchVector(contract.getId());
        return contract;
    }

    @Transactional
    public Contract updateContract(String id, SaveContractRequest request) {
        Contract contract = contractMapper.selectById(id);
        if (contract == null) {
            throw new ResourceNotFoundException(ErrorCode.CTR_CONTRACT_NOT_FOUND, "合同不存在: " + id);
        }
        if (!domainService.isEditable(contract.getStatus())) {
            throw new BusinessException(ErrorCode.CTR_CONTRACT_ARCHIVED_READ_ONLY,
                    "已归档/已取消的合同不可修改");
        }
        contract.setTitle(request.getTitle());
        if (request.getContractType() != null) contract.setContractType(request.getContractType());
        contract.setPartyA(request.getPartyA());
        contract.setPartyB(request.getPartyB());
        contract.setSignedDate(request.getSignedDate());
        contract.setEffectiveDate(request.getEffectiveDate());
        contract.setExpireDate(request.getExpireDate());
        contract.setAmount(request.getAmount());
        if (request.getCurrency() != null) contract.setCurrency(request.getCurrency());
        contract.setContentSummary(request.getContentSummary());
        contractMapper.updateById(contract);

        updateSearchVector(contract.getId());
        return contract;
    }

    // ==================== 状态流转 ====================

    @Transactional
    public Contract doAction(String contractId, ContractActionRequest request) {
        Contract contract = contractMapper.selectById(contractId);
        if (contract == null) {
            throw new ResourceNotFoundException(ErrorCode.CTR_CONTRACT_NOT_FOUND, "合同不存在: " + contractId);
        }

        String action = request.getAction();
        String fromStatus = contract.getStatus();
        String toStatus = domainService.validateTransition(action, fromStatus);
        OffsetDateTime now = OffsetDateTime.now();

        // APPROVE/REJECT 必填审批意见
        if (("APPROVE".equals(action) || "REJECT".equals(action))
                && (request.getOpinion() == null || request.getOpinion().isBlank())) {
            throw new BusinessException(ErrorCode.CTR_CONTRACT_APPROVAL_OPINION_REQUIRED,
                    "审批通过/驳回必须填写审批意见");
        }

        // 各操作特有时间戳
        switch (action) {
            case "SUBMIT", "RESUBMIT" -> contract.setSubmittedTime(now);
            case "APPROVE" -> contract.setApprovedTime(now);
            case "SIGN" -> contract.setSignedTime(now);
            case "ARCHIVE" -> contract.setArchivedTime(now);
            default -> { /* CANCEL/REJECT 无特殊时间戳 */ }
        }

        contract.setStatus(toStatus);
        contractMapper.updateById(contract);

        writeApproval(contract.getId(), action, fromStatus, toStatus, request.getOpinion());
        // 状态变更后重新维护全文检索向量（status 不在 tsvector 中，但内容可能变化）
        if ("SUBMIT".equals(action) || "RESUBMIT".equals(action)) {
            updateSearchVector(contract.getId());
        }
        return contract;
    }

    // ==================== 标签管理 ====================

    @Transactional
    public ContractTag addTag(String contractId, String tagName) {
        Contract contract = contractMapper.selectById(contractId);
        if (contract == null) {
            throw new ResourceNotFoundException(ErrorCode.CTR_CONTRACT_NOT_FOUND, "合同不存在: " + contractId);
        }
        ContractTag tag = new ContractTag();
        tag.setId(IdGenerator.nextId());
        tag.setContractId(contractId);
        tag.setTagName(tagName);
        try {
            tagMapper.insert(tag);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 唯一索引拦截重复标签
            throw new BusinessException(ErrorCode.SYS_BUSINESS_CONFLICT,
                    "标签已存在: " + tagName);
        }
        return tag;
    }

    @Transactional
    public void removeTag(String contractId, String tagName) {
        tagMapper.delete(new LambdaQueryWrapper<ContractTag>()
                .eq(ContractTag::getContractId, contractId)
                .eq(ContractTag::getTagName, tagName));
    }

    // ==================== 私有方法 ====================

    /** 判断合同状态是否 >= APPROVED（用于 party_b 脱敏判断） */
    private boolean isApprovedOrAbove(String status) {
        return Contract.STATUS_APPROVED.equals(status)
                || Contract.STATUS_SIGNED.equals(status)
                || Contract.STATUS_ARCHIVED.equals(status);
    }

    private void writeApproval(String contractId, String action, String fromStatus,
                                String toStatus, String opinion) {
        ContractApproval approval = new ContractApproval();
        approval.setId(IdGenerator.nextId());
        approval.setContractId(contractId);
        approval.setAction(action);
        approval.setFromStatus(fromStatus);
        approval.setToStatus(toStatus);
        approval.setApproverId(CurrentUserContext.getUserId());
        approval.setApproverName(CurrentUserContext.getUsername());
        approval.setOpinion(opinion);
        approvalMapper.insert(approval);
    }

    /**
     * 维护合同的全文检索向量。
     * 直接执行 UPDATE SQL，避免 MyBatis-Plus 实体映射 tsvector 类型。
     */
    private void updateSearchVector(String contractId) {
        com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaUpdate(Contract.class);
        contractMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Contract>()
                        .eq(Contract::getId, contractId)
                        .setSql("search_vector = to_tsvector('simple', " +
                                "coalesce(title,'') || ' ' || coalesce(contract_no,'') || ' ' || " +
                                "coalesce(party_a,'') || ' ' || coalesce(party_b,'') || ' ' || " +
                                "coalesce(content_summary,''))"));
    }
}
