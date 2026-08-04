import { get, post } from '@/utils/request';

export interface WorkbenchStats {
  todoCount: number;
  messageCount: number;
  pendingRequestCount: number;
  /** 本月申请数（聚合接口扩展字段，后端未返回时缺省 0）。 */
  monthApplyCount?: number;
  /** 上传队列失败数（聚合接口扩展字段，后端未返回时缺省 0）。 */
  uploadFailedCount?: number;
}

/**
 * 工作台聚合响应。
 * 设计来源: P1-1 工作台首屏聚合接口，一次性返回 stats + recentTodos(Top5)。
 * 兼容后端两种返回形态:
 *  - 嵌套结构 { stats: {...}, recentTodos: [...] }（目标契约）
 *  - 扁平结构 { todoCount, messageCount, pendingRequestCount, ... }（当前后端）
 */
export interface WorkbenchResponse {
  stats: WorkbenchStats;
  recentTodos: MobileTodoVO[];
}

export interface MobileTodoVO {
  id: string;
  bizType: string;
  bizId: string;
  requestNo: string;
  title: string;
  customerName: string;
  totalAmount: number;
  submittedTime: string;
  statusLabel: string;
  version: number;
}

export interface BizRequestItem {
  id: string;
  productId: string;
  productCodeSnapshot: string;
  productNameSnapshot: string;
  unit: string;
  quantity: number;
  unitPrice: number;
  lineAmount: number;
  sortNo: number;
}

export interface ApprovalRecord {
  id: string;
  action: string;
  result: string;
  opinion: string;
  operatorId: string;
  operatedTime: string;
}

export interface MobileRequestDetailVO {
  id: string;
  requestNo: string;
  title: string;
  customerId: string;
  customerName: string;
  requestStatus: string;
  requestStatusLabel: string;
  totalAmount: number;
  applyReason: string;
  applicantName: string;
  submittedTime: string;
  approvedTime: string;
  /** 最后更新时间（ISO 字符串），用于详情页"最后更新：X分钟前"展示。 */
  lastUpdateTime?: string;
  /** 兼容字段：部分后端契约以 updateTime 命名。 */
  updateTime?: string;
  /** 兼容字段：部分后端契约以 lastModifiedAt 命名。 */
  lastModifiedAt?: string;
  version: number;
  items: BizRequestItem[];
  approvals: ApprovalRecord[];
}

export interface PageResult<T> {
  records: T[];
  total: number;
  page: number;
  size: number;
}

export function getWorkbenchStats() {
  return get<WorkbenchStats>('/mobile/workbench');
}

/**
 * 工作台聚合接口（P1-1）。
 * 调用 GET /mobile/workbench，一次性获取 stats + recentTodos。
 * 兼容后端两种返回形态：
 *  - 嵌套 { stats, recentTodos }：直接采用
 *  - 扁平 { todoCount, ... }：包装为 stats，recentTodos 缺省空数组
 * 缺省字段补 0，保证 UI 取值安全。
 */
export function getWorkbench(): Promise<WorkbenchResponse> {
  return get<unknown>('/mobile/workbench').then((raw) => {
    const obj = (raw || {}) as Record<string, unknown>;
    const DEFAULT_STATS: WorkbenchStats = {
      todoCount: 0,
      messageCount: 0,
      pendingRequestCount: 0,
      monthApplyCount: 0,
      uploadFailedCount: 0,
    };
    // 嵌套结构：已带 stats 字段
    if (obj.stats && typeof obj.stats === 'object') {
      const stats = { ...DEFAULT_STATS, ...(obj.stats as WorkbenchStats) };
      const recentTodos = Array.isArray(obj.recentTodos)
        ? (obj.recentTodos as MobileTodoVO[])
        : [];
      return { stats, recentTodos };
    }
    // 扁平结构：整体视为 stats
    const stats: WorkbenchStats = {
      todoCount: Number(obj.todoCount) || 0,
      messageCount: Number(obj.messageCount) || 0,
      pendingRequestCount: Number(obj.pendingRequestCount) || 0,
      monthApplyCount: Number(obj.monthApplyCount) || 0,
      uploadFailedCount: Number(obj.uploadFailedCount) || 0,
    };
    const recentTodos = Array.isArray(obj.recentTodos)
      ? (obj.recentTodos as MobileTodoVO[])
      : [];
    return { stats, recentTodos };
  });
}

export function getMobileTodos(params: {
  pageNo?: number;
  pageSize?: number;
  keyword?: string;
  todoStatus?: string;
}) {
  return get<PageResult<MobileTodoVO>>('/mobile/todos', params);
}

export function getMobileRequestDetail(id: string) {
  return get<MobileRequestDetailVO>(`/mobile/biz-requests/${id}`);
}

/**
 * Approve a biz request.
 *
 * The backend reads parameters from the query string (@RequestParam),
 * so we build the query string manually and pass it via the URL.
 */
export function approveMobileRequest(
  id: string,
  params: { opinion?: string; version?: number; idempotencyKey?: string },
) {
  const query = new URLSearchParams();
  if (params.opinion) query.set('opinion', params.opinion);
  if (params.version != null) query.set('version', String(params.version));
  if (params.idempotencyKey) query.set('idempotencyKey', params.idempotencyKey);
  const qs = query.toString();
  return post<void>(`/mobile/biz-requests/${id}/approve${qs ? '?' + qs : ''}`);
}

/**
 * Reject a biz request.
 *
 * The backend reads parameters from the query string (@RequestParam),
 * so we build the query string manually and pass it via the URL.
 */
export function rejectMobileRequest(
  id: string,
  params: { opinion: string; version?: number; idempotencyKey?: string },
) {
  const query = new URLSearchParams();
  if (params.opinion) query.set('opinion', params.opinion);
  if (params.version != null) query.set('version', String(params.version));
  if (params.idempotencyKey) query.set('idempotencyKey', params.idempotencyKey);
  const qs = query.toString();
  return post<void>(`/mobile/biz-requests/${id}/reject${qs ? '?' + qs : ''}`);
}

export function resolveScan(code: string) {
  return post<{ routeId: string; params: Record<string, unknown> }>(
    `/mobile/scan/resolve?code=${encodeURIComponent(code)}`,
  );
}
