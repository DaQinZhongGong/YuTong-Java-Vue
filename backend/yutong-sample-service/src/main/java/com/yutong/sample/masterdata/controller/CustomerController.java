package com.yutong.sample.masterdata.controller;

import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import com.yutong.sample.masterdata.domain.Customer;
import com.yutong.sample.masterdata.service.CustomerService;
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
 * 客户主数据接口。设计来源: 18-样例业务详细设计、98-后端实现蓝图
 */
@Tag(name = "客户主数据")
@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private static final String BIZ_TYPE = "CUSTOMER";

    private final CustomerService customerService;
    private final ImportExportTaskService importExportTaskService;
    private final FileService fileService;

    public CustomerController(CustomerService customerService,
                              ImportExportTaskService importExportTaskService,
                              FileService fileService) {
        this.customerService = customerService;
        this.importExportTaskService = importExportTaskService;
        this.fileService = fileService;
    }

    @Operation(summary = "分页查询客户", operationId = "listCustomers")
    @RequiresPermission("biz:customer:list")
    @GetMapping
    public Result<PageResult<Customer>> page(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        PageRequest request = PageRequest.of(pageNo, pageSize);
        return Result.ok(customerService.pageCustomers(request, keyword, status), TraceContext.getTraceId());
    }

    @Operation(summary = "查询客户详情", operationId = "getCustomer")
    @RequiresPermission("biz:customer:detail")
    @GetMapping("/{id}")
    public Result<Customer> get(@PathVariable String id) {
        return Result.ok(customerService.getCustomer(id), TraceContext.getTraceId());
    }

    @Operation(summary = "创建客户", operationId = "createCustomer")
    @RequiresPermission("biz:customer:add")
    @Auditable(operationType = "CREATE", module = "sample", bizType = "biz_customer",
            bizIdExpr = "#result.data.id", content = "创建客户", recordResult = true)
    @PostMapping
    public Result<Customer> create(@Valid @RequestBody Customer customer) {
        return Result.ok(customerService.createCustomer(customer), TraceContext.getTraceId());
    }

    @Operation(summary = "更新客户", operationId = "updateCustomer")
    @RequiresPermission("biz:customer:edit")
    @Auditable(operationType = "UPDATE", module = "sample", bizType = "biz_customer",
            bizIdExpr = "#id", content = "更新客户")
    @PutMapping("/{id}")
    public Result<Customer> update(@PathVariable String id, @Valid @RequestBody Customer customer) {
        return Result.ok(customerService.updateCustomer(id, customer), TraceContext.getTraceId());
    }

    @Operation(summary = "删除客户", operationId = "deleteCustomer")
    @RequiresPermission("biz:customer:delete")
    @Auditable(operationType = "DELETE", module = "sample", bizType = "biz_customer",
            bizIdExpr = "#id", content = "删除客户")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        customerService.deleteCustomer(id);
        return Result.ok(null, TraceContext.getTraceId());
    }

    @Operation(summary = "导入客户", operationId = "importCustomers")
    @RequiresPermission("biz:customer:import")
    @Auditable(operationType = "IMPORT", module = "sample", bizType = "biz_customer",
            content = "导入客户")
    @PostMapping("/import")
    public Result<Map<String, Object>> importCustomers(@RequestParam("file") MultipartFile file) throws IOException {
        // 同步读取文件字节 (请求结束后 MultipartFile 流关闭，异步线程无法再读)
        byte[] fileBytes = file.getBytes();
        String fileName = file.getOriginalFilename();
        var task = importExportTaskService.submitImportTask(BIZ_TYPE, fileName, fileBytes,
                this::doCustomerImport);
        return Result.ok(Map.of(
                "taskId", task.getId(),
                "status", task.getStatus(),
                "message", "导入任务已提交"), TraceContext.getTraceId());
    }

    @Operation(summary = "导出客户", operationId = "exportCustomers")
    @RequiresPermission("biz:customer:export")
    @Auditable(operationType = "EXPORT", module = "sample", bizType = "biz_customer",
            content = "导出客户")
    @GetMapping("/export")
    public Result<Map<String, Object>> exportCustomers() {
        var task = importExportTaskService.submitExportTask(BIZ_TYPE, this::doCustomerExport);
        return Result.ok(Map.of(
                "taskId", task.getId(),
                "status", task.getStatus(),
                "message", "导出任务已提交"), TraceContext.getTraceId());
    }

    // ==================== 导入导出业务工作函数 ====================

    /**
     * 客户导入工作函数: 解析 CSV → 逐行创建客户 → 累计成功/失败 → 生成错误报告上传 MinIO。
     * CSV 表头: customerCode,customerName,contactName,contactPhone,address,status
     */
    private ImportExportResult doCustomerImport(byte[] fileBytes, String fileName) {
        List<String> lines = splitLines(fileBytes);
        if (lines.isEmpty()) {
            return ImportExportResult.ofImport(0, 0, 0, null, "文件为空");
        }
        // 跳过表头
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
                Customer customer = new Customer();
                customer.setCustomerCode(fieldOrBlank(fields, 0));
                customer.setCustomerName(fieldOrBlank(fields, 1));
                customer.setContactName(fieldOrBlank(fields, 2));
                customer.setContactPhone(fieldOrBlank(fields, 3));
                customer.setAddress(fieldOrBlank(fields, 4));
                customer.setStatus(fieldOrBlank(fields, 5));
                customerService.createCustomer(customer);
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
     * 客户导出工作函数: 查询全部客户 → 生成 CSV → 上传 MinIO → 返回 fileId。
     */
    private ImportExportResult doCustomerExport() {
        // 分页拉取全部客户 (按页大小 500 遍历)
        List<Customer> all = new ArrayList<>();
        int pageNo = 1;
        int pageSize = 500;
        while (true) {
            PageResult<Customer> page = customerService.pageCustomers(
                    PageRequest.of(pageNo, pageSize), null, null);
            all.addAll(page.records());
            if (all.size() >= page.total() || page.records().isEmpty()) {
                break;
            }
            pageNo++;
        }
        StringBuilder csv = new StringBuilder();
        csv.append("customerCode,customerName,contactName,contactPhone,address,status\n");
        for (Customer c : all) {
            csv.append(csvField(c.getCustomerCode())).append(',')
                    .append(csvField(c.getCustomerName())).append(',')
                    .append(csvField(c.getContactName())).append(',')
                    .append(csvField(c.getContactPhone())).append(',')
                    .append(csvField(c.getAddress())).append(',')
                    .append(csvField(c.getStatus())).append('\n');
        }
        byte[] content = csv.toString().getBytes(StandardCharsets.UTF_8);
        SysFile uploaded = fileService.uploadBytes("customers_export.csv", content, "text/csv");
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

    /** 简单 CSV 行解析: 按逗号分割，不处理引号转义 (导入文件为系统生成的标准 CSV)。 */
    private static String[] parseCsvLine(String line) {
        return line.split(",", -1);
    }

    private static String fieldOrBlank(String[] fields, int idx) {
        return (idx < fields.length && fields[idx] != null) ? fields[idx].trim() : "";
    }

    /** CSV 字段转义: 含逗号/引号/换行时用双引号包裹并转义内部引号。 */
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
