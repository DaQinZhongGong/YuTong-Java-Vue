package com.yutong.ai.drama.storyboard.gateway;

import java.math.BigDecimal;
import java.util.List;

/**
 * 分镜视频生成网关。设计来源: ai-depth-parity.md S2.2
 *
 * <p>封装媒体生成调用 (MediaService / VideoGenerationService)。无 provider Key 时
 * 必须失败关闭, 禁止返回假 URL。默认实现见 {@link MediaServiceVideoGateway}。
 *
 * <p>参考图规则:
 * <ul>
 *   <li>≥2 张 → multi-reference image-to-video</li>
 *   <li>1 张 → image-to-video</li>
 *   <li>0 张 → text-to-video</li>
 *   <li>同 location 上一镜 last_frame_url 作为下一镜 firstFrameUrl</li>
 * </ul>
 */
public interface VideoGateway {

    /**
     * 同步生成一段分镜视频。失败抛业务异常, 不返回假 URL。
     */
    Result generate(Command command);

    /**
     * 生成指令。
     *
     * @param prompt           视频提示词 (非空)
     * @param durationSeconds  目标时长秒 (可空, 由 provider 默认)
     * @param aspectRatio      画幅 (可空)
     * @param referenceImages  参考图 URL 列表 (可空)
     * @param firstFrameUrl    首帧/末帧承接 URL (可空, 同 location 上一镜末帧)
     * @param styleRef         风格参考 (可空)
     * @param providerCode     指定供应商编码 (可空)
     * @param modelCode        指定模型编码 (可空)
     */
    record Command(
            String prompt,
            BigDecimal durationSeconds,
            String aspectRatio,
            List<String> referenceImages,
            String firstFrameUrl,
            String styleRef,
            String providerCode,
            String modelCode
    ) {
        public boolean isMultiReference() {
            return referenceImages != null && referenceImages.size() >= 2;
        }

        public boolean hasReferenceImage() {
            return referenceImages != null && !referenceImages.isEmpty();
        }

        public boolean hasFirstFrame() {
            return firstFrameUrl != null && !firstFrameUrl.isBlank();
        }

        /** 是否走 image-to-video (含多参考与首帧承接) */
        public boolean isImageToVideo() {
            return hasReferenceImage() || hasFirstFrame();
        }
    }

    /**
     * 生成结果。videoUrl 必须真实可访问; lastFrameUrl 可空 (provider 未返回时)。
     *
     * @param videoUrl     成功视频 URL
     * @param lastFrameUrl 末帧 URL (可空)
     * @param videoId      厂商异步任务 ID (可空)
     * @param mediaJobId   ai_media_job.id
     */
    record Result(String videoUrl, String lastFrameUrl, String videoId, String mediaJobId) {
    }
}
