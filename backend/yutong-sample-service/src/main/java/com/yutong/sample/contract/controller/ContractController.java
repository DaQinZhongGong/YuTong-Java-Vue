package com.yutong.sample.contract.controller;

import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import com.yutong.sample.contract.domain.Contract;
import com.yutong.sample.contract.domain.ContractTag;
import com.yutong.sample.contract.dto.ContractActionRequest;
import com.yutong.sample.contract.dto.ContractDetailVO;
import com.yutong.sample.contract.dto.SaveContractRequest;
import com.yutong.sample.contract.service.ContractApplicationService;
import com.yutong.system.log.auditable.Auditable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 合同档案 Controller。设计来源: 35-样例业务矩阵扩展设计 P1 合同档案。
 *
 * <p>提供合同 CRUD + 状态流转 + 全文检索 + 标签管理 API。
 * <p>核心验证能力: 文件版本管理、PG 全文检索、敏感字段脱敏、操作审计(@Auditable)、归档只读。
 *
 * <p>状态机: DRAFT → SUBMITTED → APPROVED → SIGNED → ARCHIVED
 *           (REJECTED 驳回分支，CANCELLED 取消终态)
 * <p>Action: SUBMIT/APPROVE/REJECT/RESUBMIT/SIGN/ARCHIVE/CANCEL
 */
@RestController
@RequestMapping("/api/v1/contracts")
@Tag(name = "Contract", description = "合同档案")
public class ContractController {

    private final ContractApplicationService contractService;

    public ContractController(ContractApplicationService contractService) {
        this.contractService = contractService;
    }

    @GetMapping
    @Operation(summary = "分页查询合同列表", operationId = "pageContracts")
    @RequiresPermission("biz:contract:list")
    public Result<PageResult<Contract>> pageContracts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String contractNo,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String contractType,
            @RequestParam(required = false) String partyB) {
        PageResult<Contract> result = contractService.pageContracts(
                PageRequest.of(page, size), contractNo, title, status, contractType, partyB);
        return Result.ok(result, TraceContext.getTraceId());
    }

    @GetMapping("/search")
    @Operation(summary = "全文检索合同（PG tsvector + GIN 索引）",
            operationId = "searchContracts",
            description = "query 参数支持 plainto_tsquery 语法，如 \"技术服务\" 或 \"采购 & 合同\"")
    @RequiresPermission("biz:contract:list")
    public Result<List<Contract>> searchContracts(
            @RequestParam String query,
            @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(contractService.searchContracts(query, limit), TraceContext.getTraceId());
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询合同详情（含版本/审批/标签聚合）", operationId = "getContractDetail")
    @RequiresPermission("biz:contract:detail")
    public Result<ContractDetailVO> getContractDetail(@PathVariable String id) {
        return Result.ok(contractService.getContractDetail(id), TraceContext.getTraceId());
    }

    @PostMapping
    @Operation(summary = "创建合同草稿", operationId = "createContract")
    @RequiresPermission("biz:contract:add")
    @Auditable(operationType = "CREATE", module = "sample", bizType = "contract",
            bizIdExpr = "#result.data.id", content = "创建合同草稿", recordResult = true)
    public Result<Contract> createContract(@Valid @RequestBody SaveContractRequest request) {
        return Result.ok(contractService.createContract(request), TraceContext.getTraceId());
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新合同草稿（已归档/已取消不可编辑）", operationId = "updateContract")
    @RequiresPermission("biz:contract:edit")
    @Auditable(operationType = "UPDATE", module = "sample", bizType = "contract",
            bizIdExpr = "#id", content = "更新合同草稿")
    public Result<Contract> updateContract(@PathVariable String id,
                                             @Valid @RequestBody SaveContractRequest request) {
        return Result.ok(contractService.updateContract(id, request), TraceContext.getTraceId());
    }

    @PostMapping("/{id}/actions")
    @Operation(summary = "合同状态流转操作", operationId = "contractAction",
            description = "action: SUBMIT 提交审批 / APPROVE 审批通过 / REJECT 驳回 / RESUBMIT 重新提交 / SIGN 签订 / ARCHIVE 归档 / CANCEL 取消")
    @RequiresPermission("biz:contract:archive")
    @Auditable(operationType = "ACTION", module = "sample", bizType = "contract",
            bizIdExpr = "#id", content = "合同状态流转")
    public Result<Contract> doAction(@PathVariable String id,
                                      @RequestBody ContractActionRequest request) {
        return Result.ok(contractService.doAction(id, request), TraceContext.getTraceId());
    }

    @PostMapping("/{id}/tags")
    @Operation(summary = "添加合同标签", operationId = "addContractTag")
    @RequiresPermission("biz:contract:add")
    @Auditable(operationType = "TAG_ADD", module = "sample", bizType = "contract",
            bizIdExpr = "#id", content = "添加合同标签")
    public Result<ContractTag> addTag(@PathVariable String id,
                                       @RequestParam String tagName) {
        return Result.ok(contractService.addTag(id, tagName), TraceContext.getTraceId());
    }

    @DeleteMapping("/{id}/tags/{tagName}")
    @Operation(summary = "删除合同标签", operationId = "removeContractTag")
    @RequiresPermission("biz:contract:delete")
    @Auditable(operationType = "TAG_REMOVE", module = "sample", bizType = "contract",
            bizIdExpr = "#id", content = "删除合同标签")
    public Result<Void> removeTag(@PathVariable String id, @PathVariable String tagName) {
        contractService.removeTag(id, tagName);
        return Result.ok(null, TraceContext.getTraceId());
    }
}
