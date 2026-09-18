package com.yutong.ai.media.service;

import com.yutong.ai.media.domain.MediaJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 媒体生成工厂单元测试。多模态媒体生成工厂。
 *
 * 覆盖: image/video/audio 三模态分别委托 generateSync 对应类型 + 结果透传。
 */
class MediaGenerationFactoryTest {

    private MediaService mediaService;
    private MediaGenerationFactory factory;

    @BeforeEach
    void setUp() {
        mediaService = Mockito.mock(MediaService.class);
        factory = new MediaGenerationFactory(mediaService);
    }

    @Test
    @DisplayName("image() 委托 image 类型并透传结果")
    void imageDelegates() {
        MediaJob job = new MediaJob();
        when(mediaService.generateSync(eq("image"), eq("a cat"))).thenReturn(job);

        assertSame(job, factory.image().generate("a cat"));
    }

    @Test
    @DisplayName("video() 委托 video 类型并透传结果")
    void videoDelegates() {
        MediaJob job = new MediaJob();
        when(mediaService.generateSync(eq("video"), eq("a scene"))).thenReturn(job);

        assertSame(job, factory.video().generate("a scene"));
    }

    @Test
    @DisplayName("audio() 委托 audio 类型并透传结果")
    void audioDelegates() {
        MediaJob job = new MediaJob();
        when(mediaService.generateSync(eq("audio"), eq("hello"))).thenReturn(job);

        assertSame(job, factory.audio().generate("hello"));
    }
}
