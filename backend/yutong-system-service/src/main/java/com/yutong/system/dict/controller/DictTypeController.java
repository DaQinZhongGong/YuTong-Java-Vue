package com.yutong.system.dict.controller;

import com.yutong.auth.PublicEndpoint;
import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import com.yutong.system.dict.domain.DictType;
import com.yutong.system.dict.service.DictService;
import com.yutong.system.log.auditable.Auditable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 字典接口。设计来源: 08-API契约设计、98-后端实现蓝图系统基础接口补齐规则
 */
@Tag(name = "字典管理")
@RestController
@RequestMapping("/api/v1/dict-types")
public class DictTypeController {

    private final DictService dictService;

    public DictTypeController(DictService dictService) {
        this.dictService = dictService;
    }

    @Operation(summary = "分页查询字典类型", operationId = "listDictTypes")
    @RequiresPermission("system:dict:list")
    @GetMapping
    public Result<PageResult<DictType>> page(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             @RequestParam(required = false) String keyword) {
        return Result.ok(dictService.pageDictTypes(PageRequest.of(page, size), keyword), TraceContext.getTraceId());
    }

    @Operation(summary = "创建字典类型", operationId = "createDictType")
    @RequiresPermission("system:dict:add")
    @Auditable(operationType = "CREATE", module = "system", bizType = "sys_dict_type",
            bizIdExpr = "#result.data.id", content = "创建字典类型", recordResult = true)
    @PostMapping
    public Result<DictType> create(@Valid @RequestBody DictType type) {
        return Result.ok(dictService.createDictType(type), TraceContext.getTraceId());
    }

    @Operation(summary = "更新字典类型", operationId = "updateDictType")
    @RequiresPermission("system:dict:edit")
    @Auditable(operationType = "UPDATE", module = "system", bizType = "sys_dict_type",
            bizIdExpr = "#id", content = "更新字典类型")
    @PutMapping("/{id}")
    public Result<DictType> update(@PathVariable String id, @Valid @RequestBody DictType type) {
        return Result.ok(dictService.updateDictType(id, type), TraceContext.getTraceId());
    }

    @Operation(summary = "删除字典类型", operationId = "deleteDictType")
    @RequiresPermission("system:dict:delete")
    @Auditable(operationType = "DELETE", module = "system", bizType = "sys_dict_type",
            bizIdExpr = "#id", content = "删除字典类型")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        dictService.deleteDictType(id);
        return Result.ok(null, TraceContext.getTraceId());
    }
}
