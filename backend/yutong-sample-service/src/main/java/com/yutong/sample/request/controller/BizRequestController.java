package com.yutong.sample.request.controller;

import com.yutong.auth.RequiresPermission;
import com.yutong.common.idempotency.Idempotent;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import com.yutong.sample.request.domain.BizRequest;
import com.yutong.sample.request.dto.BizRequestDetailVO;
import com.yutong.sample.request.dto.SaveBizRequestRequest;
import com.yutong.sample.request.service.BizRequestApplicationService;
import com.yutong.system.file.domain.SysFile;
import com.yutong.system.file.service.FileService;
import com.yutong.system.log.auditable.Auditable;
import com.yutong.system.log.service.ImportExportResult;
import com.yutong.system.log.service.ImportExportTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 申请单接口。设计来源: 18-样例业务详细设计 API 清单
 * 端点: 分页查询、详情、保存草稿、修改草稿、提交、审核通过、驳回、撤回、归档。
 */
@Tag(name = "申请单管理")
@RestController
@RequestMapping("/api/v1/biz-requests")
public class BizRequestController {

    private static final String BIZ_TYPE = "BIZ_REQUEST";

    private final BizRequestApplicationService appService;
    private final ImportExportTaskService importExportTaskService;
    private final FileService fileService;

    public BizRequestController(BizRequestApplicationService appService,
                                ImportExportTaskService importExportTaskService,
                                FileService fileService) {
        this.appService = appService;
        this.importExportTaskService = importExportTaskService;
        this.fileService = fileService;
    }

    /**
     * 状态流转动作请求体 (提交/审核/驳回/撤回/归档共用)。
     * opinion: 审核意见 (驳回必填)
     * reason:  撤回原因
     * version: 乐观锁版本号
     * idempotencyKey: 幂等键
     */
    public record ActionRequest(String opinion, String reason, Integer version, String idempotencyKey) {}

    @Operation(summary = "分页查询申请单", operationId = "listBizRequests")
    @RequiresPermission("biz:request:list")
    @GetMapping
    public Result<PageResult<BizRequest>> page(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String requestNo,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) String cursor) {
        // PERF-002: mode=keyset 时走 keyset pagination，深翻页性能稳定
        // cursor 为空时查第一页，非空时翻下一页
        if ("keyset".equalsIgnoreCase(mode)) {
            return Result.ok(appService.pageRequestsByKeyset(cursor, pageSize, requestNo, title, status, customerId),
                    TraceContext.getTraceId());
        }
        PageRequest request = PageRequest.of(pageNo, pageSize);
        return Result.ok(appService.pageRequests(request, requestNo, title, status, customerId),
                TraceContext.getTraceId());
    }

    @Operation(summary = "查询申请单详情", operationId = "getBizRequest")
    @RequiresPermission("biz:request:detail")
    @GetMapping("/{id}")
    public Result<BizRequestDetailVO> detail(@PathVariable String id) {
        return Result.ok(appService.getRequestDetail(id), TraceContext.getTraceId());
    }

    @Operation(summary = "保存草稿(新建)", operationId = "createBizRequest")
    @RequiresPermission("biz:request:add")
    @Auditable(operationType = "CREATE", module = "sample", bizType = "biz_request",
            bizIdExpr = "#result.data.id", content = "新建申请单草稿", recordResult = true)
    @Idempotent(resourceType = "biz-draft-create", action = "CREATE", ttlSeconds = 10)
    @PostMapping
    public Result<BizRequest> create(@Valid @RequestBody SaveBizRequestRequest request) {
        request.setId(null);
        return Result.ok(appService.saveDraft(request), TraceContext.getTraceId());
    }

    @Operation(summary = "修改草稿", operationId = "updateBizRequest")
    @RequiresPermission("biz:request:edit")
    @Auditable(operationType = "UPDATE", module = "sample", bizType = "biz_request",
            bizIdExpr = "#id", content = "修改申请单草稿")
    @PutMapping("/{id}")
    public Result<BizRequest> update(@PathVariable String id, @Valid @RequestBody SaveBizRequestRequest request) {
        request.setId(id);
        return Result.ok(appService.saveDraft(request), TraceContext.getTraceId());
    }

    @Operation(summary = "提交申请单", operationId = "submitBizRequest")
    @RequiresPermission("biz:request:submit")
    @Auditable(operationType = "SUBMIT", module = "sample", bizType = "biz_request",
            bizIdExpr = "#id", content = "提交申请单")
    @PostMapping("/{id}/submit")
    public Result<BizRequest> submit(@PathVariable String id, @RequestBody ActionRequest body) {
        return Result.ok(appService.submit(id, body.version(), body.idempotencyKey()),
                TraceContext.getTraceId());
    }

    @Operation(summary = "审核通过", operationId = "approveBizRequest")
    @RequiresPermission("biz:request:approve")
    @Auditable(operationType = "APPROVE", module = "sample", bizType = "biz_request",
            bizIdExpr = "#id", content = "审核通过申请单")
    @PostMapping("/{id}/approve")
    public Result<BizRequest> approve(@PathVariable String id, @RequestBody ActionRequest body) {
        return Result.ok(appService.approve(id, body.opinion(), body.version(), body.idempotencyKey()),
                TraceContext.getTraceId());
    }

    @Operation(summary = "驳回申请单", operationId = "rejectBizRequest")
    @RequiresPermission("biz:request:reject")
    @Auditable(operationType = "REJECT", module = "sample", bizType = "biz_request",
            bizIdExpr = "#id", content = "驳回申请单")
    @PostMapping("/{id}/reject")
    public Result<BizRequest> reject(@PathVariable String id, @RequestBody ActionRequest body) {
        return Result.ok(appService.reject(id, body.opinion(), body.version(), body.idempotencyKey()),
                TraceContext.getTraceId());
    }

    @Operation(summary = "撤回申请单", operationId = "withdrawBizRequest")
    @RequiresPermission("biz:request:withdraw")
    @Auditable(operationType = "WITHDRAW", module = "sample", bizType = "biz_request",
            bizIdExpr = "#id", content = "撤回申请单")
    @PostMapping("/{id}/withdraw")
    public Result<BizRequest> withdraw(@PathVariable String id, @RequestBody ActionRequest body) {
        return Result.ok(appService.withdraw(id, body.reason(), body.version(), body.idempotencyKey()),
                TraceContext.getTraceId());
    }

    @Operation(summary = "归档申请单", operationId = "archiveBizRequest")
    @RequiresPermission("biz:request:archive")
    @Auditable(operationType = "ARCHIVE", module = "sample", bizType = "biz_request",
            bizIdExpr = "#id", content = "归档申请单")
    @PostMapping("/{id}/archive")
    public Result<BizRequest> archive(@PathVariable String id, @RequestBody ActionRequest body) {
        return Result.ok(appService.archive(id, body.version(), body.idempotencyKey()),
                TraceContext.getTraceId());
    }

    @Operation(summary = "删除申请单", operationId = "deleteBizRequest")
    @RequiresPermission("biz:request:delete")
    @Auditable(operationType = "DELETE", module = "sample", bizType = "biz_request",
            bizIdExpr = "#id", content = "删除申请单")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        appService.delete(id);
        return Result.ok(null, TraceContext.getTraceId());
    }

    @Operation(summary = "导入申请单", operationId = "importBizRequests")
    @RequiresPermission("biz:request:import")
    @Auditable(operationType = "IMPORT", module = "sample", bizType = "biz_request",
            content = "导入申请单")
    @Idempotent(resourceType = "async-task", action = "IMPORT", ttlSeconds = 10)
    @PostMapping("/import")
    public Result<Map<String, Object>> importBizRequests(@RequestParam("file") MultipartFile file) throws IOException {
        byte[] fileBytes = file.getBytes();
        String fileName = file.getOriginalFilename();
        var task = importExportTaskService.submitImportTask(BIZ_TYPE, fileName, fileBytes,
                this::doBizRequestImport);
        return Result.ok(Map.of(
                "taskId", task.getId(),
                "status", task.getStatus(),
                "message", "导入任务已提交"), TraceContext.getTraceId());
    }

    @Operation(summary = "导出申请单", operationId = "exportBizRequests")
    @RequiresPermission("biz:request:export")
    @Auditable(operationType = "EXPORT", module = "sample", bizType = "biz_request",
            content = "导出申请单")
    @GetMapping("/export")
    public Result<Map<String, Object>> exportBizRequests() {
        var task = importExportTaskService.submitExportTask(BIZ_TYPE, this::doBizRequestExport);
        return Result.ok(Map.of(
                "taskId", task.getId(),
                "status", task.getStatus(),
                "message", "导出任务已提交"), TraceContext.getTraceId());
    }

    // ==================== 导入导出业务工作函数 ====================

    /**
     * 申请单导入工作函数: 解析 CSV → 逐行创建草稿申请单 → 累计成功/失败 → 生成错误报告上传 MinIO。
     * CSV 表头: title,customerId,applyReason
     * (requestNo 由系统自动生成，导入一律创建为 DRAFT 状态)
     */
    private ImportExportResult doBizRequestImport(byte[] fileBytes, String fileName) {
        List<String> lines = splitLines(fileBytes);
        if (lines.isEmpty()) {
            return ImportExportResult.ofImport(0, 0, 0, null, "文件为空");
        }
        List<String> dataLines = lines.subList(1, lines.size());
        int success = 0;
        int fail = 0;
        List<String> errorRows = new ArrayList<>();
        for (int i = 0; i < dataLines.size(); i++) {
            String line = dataLines.get(i);
            if (line.isBlank()) {
                continue;
            }
            try {
                String[] fields = parseCsvLine(line);
                SaveBizRequestRequest req = new SaveBizRequestRequest();
                req.setId(null);
                req.setTitle(fieldOrBlank(fields, 0));
                req.setCustomerId(fieldOrBlank(fields, 1));
                req.setApplyReason(fieldOrBlank(fields, 2));
                appService.saveDraft(req);
                success++;
            } catch (Exception e) {
                fail++;
                errorRows.add("行" + (i + 2) + ": " + e.getMessage() + " | " + line);
            }
        }
        String errorReportFileId = uploadErrorReport(errorRows, fileName);
        String summary = "成功 " + success + " 条, 失败 " + fail + " 条";
        return ImportExportResult.ofImport(dataLines.size(), success, fail, errorReportFileId, summary);
    }

    /**
     * 申请单导出工作函数: 查询全部申请单 → 生成 CSV → 上传 MinIO → 返回 fileId。
     */
    private ImportExportResult doBizRequestExport() {
        List<BizRequest> all = new ArrayList<>();
        int pageNo = 1;
        int pageSize = 500;
        while (true) {
            PageResult<BizRequest> page = appService.pageRequests(
                    PageRequest.of(pageNo, pageSize), null, null, null, null);
            all.addAll(page.records());
            if (all.size() >= page.total() || page.records().isEmpty()) {
                break;
            }
            pageNo++;
        }
        StringBuilder csv = new StringBuilder();
        csv.append("requestNo,title,customerId,customerNameSnapshot,requestStatus,totalAmount,applyReason\n");
        for (BizRequest r : all) {
            csv.append(csvField(r.getRequestNo())).append(',')
                    .append(csvField(r.getTitle())).append(',')
                    .append(csvField(r.getCustomerId())).append(',')
                    .append(csvField(r.getCustomerNameSnapshot())).append(',')
                    .append(csvField(r.getRequestStatus())).append(',')
                    .append(r.getTotalAmount() != null ? r.getTotalAmount().toPlainString() : "").append(',')
                    .append(csvField(r.getApplyReason())).append('\n');
        }
        byte[] content = csv.toString().getBytes(StandardCharsets.UTF_8);
        SysFile uploaded = fileService.uploadBytes("biz_requests_export.csv", content, "text/csv");
        return ImportExportResult.ofExport(all.size(), uploaded.getId());
    }

    /** 上传错误报告 CSV 到 MinIO，返回 fileId；无错误行时返回 null。 */
    private String uploadErrorReport(List<String> errorRows, String sourceFileName) {
        if (errorRows.isEmpty()) {
            return null;
        }
        StringBuilder csv = new StringBuilder();
        csv.append("row,error,rawLine\n");
        for (String row : errorRows) {
            csv.append(csvField(row)).append('\n');
        }
        byte[] content = csv.toString().getBytes(StandardCharsets.UTF_8);
        String errorFileName = (sourceFileName != null ? sourceFileName : "import") + "_errors.csv";
        SysFile uploaded = fileService.uploadBytes(errorFileName, content, "text/csv");
        return uploaded.getId();
    }

    // ==================== CSV 工具 ====================

    private static List<String> splitLines(byte[] fileBytes) {
        if (fileBytes == null || fileBytes.length == 0) {
            return List.of();
        }
        String text = new String(fileBytes, StandardCharsets.UTF_8);
        String[] lines = text.split("\r?\n");
        return List.of(lines);
    }

    private static String[] parseCsvLine(String line) {
        return line.split(",", -1);
    }

    private static String fieldOrBlank(String[] fields, int idx) {
        return (idx < fields.length && fields[idx] != null) ? fields[idx].trim() : "";
    }

    private static String csvField(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
