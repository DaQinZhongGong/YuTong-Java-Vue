import { get, post } from '@/utils/request';
import type { PageResult } from './mobile';

export interface SysMessage {
  id: string;
  receiverId: string;
  msgType: string;
  title: string;
  content: string;
  readStatus: string;
  readTime: string;
  bizType: string;
  bizId: string;
  targetRouteId: string;
  targetParams: string;
  createdTime: string;
}

export function getMessages(params: { pageNo?: number; pageSize?: number }) {
  return get<PageResult<SysMessage>>('/messages', params);
}

export function getUnreadCount() {
  return get<number>('/messages/unread-count');
}

export function markAsRead(id: string) {
  return post<void>(`/messages/${id}/read`);
}

export function markAllAsRead() {
  return post<void>('/messages/read-all');
}
