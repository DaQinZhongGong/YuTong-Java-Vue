import service from './request'
import type { PageResult } from './types'

/**
 * 工作流 BPMN 引擎 API。设计来源: 41-工作流与BPMN引擎设计。
 * 后端 WorkflowController @RequestMapping("/api/v1/workflow")
 *
 * GA2-44 L1+L2 轻量自研引擎: 流程定义/实例/任务/委派/转办/运营监控。
 */

/** 流程定义状态 */
export type WfDefinitionStatus = 'DRAFT' | 'PUBLISHED' | 'DISABLED'

/** 流程实例状态 */
export type WfInstanceStatus = 'RUNNING' | 'COMPLETED' | 'TERMINATED' | 'SUSPENDED'

/** 任务状态 */
export type WfTaskStatus = 'PENDING' | 'COMPLETED' | 'REJECTED' | 'DELEGATED' | 'TRANSFERRED' | 'CANCELLED'

/** 委派类型 */
export type WfDelegateType = 'DELEGATE' | 'TRANSFER'

/** 流程定义 */
export interface WfProcessDefinition {
  id: string
  processKey: string
  processName: string
  categoryCode?: string
  bizType?: string
  engineDeploymentId?: string
  bpmnXml?: string
  versionNo: number
  status: WfDefinitionStatus
  formPageCode?: string
  description?: string
  serviceTaskWhitelist?: string
  createdTime?: string
  updatedTime?: string
  createdBy?: string
}

/** 流程实例 */
export interface WfProcessInstance {
  id: string
  engineInstanceId?: string
  processDefinitionId: string
  processKey: string
  bizType: string
  bizId: string
  bizNo?: string
  starterId: string
  instanceStatus: WfInstanceStatus
  currentNodeNames?: string
  variables?: string
  businessCallbackUrl?: string
  terminateReason?: string
  startTime?: string
  endTime?: string
  createdTime?: string
}

/** 任务扩展 */
export interface WfTaskExt {
  id: string
  engineTaskId?: string
  instanceId: string
  processKey?: string
  bizType?: string
  bizId?: string
  bizNo?: string
  nodeId: string
  taskName: string
  assigneeId?: string
  candidateGroup?: string
  taskStatus: WfTaskStatus
  taskType?: string
  dueTime?: string
  createTime?: string
  completeTime?: string
  formDataJson?: string
  opinion?: string
  delegateType?: WfDelegateType
  delegateToUserId?: string
  actualHandlerId?: string
}

/** 流程实例详情 (含任务历史 + BPMN XML) */
export interface WfInstanceDetail extends WfProcessInstance {
  processDefinitionName?: string
  tasks: WfTaskExt[]
  highlightNodeIds: string[]
  bpmnXml?: string
}

/** 保存流程定义请求 */
export interface SaveProcessDefinitionRequest {
  processKey: string
  processName: string
  categoryCode?: string
  bizType?: string
  bpmnXml: string
  formPageCode?: string
  description?: string
  serviceTaskWhitelist?: string[]
}

/** 启动流程实例请求 */
export interface StartProcessRequest {
  processKey: string
  bizType: string
  bizId: string
  bizNo?: string
  variables?: Record<string, unknown>
  businessCallbackUrl?: string
}

/** 办理任务请求 */
export interface CompleteTaskRequest {
  opinion?: string
  formData?: Record<string, unknown>
  variableUpdates?: Record<string, unknown>
}

/** 驳回任务请求 */
export interface RejectTaskRequest {
  opinion: string
  variableUpdates?: Record<string, unknown>
}

/** 委派/转办任务请求 */
export interface DelegateTaskRequest {
  delegateToUserId: string
  opinion?: string
  delegateType?: WfDelegateType
}

/** 终止流程实例请求 */
export interface TerminateInstanceRequest {
  reason: string
}

/** 运营监控统计 */
export interface WorkflowStats {
  publishedDefinitionCount: number
  totalInstanceCount: number
  runningInstanceCount: number
  completedInstanceCount: number
  terminatedInstanceCount: number
  pendingTaskCount: number
  completedTaskCount: number
  myPendingTaskCount: number
  myCompletedTaskCount: number
}

// ===== 流程定义 API =====

/** 分页查询流程定义 */
export function pageDefinitions(params: {
  page?: number
  size?: number
  keyword?: string
  status?: WfDefinitionStatus
  bizType?: string
}): Promise<PageResult<WfProcessDefinition>> {
  return service.get('/workflow/definitions', { params }) as unknown as Promise<PageResult<WfProcessDefinition>>
}

/** 查询流程定义详情 */
export function getDefinition(id: string): Promise<WfProcessDefinition> {
  return service.get(`/workflow/definitions/${id}`) as unknown as Promise<WfProcessDefinition>
}

/** 创建流程定义 */
export function createDefinition(data: SaveProcessDefinitionRequest): Promise<WfProcessDefinition> {
  return service.post('/workflow/definitions', data) as unknown as Promise<WfProcessDefinition>
}

/** 更新流程定义 */
export function updateDefinition(id: string, data: SaveProcessDefinitionRequest): Promise<WfProcessDefinition> {
  return service.put(`/workflow/definitions/${id}`, data) as unknown as Promise<WfProcessDefinition>
}

/** 发布流程定义 */
export function publishDefinition(id: string): Promise<WfProcessDefinition> {
  return service.post(`/workflow/definitions/${id}/publish`) as unknown as Promise<WfProcessDefinition>
}

/** 删除流程定义 (仅 DRAFT/DISABLED 可删除) */
export function deleteDefinition(id: string): Promise<void> {
  return service.delete(`/workflow/definitions/${id}`) as unknown as Promise<void>
}

// ===== 流程实例 API =====

/** 分页查询流程实例 */
export function pageInstances(params: {
  page?: number
  size?: number
  processKey?: string
  bizType?: string
  bizNo?: string
  starterId?: string
  instanceStatus?: WfInstanceStatus
}): Promise<PageResult<WfProcessInstance>> {
  return service.get('/workflow/instances', { params }) as unknown as Promise<PageResult<WfProcessInstance>>
}

/** 查询流程实例详情 (含任务历史 + BPMN XML) */
export function getInstance(id: string): Promise<WfInstanceDetail> {
  return service.get(`/workflow/instances/${id}`) as unknown as Promise<WfInstanceDetail>
}

/** 启动流程实例 */
export function startInstance(data: StartProcessRequest): Promise<WfProcessInstance> {
  return service.post('/workflow/instances/start', data) as unknown as Promise<WfProcessInstance>
}

/** 终止流程实例 */
export function terminateInstance(id: string, data: TerminateInstanceRequest): Promise<WfProcessInstance> {
  return service.post(`/workflow/instances/${id}/terminate`, data) as unknown as Promise<WfProcessInstance>
}

/** 获取流程图 (高亮当前节点) */
export function getInstanceDiagram(id: string): Promise<WfInstanceDetail> {
  return service.get(`/workflow/instances/${id}/diagram`) as unknown as Promise<WfInstanceDetail>
}

// ===== 任务 API =====

/** 分页查询任务 */
export function pageTasks(params: {
  page?: number
  size?: number
  processKey?: string
  bizType?: string
  bizNo?: string
  taskStatus?: WfTaskStatus
  myTodoOnly?: boolean
  myDoneOnly?: boolean
}): Promise<PageResult<WfTaskExt>> {
  return service.get('/workflow/tasks', { params }) as unknown as Promise<PageResult<WfTaskExt>>
}

/** 我的待办 (alias 端点) */
export function myTodoTasks(params: { page?: number; size?: number }): Promise<PageResult<WfTaskExt>> {
  return service.get('/workflow/tasks/todo', { params }) as unknown as Promise<PageResult<WfTaskExt>>
}

/** 查询任务详情 */
export function getTask(id: string): Promise<WfTaskExt> {
  return service.get(`/workflow/tasks/${id}`) as unknown as Promise<WfTaskExt>
}

/** 办理通过任务 */
export function completeTask(id: string, data: CompleteTaskRequest): Promise<WfTaskExt> {
  return service.post(`/workflow/tasks/${id}/complete`, data) as unknown as Promise<WfTaskExt>
}

/** 驳回任务 (回退到上一 UserTask) */
export function rejectTask(id: string, data: RejectTaskRequest): Promise<WfTaskExt> {
  return service.post(`/workflow/tasks/${id}/reject`, data) as unknown as Promise<WfTaskExt>
}

/** 委派任务 */
export function delegateTask(id: string, data: DelegateTaskRequest): Promise<WfTaskExt> {
  return service.post(`/workflow/tasks/${id}/delegate`, data) as unknown as Promise<WfTaskExt>
}

/** 转办任务 */
export function transferTask(id: string, data: DelegateTaskRequest): Promise<WfTaskExt> {
  return service.post(`/workflow/tasks/${id}/transfer`, data) as unknown as Promise<WfTaskExt>
}

// ===== 运营监控 API =====

/** 运营监控统计 (9 项指标) */
export function getWorkflowStats(): Promise<WorkflowStats> {
  return service.get('/workflow/stats') as unknown as Promise<WorkflowStats>
}
