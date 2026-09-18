package com.yutong.ai.drama.compose.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 短剧合成部署级配置。设计来源: ADR 0004 P2-D 批次 5-C。
 *
 * <p>说明 (配置分层):
 * <ul>
 *   <li>ffmpeg 二进制路径 / 工作目录 / 超时属于<b>部署级</b>配置 (随镜像与环境走),
 *       按 12-factor 通过环境变量覆盖, 不进租户 DB (业务配置才可视化入库);</li>
 *   <li>运行时可用性通过 {@code GET /api/v1/ai/drama/compose/capabilities} 暴露,
 *       前端合成页据此展示横幅, 缺失时提交直接 503 失败关闭;</li>
 *   <li>docker 镜像已预装 ffmpeg (见 backend/Dockerfile)。</li>
 * </ul>
 *
 * <p>环境变量映射 (Spring 宽松绑定):
 * <ul>
 *   <li>{@code YUTONG_DRAMA_COMPOSE_FFMPEG-PATH} → ffmpegPath</li>
 *   <li>{@code YUTONG_DRAMA_COMPOSE_FFPROBE-PATH} → ffprobePath</li>
 *   <li>{@code YUTONG_DRAMA_COMPOSE_WORK-DIR} → workDir</li>
 *   <li>{@code YUTONG_DRAMA_COMPOSE_TIMEOUT-SEC} → timeoutSec</li>
 *   <li>{@code YUTONG_DRAMA_COMPOSE_MAX-DURATION-SEC} → maxDurationSec</li>
 * </ul>
 */
@Getter
@Setter@Component
@ConfigurationProperties(prefix = "yutong.drama.compose")
public class DramaComposeProperties {

    /** ffmpeg 二进制 (默认走 PATH; 生产建议 /usr/bin/ffmpeg) */
    private String ffmpegPath = "ffmpeg";

    /** ffprobe 二进制 (默认走 PATH) */
    private String ffprobePath = "ffprobe";

    /**
     * 工作目录 (绝对路径; 所有输入/输出相对路径均约束在此目录下)。
     * 默认 JVM 工作目录下 drama-work; 生产建议挂载 volume。
     */
    private String workDir = System.getProperty("user.dir") + "/drama-work";

    /** 单次合成超时秒数 (默认 600s; 超时杀进程落 FAILED) */
    private long timeoutSec = 600;

    /** 成片最大允许时长秒数 (默认 300s = 5min; ffprobe 超限落 FAILED) */
    private long maxDurationSec = 300;

    /** 单次最多镜头数 (默认 20, 与 DTO @Size 对齐) */
    private int maxShots = 20;
}
