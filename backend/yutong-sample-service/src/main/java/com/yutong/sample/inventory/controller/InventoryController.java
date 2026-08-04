package com.yutong.sample.inventory.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import com.yutong.sample.inventory.domain.*;
import com.yutong.sample.inventory.dto.*;
import com.yutong.sample.inventory.service.InventoryApplicationService;
import com.yutong.system.log.auditable.Auditable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 库存出入库 Controller。设计来源: 35-样例业务矩阵扩展设计 P1 库存出入库。
 *
 * <p>提供物料/仓库/库存余额/入库单/出库单/库存流水 CRUD + 状态流转 API。
 * <p>核心验证能力: 并发扣减乐观锁、幂等键防重复、异常补偿、事务一致性。
 *
 * <p>入库单状态机: DRAFT → CONFIRMED (CONFIRM 确认入库)
 * <p>出库单状态机: DRAFT → CONFIRMED (CONFIRM 确认出库) → COMPENSATED (COMPENSATE 异常补偿)
 */
@RestController
@RequestMapping("/api/v1/inventory")
@Tag(name = "Inventory", description = "库存出入库")
public class InventoryController {

    private final InventoryApplicationService inventoryService;

    public InventoryController(InventoryApplicationService inventoryService) {
        this.inventoryService = inventoryService;
    }

    // ==================== 物料 ====================

    @GetMapping("/materials")
    @Operation(summary = "分页查询物料", operationId = "pageMaterials")
    @RequiresPermission("biz:inventory:list")
    public Result<Page<InvMaterial>> pageMaterials(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String materialName,
            @RequestParam(required = false) String category) {
        Page<InvMaterial> page = inventoryService.pageMaterials(pageNo, pageSize, materialCode, materialName, category);
        return Result.ok(page, TraceContext.getTraceId());
    }

    @PostMapping("/materials")
    @Operation(summary = "创建物料", operationId = "createMaterial")
    @RequiresPermission("biz:inventory:inbound")
    @Auditable(bizType = "inventory", module = "sample", bizIdExpr = "#result.data.id", operationType = "CREATE")
    public Result<InvMaterial> createMaterial(@Valid @RequestBody SaveMaterialRequest request) {
        InvMaterial material = inventoryService.createMaterial(request);
        return Result.ok(material, TraceContext.getTraceId());
    }

    // ==================== 仓库 ====================

    @GetMapping("/warehouses")
    @Operation(summary = "查询仓库列表", operationId = "listWarehouses")
    @RequiresPermission("biz:inventory:list")
    public Result<List<InvWarehouse>> listWarehouses() {
        List<InvWarehouse> list = inventoryService.listWarehouses();
        return Result.ok(list, TraceContext.getTraceId());
    }

    // ==================== 库存余额 ====================

    @GetMapping("/balances")
    @Operation(summary = "分页查询库存余额", operationId = "pageStockBalances")
    @RequiresPermission("biz:inventory:list")
    public Result<Page<StockBalanceVO>> pageBalances(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String materialId,
            @RequestParam(required = false) String warehouseId,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String materialName) {
        InventoryPageQuery query = new InventoryPageQuery();
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        query.setMaterialId(materialId);
        query.setWarehouseId(warehouseId);
        query.setMaterialCode(materialCode);
        query.setMaterialName(materialName);
        Page<StockBalanceVO> page = inventoryService.pageBalances(query);
        return Result.ok(page, TraceContext.getTraceId());
    }

    // ==================== 入库单 ====================

    @GetMapping("/inbounds")
    @Operation(summary = "分页查询入库单", operationId = "pageInbounds")
    @RequiresPermission("biz:inventory:list")
    public Result<Page<InvInboundOrder>> pageInbounds(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String materialId,
            @RequestParam(required = false) String warehouseId,
            @RequestParam(required = false) String batchNo) {
        OrderPageQuery query = new OrderPageQuery();
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        query.setOrderNo(orderNo);
        query.setStatus(status);
        query.setMaterialId(materialId);
        query.setWarehouseId(warehouseId);
        query.setBatchNo(batchNo);
        Page<InvInboundOrder> page = inventoryService.pageInbounds(query);
        return Result.ok(page, TraceContext.getTraceId());
    }

    @GetMapping("/inbounds/{id}")
    @Operation(summary = "查询入库单详情", operationId = "getInbound")
    @RequiresPermission("biz:inventory:detail")
    public Result<InvInboundOrder> getInbound(@PathVariable String id) {
        InvInboundOrder order = inventoryService.getInbound(id);
        return Result.ok(order, TraceContext.getTraceId());
    }

    @PostMapping("/inbounds")
    @Operation(summary = "创建入库单 (幂等)", operationId = "createInbound")
    @RequiresPermission("biz:inventory:inbound")
    @Auditable(bizType = "inventory", module = "sample", bizIdExpr = "#result.data.id", operationType = "CREATE")
    public Result<InvInboundOrder> createInbound(@Valid @RequestBody InboundRequest request) {
        InvInboundOrder order = inventoryService.createInbound(request);
        return Result.ok(order, TraceContext.getTraceId());
    }

    @PostMapping("/inbounds/{id}/confirm")
    @Operation(summary = "确认入库 (触发库存增加)", operationId = "confirmInbound")
    @RequiresPermission("biz:inventory:inbound")
    @Auditable(bizType = "inventory", module = "sample", bizIdExpr = "#id", operationType = "CONFIRM")
    public Result<InvInboundOrder> confirmInbound(@PathVariable String id) {
        InvInboundOrder order = inventoryService.confirmInbound(id);
        return Result.ok(order, TraceContext.getTraceId());
    }

    // ==================== 出库单 ====================

    @GetMapping("/outbounds")
    @Operation(summary = "分页查询出库单", operationId = "pageOutbounds")
    @RequiresPermission("biz:inventory:list")
    public Result<Page<InvOutboundOrder>> pageOutbounds(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String materialId,
            @RequestParam(required = false) String warehouseId,
            @RequestParam(required = false) String batchNo) {
        OrderPageQuery query = new OrderPageQuery();
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        query.setOrderNo(orderNo);
        query.setStatus(status);
        query.setMaterialId(materialId);
        query.setWarehouseId(warehouseId);
        query.setBatchNo(batchNo);
        Page<InvOutboundOrder> page = inventoryService.pageOutbounds(query);
        return Result.ok(page, TraceContext.getTraceId());
    }

    @GetMapping("/outbounds/{id}")
    @Operation(summary = "查询出库单详情", operationId = "getOutbound")
    @RequiresPermission("biz:inventory:detail")
    public Result<InvOutboundOrder> getOutbound(@PathVariable String id) {
        InvOutboundOrder order = inventoryService.getOutbound(id);
        return Result.ok(order, TraceContext.getTraceId());
    }

    @PostMapping("/outbounds")
    @Operation(summary = "创建出库单 (幂等)", operationId = "createOutbound")
    @RequiresPermission("biz:inventory:outbound")
    @Auditable(bizType = "inventory", module = "sample", bizIdExpr = "#result.data.id", operationType = "CREATE")
    public Result<InvOutboundOrder> createOutbound(@Valid @RequestBody OutboundRequest request) {
        InvOutboundOrder order = inventoryService.createOutbound(request);
        return Result.ok(order, TraceContext.getTraceId());
    }

    @PostMapping("/outbounds/{id}/confirm")
    @Operation(summary = "确认出库 (触发库存扣减, 库存不足拦截)", operationId = "confirmOutbound")
    @RequiresPermission("biz:inventory:outbound")
    @Auditable(bizType = "inventory", module = "sample", bizIdExpr = "#id", operationType = "CONFIRM")
    public Result<InvOutboundOrder> confirmOutbound(@PathVariable String id) {
        InvOutboundOrder order = inventoryService.confirmOutbound(id);
        return Result.ok(order, TraceContext.getTraceId());
    }

    @PostMapping("/outbounds/{id}/compensate")
    @Operation(summary = "异常补偿出库 (库存回补)", operationId = "compensateOutbound")
    @RequiresPermission("biz:inventory:outbound")
    @Auditable(bizType = "inventory", module = "sample", bizIdExpr = "#id", operationType = "COMPENSATE")
    public Result<InvOutboundOrder> compensateOutbound(@PathVariable String id) {
        InvOutboundOrder order = inventoryService.compensateOutbound(id);
        return Result.ok(order, TraceContext.getTraceId());
    }

    // ==================== 库存流水 ====================

    @GetMapping("/transactions")
    @Operation(summary = "分页查询库存流水", operationId = "pageStockTransactions")
    @RequiresPermission("biz:inventory:flow")
    public Result<Page<InvStockTransaction>> pageTransactions(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String balanceId,
            @RequestParam(required = false) String materialId) {
        Page<InvStockTransaction> page = inventoryService.pageTransactions(pageNo, pageSize, balanceId, materialId);
        return Result.ok(page, TraceContext.getTraceId());
    }
}
