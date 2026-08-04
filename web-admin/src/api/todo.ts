import service from './request'
import type { TodoTask, PageResult, PageRequest } from './types'

export function getTodos(params?: PageRequest & { todoStatus?: string }): Promise<PageResult<TodoTask>> {
  // baseURL 已为 /api/v1，路径仅写 /todos 即可，避免拼成 /api/v1/api/v1/todos 导致 404
  return service.get('/todos', { params }) as unknown as Promise<PageResult<TodoTask>>
}

export function completeTodo(id: string): Promise<void> {
  return service.post(`/todos/${id}/complete`) as unknown as Promise<void>
}
