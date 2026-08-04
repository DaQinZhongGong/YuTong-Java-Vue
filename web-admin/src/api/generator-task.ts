import service from './request'
import type {
  CreateGeneratorTaskRequest,
  LcGeneratorTask,
  PageRequest,
  PageResult,
} from './types'

/**
 * GA2-47: 低代码生成任务 API。设计来源: 14-低代码平台设计、47-低代码设计器交互详设
 *
 * 路径对齐后端 GeneratorTaskController @RequestMapping("/api/v1/lowcode/generator-tasks"),
 * baseURL 已含 /api/v1, 此处用相对路径避免双前缀。
 *
 *  - GET    /lowcode/generator-tasks          → listGeneratorTasks (分页查询)
 *  - GET    /lowcode/generator-tasks/{id}     → getGeneratorTask (详情, 含 diffJson)
 *  - POST   /lowcode/generator-tasks          → createGeneratorTask (创建 PENDING 任务)
 *  - POST   /lowcode/generator-tasks/{id}/run → runGeneratorTask (执行生成, 计算 diff, @Hidden 端点)
 *  - POST   /lowcode/generator-tasks/{id}/cancel → cancelGeneratorTask (取消, @Hidden 端点)
 *
 * 47 号文档第 113-125 行:
 *  - 默认禁止静默覆盖 Git 已修改文件 → CONFLICT 状态时 conflict 数组非空, 前端需逐项勾选
 *  - 冲突文件列表需用户逐项勾选 → applyToWorkspace 仅为前端交互, 后端只读 diffJson
 */
export { LC_GENERATOR_TASK_STATUS, LC_GENERATOR_SCOPE } from './types'

/** 分页查询生成任务: GET /lowcode/generator-tasks */
export function getGeneratorTasks(
  params?: PageRequest & { taskNo?: string; status?: string }
): Promise<PageResult<LcGeneratorTask>> {
  return service.get('/lowcode/generator-tasks', { params }) as unknown as Promise<
    PageResult<LcGeneratorTask>
  >
}

/** 查询生成任务详情 (含 diffJson): GET /lowcode/generator-tasks/{id} */
export function getGeneratorTask(id: string): Promise<LcGeneratorTask> {
  return service.get(`/lowcode/generator-tasks/${id}`) as unknown as Promise<LcGeneratorTask>
}

/**
 * 创建生成任务: POST /lowcode/generator-tasks
 * 创建后状态为 PENDING, 需调用 runGeneratorTask 执行生成。
 */
export function createGeneratorTask(
  data: CreateGeneratorTaskRequest
): Promise<LcGeneratorTask> {
  return service.post('/lowcode/generator-tasks', data) as unknown as Promise<LcGeneratorTask>
}

/**
 * 执行生成任务: POST /lowcode/generator-tasks/{id}/run
 * 状态流转: PENDING → RUNNING → SUCCESS (无冲突) / CONFLICT (有冲突) / FAILED (生成异常)
 * 返回 task 含 diffJson + conflictCount。
 *
 * 后端 @Hidden 标注: 该端点为内部触发, OpenAPI 文档不展示。
 */
export function runGeneratorTask(id: string): Promise<LcGeneratorTask> {
  return service.post(`/lowcode/generator-tasks/${id}/run`) as unknown as Promise<LcGeneratorTask>
}

/**
 * 取消生成任务: POST /lowcode/generator-tasks/{id}/cancel
 * 仅 PENDING/RUNNING 状态可取消, 取消后状态为 CANCELLED。
 *
 * 后端 @Hidden 标注: 该端点为内部触发, OpenAPI 文档不展示。
 */
export function cancelGeneratorTask(id: string): Promise<LcGeneratorTask> {
  return service.post(`/lowcode/generator-tasks/${id}/cancel`) as unknown as Promise<LcGeneratorTask>
}
