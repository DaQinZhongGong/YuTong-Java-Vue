import service from './request'
import type { Customer, PageResult } from './types'

export interface CustomerPageQuery {
  pageNo?: number
  pageSize?: number
  keyword?: string
  status?: string
}

export function getCustomers(params?: CustomerPageQuery): Promise<PageResult<Customer>> {
  // baseURL 已为 /api/v1，路径仅写 /customers 即可，避免拼成 /api/v1/api/v1/customers 导致 404
  return service.get('/customers', { params }) as unknown as Promise<PageResult<Customer>>
}

export function getCustomer(id: string): Promise<Customer> {
  return service.get(`/customers/${id}`) as unknown as Promise<Customer>
}

export function createCustomer(data: Partial<Customer>): Promise<Customer> {
  return service.post('/customers', data) as unknown as Promise<Customer>
}

export function updateCustomer(id: string, data: Partial<Customer>): Promise<Customer> {
  return service.put(`/customers/${id}`, data) as unknown as Promise<Customer>
}

export function deleteCustomer(id: string): Promise<void> {
  return service.delete(`/customers/${id}`) as unknown as Promise<void>
}
