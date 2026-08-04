package com.yutong.system.file.controller;

import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import com.yutong.system.file.domain.BizFileRel;
import com.yutong.system.file.domain.SysFile;
import com.yutong.system.file.dto.BindFileRequest;
import com.yutong.system.file.service.FileService;
import com.yutong.system.log.auditable.Auditable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件管理接口。设计来源: 08-API契约设计、98-后端实现蓝图系统基础接口补齐规则
 * 端点: /api/v1/files
 * 包含: list(分页)、upload、bind、by-biz、download-url、detail、delete
 *
 * GA2-25: 修正 operationId 对齐 routes.yaml 权威基线:
 *   - listFiles → GET /api/v1/files (分页列表, 原先错位用在 by-biz 上)
 *   - listFilesByBiz → GET /api/v1/files/by-biz (@Hidden 隐藏, 不破坏 94 operation 基线)
 *
 * GA2-L188: 权限注解对齐 openapi.yaml x-permission:
 *   - listFiles 补 system:file:list (原缺失)
 *   - downloadFile 补 system:file:download (原缺失)
 *   - previewFile 改 system:file:preview (原错用 system:file:detail)
 *   - uploadFile/deleteFile 补 @Auditable 注解 (CT-9 审计落库验证)
 */
@Tag(name = "文件管理")
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @Operation(summary = "分页查询文件列表", operationId = "listFiles")
    @RequiresPermission("system:file:list")
    @GetMapping
    public Result<PageResult<SysFile>> page(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             @RequestParam(required = false) String fileName) {
        return Result.ok(fileService.pageFiles(PageRequest.of(page, size), fileName), TraceContext.getTraceId());
    }

    @Operation(summary = "上传文件", operationId = "uploadFile")
    @RequiresPermission("system:file:upload")
    @Auditable(operationType = "CREATE", module = "system", bizType = "sys_file",
            bizIdExpr = "#result.data.id", content = "上传文件", recordResult = true)
    @PostMapping("/upload")
    public Result<SysFile> upload(@RequestParam("file") MultipartFile file) {
        return Result.ok(fileService.upload(file), TraceContext.getTraceId());
    }

    @Operation(summary = "绑定文件到业务对象", operationId = "bindFile")
    @RequiresPermission("system:file:bind")
    @Auditable(operationType = "BIND", module = "system", bizType = "sys_file",
            content = "绑定文件到业务对象")
    @PostMapping("/bind")
    public Result<BizFileRel> bind(@RequestBody BindFileRequest request) {
        return Result.ok(
                fileService.bind(request.bizType(), request.bizId(), request.fileId(),
                        request.relType(), request.sortNo()),
                TraceContext.getTraceId());
    }

    @io.swagger.v3.oas.annotations.Hidden
    @Operation(summary = "按业务对象查询绑定文件", operationId = "listFilesByBiz")
    @GetMapping("/by-biz")
    public Result<List<BizFileRel>> listByBiz(@RequestParam String bizType,
                                               @RequestParam String bizId) {
        return Result.ok(fileService.listByBiz(bizType, bizId), TraceContext.getTraceId());
    }

    @Operation(summary = "获取文件预签名下载链接", operationId = "downloadFile")
    @RequiresPermission("system:file:download")
    @GetMapping("/{id}/download-url")
    public Result<String> getDownloadUrl(@PathVariable String id) {
        return Result.ok(fileService.getDownloadUrl(id), TraceContext.getTraceId());
    }

    @Operation(summary = "查询文件详情", operationId = "previewFile")
    @RequiresPermission("system:file:preview")
    @GetMapping("/{id}")
    public Result<SysFile> get(@PathVariable String id) {
        return Result.ok(fileService.getFile(id), TraceContext.getTraceId());
    }

    @Operation(summary = "删除文件", operationId = "deleteFile")
    @RequiresPermission("system:file:delete")
    @Auditable(operationType = "DELETE", module = "system", bizType = "sys_file",
            bizIdExpr = "#id", content = "删除文件")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        fileService.deleteFile(id);
        return Result.ok(null, TraceContext.getTraceId());
    }
}
