import service from './request'
import type { Message, PageResult, PageRequest } from './types'

export function getMessages(params?: PageRequest & { readStatus?: string }): Promise<PageResult<Message>> {
  // baseURL 已为 /api/v1，路径仅写 /messages 即可，避免拼成 /api/v1/api/v1/messages 导致 404
  return service.get('/messages', { params }) as unknown as Promise<PageResult<Message>>
}

/** 标记消息已读: PUT /messages/{id}/read */
export function markRead(id: string): Promise<void> {
  return service.put(`/messages/${id}/read`) as unknown as Promise<void>
}

/** 查询当前用户未读消息数: GET /messages/unread-count，后端返回 { count: number } */
export async function countUnreadMessages(): Promise<number> {
  const res = (await service.get('/messages/unread-count')) as unknown as { count: number }
  return res.count
}

/** 标记全部消息已读: PUT /messages/read-all */
export function markAllMessagesRead(): Promise<void> {
  return service.put('/messages/read-all') as unknown as Promise<void>
}
