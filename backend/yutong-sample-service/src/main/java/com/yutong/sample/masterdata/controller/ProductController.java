package com.yutong.sample.masterdata.controller;

import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import com.yutong.sample.masterdata.domain.Product;
import com.yutong.sample.masterdata.service.ProductService;
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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 商品主数据接口。设计来源: 18-样例业务详细设计、98-后端实现蓝图
 */
@Tag(name = "商品主数据")
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private static final String BIZ_TYPE = "PRODUCT";

    private final ProductService productService;
    private final ImportExportTaskService importExportTaskService;
    private final FileService fileService;

    public ProductController(ProductService productService,
                             ImportExportTaskService importExportTaskService,
                             FileService fileService) {
        this.productService = productService;
        this.importExportTaskService = importExportTaskService;
        this.fileService = fileService;
    }

    @Operation(summary = "分页查询商品", operationId = "listProducts")
    @RequiresPermission("biz:product:list")
    @GetMapping
    public Result<PageResult<Product>> page(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        PageRequest request = PageRequest.of(pageNo, pageSize);
        return Result.ok(productService.pageProducts(request, keyword, status), TraceContext.getTraceId());
    }

    @Operation(summary = "查询商品详情", operationId = "getProduct")
    @RequiresPermission("biz:product:detail")
    @GetMapping("/{id}")
    public Result<Product> get(@PathVariable String id) {
        return Result.ok(productService.getProduct(id), TraceContext.getTraceId());
    }

    @Operation(summary = "创建商品", operationId = "createProduct")
    @RequiresPermission("biz:product:add")
    @Auditable(operationType = "CREATE", module = "sample", bizType = "biz_product",
            bizIdExpr = "#result.data.id", content = "创建商品", recordResult = true)
    @PostMapping
    public Result<Product> create(@Valid @RequestBody Product product) {
        return Result.ok(productService.createProduct(product), TraceContext.getTraceId());
    }

    @Operation(summary = "更新商品", operationId = "updateProduct")
    @RequiresPermission("biz:product:edit")
    @Auditable(operationType = "UPDATE", module = "sample", bizType = "biz_product",
            bizIdExpr = "#id", content = "更新商品")
    @PutMapping("/{id}")
    public Result<Product> update(@PathVariable String id, @Valid @RequestBody Product product) {
        return Result.ok(productService.updateProduct(id, product), TraceContext.getTraceId());
    }

    @Operation(summary = "删除商品", operationId = "deleteProduct")
    @RequiresPermission("biz:product:delete")
    @Auditable(operationType = "DELETE", module = "sample", bizType = "biz_product",
            bizIdExpr = "#id", content = "删除商品")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        productService.deleteProduct(id);
        return Result.ok(null, TraceContext.getTraceId());
    }

    @Operation(summary = "导入商品", operationId = "importProducts")
    @RequiresPermission("biz:product:import")
    @Auditable(operationType = "IMPORT", module = "sample", bizType = "biz_product",
            content = "导入商品")
    @PostMapping("/import")
    public Result<Map<String, Object>> importProducts(@RequestParam("file") MultipartFile file) throws IOException {
        byte[] fileBytes = file.getBytes();
        String fileName = file.getOriginalFilename();
        var task = importExportTaskService.submitImportTask(BIZ_TYPE, fileName, fileBytes,
                this::doProductImport);
        return Result.ok(Map.of(
                "taskId", task.getId(),
                "status", task.getStatus(),
                "message", "导入任务已提交"), TraceContext.getTraceId());
    }

    @Operation(summary = "导出商品", operationId = "exportProducts")
    @RequiresPermission("biz:product:export")
    @Auditable(operationType = "EXPORT", module = "sample", bizType = "biz_product",
            content = "导出商品")
    @GetMapping("/export")
    public Result<Map<String, Object>> exportProducts() {
        var task = importExportTaskService.submitExportTask(BIZ_TYPE, this::doProductExport);
        return Result.ok(Map.of(
                "taskId", task.getId(),
                "status", task.getStatus(),
                "message", "导出任务已提交"), TraceContext.getTraceId());
    }

    // ==================== 导入导出业务工作函数 ====================

    /**
     * 商品导入工作函数: 解析 CSV → 逐行创建商品 → 累计成功/失败 → 生成错误报告上传 MinIO。
     * CSV 表头: productCode,productName,unit,price,status
     */
    private ImportExportResult doProductImport(byte[] fileBytes, String fileName) {
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
                Product product = new Product();
                product.setProductCode(fieldOrBlank(fields, 0));
                product.setProductName(fieldOrBlank(fields, 1));
                product.setUnit(fieldOrBlank(fields, 2));
                String priceStr = fieldOrBlank(fields, 3);
                if (!priceStr.isEmpty()) {
                    product.setPrice(new BigDecimal(priceStr));
                }
                product.setStatus(fieldOrBlank(fields, 4));
                productService.createProduct(product);
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
     * 商品导出工作函数: 查询全部商品 → 生成 CSV → 上传 MinIO → 返回 fileId。
     */
    private ImportExportResult doProductExport() {
        List<Product> all = new ArrayList<>();
        int pageNo = 1;
        int pageSize = 500;
        while (true) {
            PageResult<Product> page = productService.pageProducts(
                    PageRequest.of(pageNo, pageSize), null, null);
            all.addAll(page.records());
            if (all.size() >= page.total() || page.records().isEmpty()) {
                break;
            }
            pageNo++;
        }
        StringBuilder csv = new StringBuilder();
        csv.append("productCode,productName,unit,price,status\n");
        for (Product p : all) {
            csv.append(csvField(p.getProductCode())).append(',')
                    .append(csvField(p.getProductName())).append(',')
                    .append(csvField(p.getUnit())).append(',')
                    .append(p.getPrice() != null ? p.getPrice().toPlainString() : "").append(',')
                    .append(csvField(p.getStatus())).append('\n');
        }
        byte[] content = csv.toString().getBytes(StandardCharsets.UTF_8);
        SysFile uploaded = fileService.uploadBytes("products_export.csv", content, "text/csv");
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
