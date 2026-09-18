import service from './request'
import type { PageResult } from './types'

/**
 * OSS 配置管理 API。设计来源: ADR 0005 P1-D + 业界同类实现 sys_oss_config。
 * 后端 OssConfigController @RequestMapping("/api/v1/oss/configs")
 */

export interface OssConfigInfo {
  id: string
  configKey: string
  configName: string
  storageType: 'MINIO' | 'S3' | 'LOCAL'
  endpoint: string
  accessKey: string
  secretKey: string
  bucketName: string
  domain?: string
  region?: string
  isHttps: boolean
  isDefault: boolean
  status: 'ENABLED' | 'DISABLED'
  createdTime?: string
  updatedTime?: string
  remark?: string
}

export interface SaveOssConfigRequest {
  id?: string
  configKey: string
  configName: string
  storageType: string
  endpoint: string
  accessKey: string
  secretKey: string
  bucketName: string
  domain?: string
  region?: string
  isHttps?: boolean
  isDefault?: boolean
  remark?: string
}

/** 分页查询: GET /oss/configs (需 system:oss-config:list) */
export function getOssConfigs(params?: {
  pageNo?: number
  pageSize?: number
  status?: string
  keyword?: string
}): Promise<PageResult<OssConfigInfo>> {
  return service.get('/oss/configs', { params }) as unknown as Promise<
    PageResult<OssConfigInfo>
  >
}

/** 全部启用配置: GET /oss/configs/enabled */
export function getEnabledOssConfigs(): Promise<OssConfigInfo[]> {
  return service.get('/oss/configs/enabled') as unknown as Promise<
    OssConfigInfo[]
  >
}

/** 详情: GET /oss/configs/{id} (含 secretKey) */
export function getOssConfig(id: string): Promise<OssConfigInfo> {
  return service.get(`/oss/configs/${id}`) as unknown as Promise<OssConfigInfo>
}

/** 保存: POST /oss/configs (需 system:oss-config:save) */
export function saveOssConfig(
  data: SaveOssConfigRequest
): Promise<string> {
  return service.post('/oss/configs', data) as unknown as Promise<string>
}

/** 启用: POST /oss/configs/{id}/enable */
export function enableOssConfig(id: string): Promise<void> {
  return service.post(`/oss/configs/${id}/enable`) as unknown as Promise<void>
}

/** 停用: POST /oss/configs/{id}/disable */
export function disableOssConfig(id: string): Promise<void> {
  return service.post(`/oss/configs/${id}/disable`) as unknown as Promise<void>
}

/** 设为默认: POST /oss/configs/{id}/default */
export function setDefaultOssConfig(id: string): Promise<void> {
  return service.post(`/oss/configs/${id}/default`) as unknown as Promise<void>
}

/** 测试连接: POST /oss/configs/{id}/test */
export function testOssConfig(id: string): Promise<boolean> {
  return service.post(`/oss/configs/${id}/test`) as unknown as Promise<boolean>
}

/** 删除: DELETE /oss/configs/{id} (需 system:oss-config:delete) */
export function deleteOssConfig(id: string): Promise<void> {
  return service.delete(`/oss/configs/${id}`) as unknown as Promise<void>
}
