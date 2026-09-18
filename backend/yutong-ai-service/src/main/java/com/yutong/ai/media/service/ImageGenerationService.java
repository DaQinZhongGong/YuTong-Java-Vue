package com.yutong.ai.media.service;

import com.yutong.ai.media.domain.MediaJob;

/**
 * 图像生成 SPI。多模态图像生成 SPI。
 *
 * <p>按模态拆分的 typed 门面, 底层委托 {@link MediaService} (任务落库 + 失败关闭语义不变)。
 * 文本对话侧对应物为 {@code LlmProviderAdapter} (IChatModelService) +
 * {@code LlmProviderSelector} (ServiceFactory)。
 */
public interface ImageGenerationService {

    /**
     * 文生图 (同步, 失败抛业务异常, 不返回假 URL)。
     *
     * @param prompt 提示词 (非空, 超 2000 字符截断, 与 MediaService 一致)
     * @return SUCCESS 终态任务 (含 outputUrl)
     */
    MediaJob generate(String prompt);
}
