import service from './request'
import type { FileInfo, PageResult, PageRequest } from './types'

/**
 * 文件管理 API。设计来源: 08-API契约设计、98-后端实现蓝图系统基础接口补齐规则
 * GA2-25: 修正路径 bug (原 /api/v1/files 与 baseURL /api/v1 拼接产生双 /api/v1),
 * 改用相对路径 /files; 补全 download/delete/preview/bind 端点。
 * 后端 FileController @RequestMapping("/api/v1/files")
 */

/** 分页查询文件列表: GET /files (需 system:file:list) */
export function getFiles(
  params?: PageRequest & { fileName?: string }
): Promise<PageResult<FileInfo>> {
  return service.get('/files', { params }) as unknown as Promise<
    PageResult<FileInfo>
  >
}

/**
 * 查询文件详情: GET /files/{id} (详情查看走 system:file:list 权限)
 * TODO: 后端契约 openapi.yaml 中 /files/{id} 仅声明 DELETE，无 GET 详情操作，待后端补齐后启用。
 */
export function getFile(id: string): Promise<FileInfo> {
  return service.get(`/files/${id}`) as unknown as Promise<FileInfo>
}

/** 上传文件: POST /files/upload (需 system:file:upload) */
export function uploadFile(file: File): Promise<FileInfo> {
  const formData = new FormData()
  formData.append('file', file)
  return service.post('/files/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  }) as unknown as Promise<FileInfo>
}

/** 下载文件流: GET /files/{id}/download (需 system:file:download)，返回二进制 Blob */
export function downloadFile(id: string): Promise<Blob> {
  return service.get(`/files/${id}/download`, {
    responseType: 'blob',
  }) as unknown as Promise<Blob>
}

/** 文件安全预览: GET /files/{id}/preview (需 system:file:preview)，返回二进制 Blob */
export function previewFile(id: string): Promise<Blob> {
  return service.get(`/files/${id}/preview`, {
    responseType: 'blob',
  }) as unknown as Promise<Blob>
}

/** 绑定文件到业务对象: POST /files/bind (需 system:file:bind) */
export function bindFile(data: {
  bizType: string
  bizId: string
  fileId: string
}): Promise<void> {
  return service.post('/files/bind', data) as unknown as Promise<void>
}

/** 删除文件: DELETE /files/{id} (需 system:file:delete) */
export function deleteFile(id: string): Promise<void> {
  return service.delete(`/files/${id}`) as unknown as Promise<void>
}
