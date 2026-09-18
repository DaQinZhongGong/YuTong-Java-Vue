/**
 * TTS 语音合成 API（移动端）。
 *
 * 设计来源: 后端 MediaService.generateSync("audio", prompt) + OpenAI 兼容 /audio/speech。
 * 落点: 业界同类实现 /system/speech/synthesize + ADR 0005 P1-E。
 */

import { post } from '@/utils/request';

export interface TtsResult {
  id: string;
  outputUrl: string;
  status: string;
}

/**
 * 语音合成: POST /ai/media/generate (mediaType=audio)。
 * 返回 MediaJob，成功后从 outputUrl 获取音频 URL。
 */
export async function synthesizeSpeech(text: string): Promise<TtsResult> {
  const job = await post<{
    id: string;
    outputUrl?: string;
    status: string;
  }>('/ai/media/generate', {
    mediaType: 'audio',
    prompt: text.substring(0, 500), // TTS 输入限制
  });

  // 同步接口: generateSync 已等待完成, 直接返回
  return {
    id: job.id,
    outputUrl: job.outputUrl || '',
    status: job.status,
  };
}
