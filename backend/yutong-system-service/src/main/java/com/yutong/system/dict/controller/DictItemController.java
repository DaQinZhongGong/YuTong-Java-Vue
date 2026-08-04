package com.yutong.system.dict.controller;

import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import com.yutong.system.dict.domain.DictItem;
import com.yutong.system.dict.service.DictService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 字典项接口。设计来源: 98-后端实现蓝图系统基础接口补齐规则
 * 必须包含: list、by-type、create、update、delete
 */
@Tag(name = "字典项管理")
@RestController
@RequestMapping("/api/v1/dict-items")
public class DictItemController {

    private final DictService dictService;

    public DictItemController(DictService dictService) {
        this.dictService = dictService;
    }

    @Operation(summary = "分页查询字典项", operationId = "listDictItems")
    @RequiresPermission("system:dict-item:list")
    @GetMapping
    public Result<PageResult<DictItem>> list(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              @RequestParam(required = false) String dictType) {
        return Result.ok(dictService.pageDictItems(PageRequest.of(page, size), dictType), TraceContext.getTraceId());
    }

    @Operation(summary = "按字典类型查询启用字典项", operationId = "listEnabledDictItemsByType")
    @GetMapping("/by-type/{dictType}")
    public Result<List<DictItem>> listByType(@PathVariable String dictType) {
        return Result.ok(dictService.listByType(dictType), TraceContext.getTraceId());
    }

    @io.swagger.v3.oas.annotations.Hidden
    @Operation(summary = "查询字典项详情", operationId = "getDictItem")
    @RequiresPermission("system:dict-item:detail")
    @GetMapping("/{id}")
    public Result<DictItem> get(@PathVariable String id) {
        return Result.ok(dictService.getDictItem(id), TraceContext.getTraceId());
    }

    @Operation(summary = "创建字典项", operationId = "createDictItem")
    @RequiresPermission("system:dict-item:add")
    @PostMapping
    public Result<DictItem> create(@RequestBody DictItem item) {
        return Result.ok(dictService.createDictItem(item), TraceContext.getTraceId());
    }

    @Operation(summary = "更新字典项", operationId = "updateDictItem")
    @RequiresPermission("system:dict-item:edit")
    @PutMapping("/{id}")
    public Result<DictItem> update(@PathVariable String id, @RequestBody DictItem item) {
        return Result.ok(dictService.updateDictItem(id, item), TraceContext.getTraceId());
    }

    @Operation(summary = "删除字典项", operationId = "deleteDictItem")
    @RequiresPermission("system:dict-item:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        dictService.deleteDictItem(id);
        return Result.ok(null, TraceContext.getTraceId());
    }
}
