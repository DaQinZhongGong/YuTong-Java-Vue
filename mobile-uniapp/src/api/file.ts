/**
 * 文件上传 / 绑定 API（移动端）。
 *
 * 设计来源: 后端 FileController 契约 (POST /api/v1/files/upload, POST /api/v1/files/bind)。
 * 契约依据:
 *  - upload: multipart 表单字段名 file, 返回 SysFile { id, fileName, fileSize, ... }
 *  - bind:   JSON 体 { bizType, bizId, fileId, relType?, sortNo? }, 返回 BizFileRel
 *
 * 注意: 上传重试队列 (uploadQueue.ts) 不持久化 token / 完整请求头；
 *       本文件在运行时通过 getToken() 读取鉴权头，队列本身只保存 filePath 等元数据。
 */

import type { Result } from './types';
import { BASE_URL, get } from '@/utils/request';
import { getToken, clearAuth } from '@/store/auth';

/** 文件元数据（对齐后端 SysFile 关键字段）。 */
export interface SysFile {
  id: string;
  fileName: string;
  fileSize: number;
  fileExt: string;
  contentType: string;
  storageType: string;
  uploadStatus: string;
  fileKey: string;
  checksum: string;
  createdTime: string;
}

/** 绑定请求体（对齐后端 BindFileRequest）。 */
export interface BindFileRequest {
  bizType: string;
  bizId: string;
  fileId: string;
  relType?: string;
  sortNo?: number;
}

/**
 * 上传单个文件（uni.uploadFile）。
 * 鉴权头在调用时实时读取，不写入任何持久化队列。
 */
export function uploadFile(filePath: string): Promise<SysFile> {
  const token = getToken();
  const header: Record<string, string> = { 'Accept-Language': 'zh-CN' };
  if (token) header['Authorization'] = `Bearer ${token}`;

  return new Promise<SysFile>((resolve, reject) => {
    uni.uploadFile({
      url: BASE_URL + '/files/upload',
      filePath,
      name: 'file',
      header,
      success: (res) => {
        if (res.statusCode === 401) {
          clearAuth();
          uni.reLaunch({ url: '/pages/login/login' });
          reject(new Error('登录已过期，请重新登录'));
          return;
        }
        if (res.statusCode < 200 || res.statusCode >= 300) {
          reject(new Error(`上传失败：HTTP ${res.statusCode}`));
          return;
        }
        try {
          const body = JSON.parse(res.data) as Result<SysFile>;
          if (!body || !body.data) {
            reject(new Error('上传响应解析失败'));
            return;
          }
          resolve(body.data);
        } catch {
          reject(new Error('上传响应解析失败'));
        }
      },
      fail: (err) => {
        reject(new Error(err.errMsg || '文件上传失败'));
      },
    });
  });
}

/**
 * 绑定文件到业务对象（uni.request POST JSON）。
 * 当绑定失败时由调用方（上传队列）置为 BIND_FAILED 状态。
 */
export function bindFile(req: BindFileRequest): Promise<void> {
  const token = getToken();
  const header: Record<string, string> = {
    'Content-Type': 'application/json',
    'Accept-Language': 'zh-CN',
  };
  if (token) header['Authorization'] = `Bearer ${token}`;

  return new Promise<void>((resolve, reject) => {
    uni.request({
      url: BASE_URL + '/files/bind',
      method: 'POST',
      data: req,
      header,
      success: (res) => {
        if (res.statusCode === 401) {
          clearAuth();
          uni.reLaunch({ url: '/pages/login/login' });
          reject(new Error('登录已过期，请重新登录'));
          return;
        }
        if (res.statusCode < 200 || res.statusCode >= 300) {
          reject(new Error(`绑定失败：HTTP ${res.statusCode}`));
          return;
        }
        resolve();
      },
      fail: (err) => {
        reject(new Error(err.errMsg || '文件绑定失败'));
      },
    });
  });
}

/**
 * 文件业务绑定记录（对齐后端 BizFileRel）。
 * 后端 /files/by-biz 仅返回绑定关系，fileName/fileSize 等为防御性字段，
 * 便于后端后续内联文件元数据时直接展示。
 */
export interface BizFileRelVO {
  id: string;
  bizType: string;
  bizId: string;
  fileId: string;
  relType?: string;
  sortNo?: number;
  fileName?: string;
  fileSize?: number;
  contentType?: string;
  fileExt?: string;
}

/**
 * 按业务对象查询已绑定文件列表。
 * GET /api/v1/files/by-biz?bizType=&bizId=
 */
export function listFilesByBiz(bizType: string, bizId: string): Promise<BizFileRelVO[]> {
  return get<BizFileRelVO[]>('/files/by-biz', { bizType, bizId });
}

/**
 * 获取文件预签名下载链接。
 * GET /api/v1/files/{id}/download-url
 */
export function getDownloadUrl(fileId: string): Promise<string> {
  return get<string>(`/files/${fileId}/download-url`);
}
