package com.yutong.sample.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.auth.DataScopeResolver;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.auth.DataScope;
import com.yutong.common.auth.DataScopeType;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.id.IdGenerator;
import com.yutong.sample.inventory.domain.*;
import com.yutong.sample.inventory.dto.*;
import com.yutong.sample.inventory.mapper.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 库存应用服务。设计来源: 35-样例业务矩阵扩展设计 P1 库存出入库。
 *
 * <p>验证能力:
 * <ul>
 *   <li>库存扣减并发控制: 乐观锁 (version 字段 + updateById WHERE version=?)</li>
 *   <li>幂等键防重复提交: idempotency_key 唯一约束, DuplicateKeyException 拦截</li>
 *   <li>乐观锁或数据库约束: balance 表 version 字段 + material_id+warehouse_id 唯一索引</li>
 *   <li>异步任务: 导出库存异步化 (复用 sys_import_export_task)</li>
 *   <li>异常补偿: 出库单 COMPENSATE 回补库存 + 写入 COMPENSATE 流水</li>
 *   <li>事务一致性: @Transactional 保证单据+余额+流水三表原子性</li>
 * </ul>
 */
@Service
public class InventoryApplicationService {

    private static final int MAX_RETRY = 3;

    /** 库存资源编码，对齐 permissions.yaml biz:inventory:* 命名。 */
    public static final String RESOURCE_CODE = "biz:inventory";

    private final InvMaterialMapper materialMapper;
    private final InvWarehouseMapper warehouseMapper;
    private final InvStockBalanceMapper balanceMapper;
    private final InvInboundOrderMapper inboundMapper;
    private final InvOutboundOrderMapper outboundMapper;
    private final InvStockTransactionMapper transactionMapper;
    private final InventorySequenceService sequenceService;
    private final InventoryDomainService domainService;
    private final DataScopeResolver dataScopeResolver;

    public InventoryApplicationService(InvMaterialMapper materialMapper,
                                        InvWarehouseMapper warehouseMapper,
                                        InvStockBalanceMapper balanceMapper,
                                        InvInboundOrderMapper inboundMapper,
                                        InvOutboundOrderMapper outboundMapper,
                                        InvStockTransactionMapper transactionMapper,
                                        InventorySequenceService sequenceService,
                                        InventoryDomainService domainService,
                                        DataScopeResolver dataScopeResolver) {
        this.materialMapper = materialMapper;
        this.warehouseMapper = warehouseMapper;
        this.balanceMapper = balanceMapper;
        this.inboundMapper = inboundMapper;
        this.outboundMapper = outboundMapper;
        this.transactionMapper = transactionMapper;
        this.sequenceService = sequenceService;
        this.domainService = domainService;
        this.dataScopeResolver = dataScopeResolver;
    }

    /**
     * GA2-DS: 对 LambdaQueryWrapper 追加 DataScope 过滤条件。
     * - ALL/TENANT: 无附加条件 (admin)
     * - 其它(SELF/DEPT/CUSTOM/NONE 等): created_by = currentUserId，userId 缺失时安全降级 1=0
     */
    private void applyDataScope(LambdaQueryWrapper<?> wrapper, DataScope scope) {
        if (scope == null) {
            return;
        }
        if (scope.scopeType() == DataScopeType.ALL || scope.scopeType() == DataScopeType.TENANT) {
            return;
        }
        String userId = scope.userId();
        if (userId == null || userId.isBlank()) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.apply("created_by = {0}", userId);
    }

    // ==================== 物料 ====================

    @Transactional
    public InvMaterial createMaterial(SaveMaterialRequest request) {
        // 检查编码唯一性
        Long count = materialMapper.selectCount(new LambdaQueryWrapper<InvMaterial>()
                .eq(InvMaterial::getTenantId, CurrentUserContext.getTenantId())
                .eq(InvMaterial::getMaterialCode, request.getMaterialCode()));
        if (count > 0) {
            throw new BusinessException(ErrorCode.IVT_IDEMPOTENT_DUPLICATE,
                    "物料编码已存在: " + request.getMaterialCode());
        }

        InvMaterial material = new InvMaterial();
        material.setId(IdGenerator.nextId());
        material.setTenantId(CurrentUserContext.getTenantId());
        material.setCreatedBy(CurrentUserContext.getUserId());
        material.setMaterialCode(request.getMaterialCode());
        material.setMaterialName(request.getMaterialName());
        material.setMaterialType(request.getMaterialType() == null ? InvMaterial.TYPE_GENERAL : request.getMaterialType());
        material.setSpec(request.getSpec());
        material.setUnit(request.getUnit() == null ? "PCS" : request.getUnit());
        material.setCategory(request.getCategory());
        material.setBarcode(request.getBarcode());
        material.setReferencePrice(request.getReferencePrice() == null ? BigDecimal.ZERO : request.getReferencePrice());
        material.setStatus(InvMaterial.STATUS_ACTIVE);
        materialMapper.insert(material);
        return material;
    }

    public Page<InvMaterial> pageMaterials(int pageNo, int pageSize, String materialCode, String materialName, String category) {
        LambdaQueryWrapper<InvMaterial> wrapper = new LambdaQueryWrapper<InvMaterial>()
                .eq(InvMaterial::getTenantId, CurrentUserContext.getTenantId())
                .eq(InvMaterial::getDeleted, false)
                .orderByDesc(InvMaterial::getCreatedTime);
        if (materialCode != null && !materialCode.isBlank()) {
            wrapper.like(InvMaterial::getMaterialCode, materialCode);
        }
        if (materialName != null && !materialName.isBlank()) {
            wrapper.like(InvMaterial::getMaterialName, materialName);
        }
        if (category != null && !category.isBlank()) {
            wrapper.eq(InvMaterial::getCategory, category);
        }
        // GA2-DS: 接入 DataScope 过滤，admin(ALL/TENANT) 放行，非 admin 按 created_by 过滤
        applyDataScope(wrapper, dataScopeResolver.resolve(RESOURCE_CODE));
        return materialMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
    }

    public List<InvWarehouse> listWarehouses() {
        return warehouseMapper.selectList(new LambdaQueryWrapper<InvWarehouse>()
                .eq(InvWarehouse::getTenantId, CurrentUserContext.getTenantId())
                .eq(InvWarehouse::getDeleted, false)
                .orderByAsc(InvWarehouse::getWarehouseCode));
    }

    // ==================== 库存余额 ====================

    public Page<StockBalanceVO> pageBalances(InventoryPageQuery query) {
        LambdaQueryWrapper<InvStockBalance> wrapper = new LambdaQueryWrapper<InvStockBalance>()
                .eq(InvStockBalance::getTenantId, CurrentUserContext.getTenantId())
                .eq(InvStockBalance::getDeleted, false)
                .orderByDesc(InvStockBalance::getUpdatedTime);
        if (query.getMaterialId() != null && !query.getMaterialId().isBlank()) {
            wrapper.eq(InvStockBalance::getMaterialId, query.getMaterialId());
        }
        if (query.getWarehouseId() != null && !query.getWarehouseId().isBlank()) {
            wrapper.eq(InvStockBalance::getWarehouseId, query.getWarehouseId());
        }
        // GA2-DS: 接入 DataScope 过滤，admin(ALL/TENANT) 放行，非 admin 按 created_by 过滤
        applyDataScope(wrapper, dataScopeResolver.resolve(RESOURCE_CODE));
        Page<InvStockBalance> page = balanceMapper.selectPage(
                new Page<>(query.getPageNo(), query.getPageSize()), wrapper);

        // 转换 VO, 冗余物料/仓库信息
        Page<StockBalanceVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<StockBalanceVO> records = page.getRecords().stream().map(b -> {
            StockBalanceVO vo = new StockBalanceVO();
            vo.setId(b.getId());
            vo.setMaterialId(b.getMaterialId());
            vo.setWarehouseId(b.getWarehouseId());
            vo.setQuantity(b.getQuantity());
            vo.setLockedQuantity(b.getLockedQuantity());
            vo.setAvailableQuantity(b.getAvailableQuantity());
            vo.setLastInTime(b.getLastInTime() == null ? null : b.getLastInTime().toString());
            vo.setLastOutTime(b.getLastOutTime() == null ? null : b.getLastOutTime().toString());

            InvMaterial mat = materialMapper.selectById(b.getMaterialId());
            if (mat != null) {
                vo.setMaterialCode(mat.getMaterialCode());
                vo.setMaterialName(mat.getMaterialName());
            }
            InvWarehouse wh = warehouseMapper.selectById(b.getWarehouseId());
            if (wh != null) {
                vo.setWarehouseCode(wh.getWarehouseCode());
                vo.setWarehouseName(wh.getWarehouseName());
            }
            return vo;
        }).collect(Collectors.toList());
        result.setRecords(records);
        return result;
    }

    // ==================== 入库 ====================

    @Transactional
    public InvInboundOrder createInbound(InboundRequest request) {
        domainService.validateQuantity(request.getQuantity());
        validateMaterialExists(request.getMaterialId());
        validateWarehouseExists(request.getWarehouseId());

        InvInboundOrder order = new InvInboundOrder();
        order.setId(IdGenerator.nextId());
        order.setTenantId(CurrentUserContext.getTenantId());
        order.setCreatedBy(CurrentUserContext.getUserId());
        order.setOrderNo(sequenceService.nextInboundNo());
        order.setIdempotencyKey(request.getIdempotencyKey());
        order.setWarehouseId(request.getWarehouseId());
        order.setMaterialId(request.getMaterialId());
        order.setQuantity(request.getQuantity());
        order.setUnitCost(request.getUnitCost() == null ? BigDecimal.ZERO : request.getUnitCost());
        order.setTotalAmount(order.getQuantity().multiply(order.getUnitCost()));
        order.setStatus(InvInboundOrder.STATUS_DRAFT);
        order.setInboundType(request.getInboundType() == null ? InvInboundOrder.TYPE_PURCHASE : request.getInboundType());
        order.setBatchNo(request.getBatchNo());
        order.setSupplier(request.getSupplier());

        try {
            inboundMapper.insert(order);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.IVT_IDEMPOTENT_DUPLICATE,
                    "入库单幂等键已存在, 请勿重复提交: " + request.getIdempotencyKey());
        }
        return order;
    }

    @Transactional
    public InvInboundOrder confirmInbound(String orderId) {
        InvInboundOrder order = getInboundOrThrow(orderId);
        String toStatus = domainService.validateInboundTransition("CONFIRM", order.getStatus());

        // 库存增加 (乐观锁重试)
        BigDecimal delta = order.getQuantity();
        InvStockBalance balance = getOrCreateBalance(order.getMaterialId(), order.getWarehouseId());
        BigDecimal before = balance.getQuantity();
        BigDecimal after = before.add(delta);

        balance.setQuantity(after);
        balance.setLastInTime(OffsetDateTime.now());
        updateBalanceWithRetry(balance);

        // 写入流水
        writeTransaction(balance, InvStockTransaction.TYPE_IN, delta, before, after,
                InvStockTransaction.BIZ_INBOUND, order.getId(), order.getOrderNo());

        // 更新单据
        order.setStatus(toStatus);
        order.setConfirmedTime(OffsetDateTime.now());
        order.setConfirmedBy(CurrentUserContext.getUserId());
        order.setBalanceId(balance.getId());
        inboundMapper.updateById(order);

        return order;
    }

    public Page<InvInboundOrder> pageInbounds(OrderPageQuery query) {
        LambdaQueryWrapper<InvInboundOrder> wrapper = new LambdaQueryWrapper<InvInboundOrder>()
                .eq(InvInboundOrder::getTenantId, CurrentUserContext.getTenantId())
                .eq(InvInboundOrder::getDeleted, false)
                .orderByDesc(InvInboundOrder::getCreatedTime);
        if (query.getOrderNo() != null && !query.getOrderNo().isBlank()) {
            wrapper.like(InvInboundOrder::getOrderNo, query.getOrderNo());
        }
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            wrapper.eq(InvInboundOrder::getStatus, query.getStatus());
        }
        if (query.getMaterialId() != null && !query.getMaterialId().isBlank()) {
            wrapper.eq(InvInboundOrder::getMaterialId, query.getMaterialId());
        }
        if (query.getWarehouseId() != null && !query.getWarehouseId().isBlank()) {
            wrapper.eq(InvInboundOrder::getWarehouseId, query.getWarehouseId());
        }
        if (query.getBatchNo() != null && !query.getBatchNo().isBlank()) {
            wrapper.eq(InvInboundOrder::getBatchNo, query.getBatchNo());
        }
        // GA2-DS: 接入 DataScope 过滤，admin(ALL/TENANT) 放行，非 admin 按 created_by 过滤
        applyDataScope(wrapper, dataScopeResolver.resolve(RESOURCE_CODE));
        return inboundMapper.selectPage(new Page<>(query.getPageNo(), query.getPageSize()), wrapper);
    }

    public InvInboundOrder getInbound(String id) {
        return getInboundOrThrow(id);
    }

    // ==================== 出库 ====================

    @Transactional
    public InvOutboundOrder createOutbound(OutboundRequest request) {
        domainService.validateQuantity(request.getQuantity());
        validateMaterialExists(request.getMaterialId());
        validateWarehouseExists(request.getWarehouseId());

        InvOutboundOrder order = new InvOutboundOrder();
        order.setId(IdGenerator.nextId());
        order.setTenantId(CurrentUserContext.getTenantId());
        order.setCreatedBy(CurrentUserContext.getUserId());
        order.setOrderNo(sequenceService.nextOutboundNo());
        order.setIdempotencyKey(request.getIdempotencyKey());
        order.setWarehouseId(request.getWarehouseId());
        order.setMaterialId(request.getMaterialId());
        order.setQuantity(request.getQuantity());
        order.setUnitCost(request.getUnitCost() == null ? BigDecimal.ZERO : request.getUnitCost());
        order.setTotalAmount(order.getQuantity().multiply(order.getUnitCost()));
        order.setStatus(InvOutboundOrder.STATUS_DRAFT);
        order.setOutboundType(request.getOutboundType() == null ? InvOutboundOrder.TYPE_SALE : request.getOutboundType());
        order.setBatchNo(request.getBatchNo());
        order.setCustomer(request.getCustomer());

        try {
            outboundMapper.insert(order);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.IVT_IDEMPOTENT_DUPLICATE,
                    "出库单幂等键已存在, 请勿重复提交: " + request.getIdempotencyKey());
        }
        return order;
    }

    @Transactional
    public InvOutboundOrder confirmOutbound(String orderId) {
        InvOutboundOrder order = getOutboundOrThrow(orderId);
        String toStatus = domainService.validateOutboundTransition("CONFIRM", order.getStatus());

        // 库存扣减 (乐观锁重试) - 库存不足时拦截
        BigDecimal delta = order.getQuantity();
        InvStockBalance balance = getBalanceOrThrow(order.getMaterialId(), order.getWarehouseId());
        BigDecimal available = balance.getAvailableQuantity();
        if (available.compareTo(delta) < 0) {
            throw new BusinessException(ErrorCode.IVT_STOCK_INSUFFICIENT,
                    "库存不足: 可用=" + available + ", 需求=" + delta);
        }

        BigDecimal before = balance.getQuantity();
        BigDecimal after = before.subtract(delta);
        balance.setQuantity(after);
        balance.setLastOutTime(OffsetDateTime.now());
        updateBalanceWithRetry(balance);

        // 写入流水 (OUT 流水 quantity 为负数)
        writeTransaction(balance, InvStockTransaction.TYPE_OUT, delta.negate(), before, after,
                InvStockTransaction.BIZ_OUTBOUND, order.getId(), order.getOrderNo());

        // 更新单据
        order.setStatus(toStatus);
        order.setConfirmedTime(OffsetDateTime.now());
        order.setConfirmedBy(CurrentUserContext.getUserId());
        order.setBalanceId(balance.getId());
        outboundMapper.updateById(order);

        return order;
    }

    @Transactional
    public InvOutboundOrder compensateOutbound(String orderId) {
        InvOutboundOrder order = getOutboundOrThrow(orderId);
        String toStatus = domainService.validateOutboundTransition("COMPENSATE", order.getStatus());

        // 异常补偿: 库存回补
        BigDecimal delta = order.getQuantity();
        InvStockBalance balance = balanceMapper.selectById(order.getBalanceId());
        if (balance == null) {
            throw new ResourceNotFoundException(ErrorCode.IVT_MATERIAL_NOT_FOUND, "库存余额不存在");
        }
        BigDecimal before = balance.getQuantity();
        BigDecimal after = before.add(delta);
        balance.setQuantity(after);
        updateBalanceWithRetry(balance);

        // 写入补偿流水 (COMPENSATE 流水 quantity 为正数)
        writeTransaction(balance, InvStockTransaction.TYPE_COMPENSATE, delta, before, after,
                InvStockTransaction.BIZ_OUTBOUND, order.getId(), order.getOrderNo());

        // 更新单据
        order.setStatus(toStatus);
        order.setCompensatedTime(OffsetDateTime.now());
        order.setCompensatedBy(CurrentUserContext.getUserId());
        outboundMapper.updateById(order);

        return order;
    }

    public Page<InvOutboundOrder> pageOutbounds(OrderPageQuery query) {
        LambdaQueryWrapper<InvOutboundOrder> wrapper = new LambdaQueryWrapper<InvOutboundOrder>()
                .eq(InvOutboundOrder::getTenantId, CurrentUserContext.getTenantId())
                .eq(InvOutboundOrder::getDeleted, false)
                .orderByDesc(InvOutboundOrder::getCreatedTime);
        if (query.getOrderNo() != null && !query.getOrderNo().isBlank()) {
            wrapper.like(InvOutboundOrder::getOrderNo, query.getOrderNo());
        }
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            wrapper.eq(InvOutboundOrder::getStatus, query.getStatus());
        }
        if (query.getMaterialId() != null && !query.getMaterialId().isBlank()) {
            wrapper.eq(InvOutboundOrder::getMaterialId, query.getMaterialId());
        }
        if (query.getWarehouseId() != null && !query.getWarehouseId().isBlank()) {
            wrapper.eq(InvOutboundOrder::getWarehouseId, query.getWarehouseId());
        }
        if (query.getBatchNo() != null && !query.getBatchNo().isBlank()) {
            wrapper.eq(InvOutboundOrder::getBatchNo, query.getBatchNo());
        }
        // GA2-DS: 接入 DataScope 过滤，admin(ALL/TENANT) 放行，非 admin 按 created_by 过滤
        applyDataScope(wrapper, dataScopeResolver.resolve(RESOURCE_CODE));
        return outboundMapper.selectPage(new Page<>(query.getPageNo(), query.getPageSize()), wrapper);
    }

    public InvOutboundOrder getOutbound(String id) {
        return getOutboundOrThrow(id);
    }

    // ==================== 库存流水 ====================

    public Page<InvStockTransaction> pageTransactions(int pageNo, int pageSize, String balanceId, String materialId) {
        LambdaQueryWrapper<InvStockTransaction> wrapper = new LambdaQueryWrapper<InvStockTransaction>()
                .eq(InvStockTransaction::getTenantId, CurrentUserContext.getTenantId())
                .orderByDesc(InvStockTransaction::getCreatedTime);
        if (balanceId != null && !balanceId.isBlank()) {
            wrapper.eq(InvStockTransaction::getBalanceId, balanceId);
        }
        if (materialId != null && !materialId.isBlank()) {
            wrapper.eq(InvStockTransaction::getMaterialId, materialId);
        }
        // GA2-DS: 接入 DataScope 过滤，admin(ALL/TENANT) 放行，非 admin 按 created_by 过滤
        applyDataScope(wrapper, dataScopeResolver.resolve(RESOURCE_CODE));
        return transactionMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
    }

    // ==================== 内部工具 ====================

    private InvInboundOrder getInboundOrThrow(String id) {
        InvInboundOrder order = inboundMapper.selectById(id);
        if (order == null || order.getDeleted()) {
            throw new ResourceNotFoundException(ErrorCode.IVT_INBOUND_NOT_FOUND, "入库单不存在");
        }
        return order;
    }

    private InvOutboundOrder getOutboundOrThrow(String id) {
        InvOutboundOrder order = outboundMapper.selectById(id);
        if (order == null || order.getDeleted()) {
            throw new ResourceNotFoundException(ErrorCode.IVT_OUTBOUND_NOT_FOUND, "出库单不存在");
        }
        return order;
    }

    private void validateMaterialExists(String materialId) {
        InvMaterial mat = materialMapper.selectById(materialId);
        if (mat == null || mat.getDeleted()) {
            throw new ResourceNotFoundException(ErrorCode.IVT_MATERIAL_NOT_FOUND, "物料不存在");
        }
    }

    private void validateWarehouseExists(String warehouseId) {
        InvWarehouse wh = warehouseMapper.selectById(warehouseId);
        if (wh == null || wh.getDeleted()) {
            throw new ResourceNotFoundException(ErrorCode.IVT_WAREHOUSE_NOT_FOUND, "仓库不存在");
        }
    }

    private InvStockBalance getOrCreateBalance(String materialId, String warehouseId) {
        InvStockBalance balance = balanceMapper.selectOne(new LambdaQueryWrapper<InvStockBalance>()
                .eq(InvStockBalance::getTenantId, CurrentUserContext.getTenantId())
                .eq(InvStockBalance::getMaterialId, materialId)
                .eq(InvStockBalance::getWarehouseId, warehouseId)
                .eq(InvStockBalance::getDeleted, false));
        if (balance == null) {
            balance = new InvStockBalance();
            balance.setId(IdGenerator.nextId());
            balance.setTenantId(CurrentUserContext.getTenantId());
            balance.setCreatedBy(CurrentUserContext.getUserId());
            balance.setMaterialId(materialId);
            balance.setWarehouseId(warehouseId);
            balance.setQuantity(BigDecimal.ZERO);
            balance.setLockedQuantity(BigDecimal.ZERO);
            try {
                balanceMapper.insert(balance);
            } catch (DuplicateKeyException e) {
                // 并发插入, 重新查询
                balance = balanceMapper.selectOne(new LambdaQueryWrapper<InvStockBalance>()
                        .eq(InvStockBalance::getTenantId, CurrentUserContext.getTenantId())
                        .eq(InvStockBalance::getMaterialId, materialId)
                        .eq(InvStockBalance::getWarehouseId, warehouseId)
                        .eq(InvStockBalance::getDeleted, false));
            }
        }
        return balance;
    }

    private InvStockBalance getBalanceOrThrow(String materialId, String warehouseId) {
        InvStockBalance balance = balanceMapper.selectOne(new LambdaQueryWrapper<InvStockBalance>()
                .eq(InvStockBalance::getTenantId, CurrentUserContext.getTenantId())
                .eq(InvStockBalance::getMaterialId, materialId)
                .eq(InvStockBalance::getWarehouseId, warehouseId)
                .eq(InvStockBalance::getDeleted, false));
        if (balance == null) {
            throw new BusinessException(ErrorCode.IVT_STOCK_INSUFFICIENT,
                    "库存余额不存在, 无法出库: materialId=" + materialId + ", warehouseId=" + warehouseId);
        }
        return balance;
    }

    /**
     * 乐观锁重试更新余额。MyBatis-Plus updateById 自动带 version 字段做 WHERE 条件。
     * 失败重试时需重新读取最新 version。
     */
    private void updateBalanceWithRetry(InvStockBalance balance) {
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            int affected = balanceMapper.updateById(balance);
            if (affected > 0) {
                return;
            }
            // 乐观锁冲突, 重新读取最新数据
            InvStockBalance latest = balanceMapper.selectById(balance.getId());
            if (latest == null) {
                throw new BusinessException(ErrorCode.IVT_STOCK_INSUFFICIENT, "库存余额已被删除");
            }
            // 重新计算 (基于最新余额 + 当前操作)
            // 注意: 这里不重新计算, 因为外层已经计算好 quantity 变化.
            // 乐观锁失败说明有并发操作, 需要外层重新计算.
            // 简化处理: 抛异常让外层重试整个事务.
            throw new BusinessException(ErrorCode.IVT_STOCK_INSUFFICIENT,
                    "库存并发冲突, 请重试: balanceId=" + balance.getId());
        }
    }

    private void writeTransaction(InvStockBalance balance, String type, BigDecimal delta,
                                   BigDecimal before, BigDecimal after,
                                   String bizType, String bizId, String bizNo) {
        InvStockTransaction tx = new InvStockTransaction();
        tx.setId(IdGenerator.nextId());
        tx.setTenantId(CurrentUserContext.getTenantId());
        tx.setCreatedBy(CurrentUserContext.getUserId());
        tx.setTransactionNo(sequenceService.nextTransactionNo());
        tx.setBalanceId(balance.getId());
        tx.setMaterialId(balance.getMaterialId());
        tx.setWarehouseId(balance.getWarehouseId());
        tx.setTransactionType(type);
        tx.setQuantity(delta);
        tx.setQuantityBefore(before);
        tx.setQuantityAfter(after);
        tx.setBizOrderType(bizType);
        tx.setBizOrderId(bizId);
        tx.setBizOrderNo(bizNo);
        transactionMapper.insert(tx);
    }
}
