package com.yutong.ai.media.service;

import org.springframework.stereotype.Service;

/**
 * 媒体生成工厂。多模态媒体生成工厂。
 *
 * <p>按模态颁发 typed SPI (image/video/audio), 实现均为委托 {@link MediaService}
 * 的无状态 lambda。调用方 (短剧/聊天) 面向接口编程, 不再散落字符串 mediaType。
 *
 * <p>纯 additive: 既有 MediaService / MediaProvider 行为零改动。
 */
@Service
public class MediaGenerationFactory {

    private final ImageGenerationService image;
    private final VideoGenerationService video;
    private final AudioGenerationService audio;

    public MediaGenerationFactory(MediaService mediaService) {
        this.image = prompt -> mediaService.generateSync("image", prompt);
        this.video = prompt -> mediaService.generateSync("video", prompt);
        this.audio = prompt -> mediaService.generateSync("audio", prompt);
    }

    /** 图像生成服务。 */
    public ImageGenerationService image() {
        return image;
    }

    /** 视频生成服务。 */
    public VideoGenerationService video() {
        return video;
    }

    /** 音频生成服务。 */
    public AudioGenerationService audio() {
        return audio;
    }
}
