package com.yutong.ai.media.service;

import com.yutong.ai.media.domain.MediaJob;

/**
 * 音频生成 SPI。多模态音频生成 SPI。
 *
 * <p>按模态拆分的 typed 门面, 底层委托 {@link MediaService} (任务落库 + 失败关闭语义不变)。
 */
public interface AudioGenerationService {

    /**
     * 文生音频 (同步, 失败抛业务异常, 不返回假 URL)。
     *
     * @param prompt 提示词 (非空, 超 2000 字符截断, 与 MediaService 一致)
     * @return SUCCESS 终态任务 (含 outputUrl)
     */
    MediaJob generate(String prompt);
}
