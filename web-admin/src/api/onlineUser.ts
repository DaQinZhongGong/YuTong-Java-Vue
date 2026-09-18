import service from './request'

/**
 * 在线用户监控 API。设计来源: ADR 0005 P1-D + 业界同类实现 SysUserOnlineController。
 * 后端 OnlineUserController @RequestMapping("/api/v1/monitor/online")
 */

export interface OnlineUserSession {
  tokenId: string
  userId: string
  username: string
  tenantId: string
  ip: string
  userAgent: string
  loginTime: string
  lastActiveTime: string
}

/** 在线用户列表: GET /monitor/online (需 system:monitor:online) */
export function getOnlineUsers(keyword?: string): Promise<OnlineUserSession[]> {
  return service.get('/monitor/online', {
    params: keyword ? { keyword } : {},
  }) as unknown as Promise<OnlineUserSession[]>
}

/** 强制下线: DELETE /monitor/online/{tokenId} */
export function kickOnlineUser(tokenId: string): Promise<boolean> {
  return service.delete(`/monitor/online/${tokenId}`) as unknown as Promise<boolean>
}
