import service from './request'
import type { Product, PageResult } from './types'

export interface ProductPageQuery {
  pageNo?: number
  pageSize?: number
  keyword?: string
  status?: string
}

export function getProducts(params?: ProductPageQuery): Promise<PageResult<Product>> {
  // baseURL 已为 /api/v1，路径仅写 /products 即可，避免拼成 /api/v1/api/v1/products 导致 404
  return service.get('/products', { params }) as unknown as Promise<PageResult<Product>>
}

export function getProduct(id: string): Promise<Product> {
  return service.get(`/products/${id}`) as unknown as Promise<Product>
}

export function createProduct(data: Partial<Product>): Promise<Product> {
  return service.post('/products', data) as unknown as Promise<Product>
}

export function updateProduct(id: string, data: Partial<Product>): Promise<Product> {
  return service.put(`/products/${id}`, data) as unknown as Promise<Product>
}

export function deleteProduct(id: string): Promise<void> {
  return service.delete(`/products/${id}`) as unknown as Promise<void>
}
