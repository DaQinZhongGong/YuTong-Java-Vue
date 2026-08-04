import service from './request'
import type {
  WorkTicket,
  WorkTicketCategory,
  TicketDetail,
  SaveTicketRequest,
  TicketActionRequest,
  PageResult,
} from './types'

/**
 * 工单中心 API。设计来源: 35-样例业务矩阵扩展设计 P1 工单中心。
 * 后端 TicketController @RequestMapping("/api/v1/tickets")
 */

/** 分页查询工单列表 */
export function pageTickets(params: {
  page?: number
  size?: number
  ticketNo?: string
  title?: string
  status?: string
  categoryId?: string
  priority?: string
}): Promise<PageResult<WorkTicket>> {
  return service.get('/tickets', { params }) as unknown as Promise<PageResult<WorkTicket>>
}

/** 查询工单详情（含处理记录时间线） */
export function getTicketDetail(id: string): Promise<TicketDetail> {
  return service.get(`/tickets/${id}`) as unknown as Promise<TicketDetail>
}

/** 创建工单 */
export function createTicket(data: SaveTicketRequest): Promise<WorkTicket> {
  return service.post('/tickets', data) as unknown as Promise<WorkTicket>
}

/** 工单状态流转操作 */
export function ticketAction(id: string, data: TicketActionRequest): Promise<WorkTicket> {
  return service.post(`/tickets/${id}/actions`, data) as unknown as Promise<WorkTicket>
}

/** 查询工单分类列表 */
export function listTicketCategories(): Promise<WorkTicketCategory[]> {
  return service.get('/tickets/categories') as unknown as Promise<WorkTicketCategory[]>
}
