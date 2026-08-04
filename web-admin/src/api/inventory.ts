import service from './request'
import type {
  InvMaterial,
  InvWarehouse,
  InvInboundOrder,
  InvOutboundOrder,
  InvStockTransaction,
  StockBalanceVO,
  SaveMaterialRequest,
  InboundRequest,
  OutboundRequest,
  InvPage,
} from './types'

/**
 * 库存出入库 API。设计来源: 35-样例业务矩阵扩展设计 P1 库存出入库。
 * 后端 InventoryController @RequestMapping("/api/v1/inventory")
 *
 * 核心能力:
 *  - 物料/仓库/库存余额分页查询
 *  - 入库单创建(幂等)+确认(库存增加)
 *  - 出库单创建(幂等)+确认(扣减库存,不足拦截)+异常补偿(回补)
 *  - 库存流水审计追溯(不可变)
 *
 * 验证 7 项能力: 并发扣减乐观锁、幂等键防重复、库存不足拦截、
 *               导入物料、导出库存、异步任务、异常补偿事务一致性。
 */

// ==================== 物料 ====================

/** 分页查询物料 */
export function pageMaterials(params: {
  pageNo?: number
  pageSize?: number
  materialCode?: string
  materialName?: string
  category?: string
}): Promise<InvPage<InvMaterial>> {
  return service.get('/inventory/materials', { params }) as unknown as Promise<InvPage<InvMaterial>>
}

/** 创建物料 */
export function createMaterial(data: SaveMaterialRequest): Promise<InvMaterial> {
  return service.post('/inventory/materials', data) as unknown as Promise<InvMaterial>
}

// ==================== 仓库 ====================

/** 查询仓库列表 (不分页, 用于下拉选择) */
export function listWarehouses(): Promise<InvWarehouse[]> {
  return service.get('/inventory/warehouses') as unknown as Promise<InvWarehouse[]>
}

// ==================== 库存余额 ====================

/** 分页查询库存余额 (含物料/仓库冗余字段) */
export function pageBalances(params: {
  pageNo?: number
  pageSize?: number
  materialId?: string
  warehouseId?: string
  materialCode?: string
  materialName?: string
}): Promise<InvPage<StockBalanceVO>> {
  return service.get('/inventory/balances', { params }) as unknown as Promise<InvPage<StockBalanceVO>>
}

// ==================== 入库单 ====================

/** 分页查询入库单 */
export function pageInbounds(params: {
  pageNo?: number
  pageSize?: number
  orderNo?: string
  status?: string
  materialId?: string
  warehouseId?: string
  batchNo?: string
}): Promise<InvPage<InvInboundOrder>> {
  return service.get('/inventory/inbounds', { params }) as unknown as Promise<InvPage<InvInboundOrder>>
}

/** 查询入库单详情 */
export function getInbound(id: string): Promise<InvInboundOrder> {
  return service.get(`/inventory/inbounds/${id}`) as unknown as Promise<InvInboundOrder>
}

/** 创建入库单 (幂等键防重复提交, 自动生成 INyyyyMMddNNNN 单号) */
export function createInbound(data: InboundRequest): Promise<InvInboundOrder> {
  return service.post('/inventory/inbounds', data) as unknown as Promise<InvInboundOrder>
}

/** 确认入库 (DRAFT → CONFIRMED, 触发库存增加 + 写入 IN 流水) */
export function confirmInbound(id: string): Promise<InvInboundOrder> {
  return service.post(`/inventory/inbounds/${id}/confirm`) as unknown as Promise<InvInboundOrder>
}

// ==================== 出库单 ====================

/** 分页查询出库单 */
export function pageOutbounds(params: {
  pageNo?: number
  pageSize?: number
  orderNo?: string
  status?: string
  materialId?: string
  warehouseId?: string
  batchNo?: string
}): Promise<InvPage<InvOutboundOrder>> {
  return service.get('/inventory/outbounds', { params }) as unknown as Promise<InvPage<InvOutboundOrder>>
}

/** 查询出库单详情 */
export function getOutbound(id: string): Promise<InvOutboundOrder> {
  return service.get(`/inventory/outbounds/${id}`) as unknown as Promise<InvOutboundOrder>
}

/** 创建出库单 (幂等键防重复提交, 自动生成 OUTyyyyMMddNNNN 单号) */
export function createOutbound(data: OutboundRequest): Promise<InvOutboundOrder> {
  return service.post('/inventory/outbounds', data) as unknown as Promise<InvOutboundOrder>
}

/** 确认出库 (DRAFT → CONFIRMED, 触发库存扣减, 库存不足返回 IVT-409001) */
export function confirmOutbound(id: string): Promise<InvOutboundOrder> {
  return service.post(`/inventory/outbounds/${id}/confirm`) as unknown as Promise<InvOutboundOrder>
}

/** 异常补偿出库 (CONFIRMED → COMPENSATED, 库存回补 + 写入 COMPENSATE 流水) */
export function compensateOutbound(id: string): Promise<InvOutboundOrder> {
  return service.post(`/inventory/outbounds/${id}/compensate`) as unknown as Promise<InvOutboundOrder>
}

// ==================== 库存流水 ====================

/** 分页查询库存流水 (不可变, 用于审计追溯) */
export function pageTransactions(params: {
  pageNo?: number
  pageSize?: number
  balanceId?: string
  materialId?: string
}): Promise<InvPage<InvStockTransaction>> {
  return service.get('/inventory/transactions', { params }) as unknown as Promise<InvPage<InvStockTransaction>>
}
