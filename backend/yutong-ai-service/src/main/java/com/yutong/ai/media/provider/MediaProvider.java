package com.yutong.ai.media.provider;

import com.yutong.ai.media.domain.MediaJob;

/**
 * 多模态媒体提供方抽象。
 * 设计来源: Phase 7 /media/* — 预留 image/video/audio/ppt 统一接口，
 * 首版仅 Mock 实现，无外部调用，后续可接入真实模型网关。
 */
public interface MediaProvider {

    /** 提供方编码，如 mock */
    String providerCode();

    /** 是否支持该媒体类型 */
    boolean supports(String mediaType);

    /**
     * 生成媒体 (mock 场景同步返回 fake URL)。
     * @param job 已持久化的任务 (含 prompt/inputJson)
     * @return 输出结果 {outputUrl, outputJson, cost}
     */
    GenerateResult generate(MediaJob job);

    record GenerateResult(String outputUrl, String outputJson, java.math.BigDecimal cost) {}
}
